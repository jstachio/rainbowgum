package io.jstach.rainbowgum.slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LoggerAPI;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class RainbowGumLoggerFactory implements ILoggerFactory {

	private final ConcurrentMap<String, Logger> loggerMap;

	/*
	 * Loggers looked up while none is bound, kept apart from loggerMap so the same name
	 * looked up after the next binding gets that gum's decorators and settings. Cleared
	 * on each binding.
	 */
	private final ConcurrentMap<String, Logger> unboundLoggerMap;

	/*
	 * Spring Boot (and anything else with its own pre-boot bootstrap sequence) can
	 * install a bootstrap RainbowGum, have SLF4J's ServiceLoader-triggered initialize()
	 * capture it here, and only later RainbowGum.set(...) the "real" one - this factory
	 * would otherwise be stuck forever routing new getLogger(...) calls through the stale
	 * bootstrap instance's config/decorators. onGlobalChange (below) keeps this current
	 * for names looked up after the swap, and a removal clears it until the next binding
	 * (see unboundLogger()). Already-created Logger instances in loggerMap are a separate
	 * concern: ones allowed to change level or created on the global router
	 * (ReplaceableLogger, below) are kept current independently via the router's own
	 * RouteChangePublisher, which is how a bootstrap-era ReplaceableLogger stops pointing
	 * at the queued placeholder router once a real router replaces it (see subscribe()).
	 * Routing for loggers that were not allowed to change (LevelLogger) is not revisited
	 * after creation. Context contributors follow the current gum for every handler
	 * through ChangingContributor.
	 */
	private record FactoryState(@Nullable RainbowGum gum, LoggerDecorator decorator) {
	}

	private volatile FactoryState state;

	private final RainbowGumMDCAdapter mdc;

	private final ChangingContributor contributor;

	/*
	 * Held here (as opposed to inline at the subscribe call below) so this factory keeps
	 * it strongly reachable - RainbowGum.onGlobalChange only holds a weak reference, so
	 * an inline lambda with no other strong referrer could become eligible for GC almost
	 * immediately after subscribing. The checker flags this::setRainbowGum as an
	 * under-initialization risk (a method reference bound before the constructor
	 * finishes), but it's never actually invoked until well after construction - only
	 * when RainbowGum's global instance later changes, via the subscription registered in
	 * the constructor below.
	 */
	@SuppressWarnings("methodref.receiver.bound")
	private final Consumer<@Nullable RainbowGum> onGlobalChange = this::setRainbowGum;

	public RainbowGumLoggerFactory(RainbowGum rainbowGum, RainbowGumMDCAdapter mdc) {
		super();
		this.loggerMap = new ConcurrentHashMap<>();
		this.unboundLoggerMap = new ConcurrentHashMap<>();
		this.state = new FactoryState(rainbowGum, LoggerDecorator.of(rainbowGum));
		this.mdc = mdc;
		this.contributor = new ChangingContributor(rainbowGum);
		RainbowGum.onGlobalChange(onGlobalChange);
	}

	private void setRainbowGum(@Nullable RainbowGum gum) {
		var next = new FactoryState(gum, gum == null ? LoggerDecorator.Noop.INSTANCE : LoggerDecorator.of(gum));
		contributor.setRainbowGum(gum);
		this.state = next;
		if (gum != null) {
			unboundLoggerMap.clear();
		}
	}

	@Override
	public Logger getLogger(String name) {
		Logger simpleLogger = loggerMap.get(name);
		if (simpleLogger != null) {
			return simpleLogger;
		}
		else {
			var current = this.state;
			var currentRainbowGum = current.gum();
			if (currentRainbowGum == null) {
				return unboundLogger(name);
			}
			currentRainbowGum.config().loggerRegistry().registerLoggerName(LoggerAPI.Standard.SLF4J, name);
			var router = currentRainbowGum.router();
			var changePublisher = currentRainbowGum.config().changePublisher();

			DepthAwareLogger newLogger;
			var level = router.levelResolver().resolveLevel(name);
			var allowedChanges = changePublisher.allowedChanges(name);
			/*
			 * callerInfoEnabled is resolved from its own logging.caller.logger.<name>
			 * property, independent of allowedChanges/logging.change.logger.<name> -
			 * fetched once here (not re-derived per branch) and threaded through to both
			 * maybeAddCallerInfo call sites and subscribe()'s captured closure,
			 * preserving the existing
			 * "decided once at construction, never revisited on change" semantics. It is
			 * also the same flag that decides, below, whether newLogger gets wrapped with
			 * LocationAwareForwardingLogger.
			 */
			boolean callerInfoEnabled = changePublisher.callerInfoEnabled(name);
			/*
			 * The queue behind the global router is abandoned once a router is bound, so
			 * a logger created on it must follow the binding whatever changes it allows.
			 */
			@SuppressWarnings("ReferenceEquality")
			boolean queued = router == LogRouter.global();
			if (queued || allowedChanges.contains(ChangeType.LEVEL)) {
				/*
				 * The level can change after this logger is created (that is the whole
				 * point of it being replaceable), so the sink must not be bound based on
				 * a level snapshot at creation time - level gating for this logger
				 * happens entirely inside ReplaceableLogger/LevelLogger instead, via
				 * set().
				 */
				LogEventLogger logger = router.eventLogger(name);
				var handler = maybeAddCallerInfo(name, callerInfoEnabled, logger, 1 + CALLER_WRAP_DEPTH);
				var changeable = ReplaceableLogger.of(level, handler);
				subscribe(name, router, changeable, callerInfoEnabled);
				newLogger = changeable;
			}
			else {
				LogEventLogger logger = router.route(name, level);
				if (level == System.Logger.Level.OFF) {
					newLogger = new LevelLogger.OffLogger(name);
				}
				else {
					var slf4jLevel = Levels.toSlf4jLevel(level);
					LogEventHandler handler = maybeAddCallerInfo(name, callerInfoEnabled, logger, CALLER_WRAP_DEPTH);
					newLogger = LevelLogger.of(slf4jLevel, handler);
				}
			}
			if (callerInfoEnabled && newLogger instanceof HandlerSource hs) {
				newLogger = new LocationAwareForwardingLogger(newLogger, hs, name);
			}
			Logger decorated = current.decorator().decorate(currentRainbowGum, newLogger);
			Logger oldInstance = loggerMap.putIfAbsent(name, decorated);
			return oldInstance == null ? decorated : oldInstance;
		}
	}

	/*
	 * While none is bound, use the global router and subscribe to its queue's transition
	 * into the next real router. No closed config, decorators, or contributors are used.
	 * Each event goes through the global router, so it reaches whatever router is bound
	 * then, even one that replaced a gum without closing it.
	 */
	private Logger unboundLogger(String name) {
		var existing = unboundLoggerMap.get(name);
		if (existing != null) {
			return existing;
		}
		var router = LogRouter.global();
		var level = router.levelResolver().resolveLevel(name);
		LogEventLogger sink = event -> {
			var route = router.route(name, event.level());
			if (route.isEnabled()) {
				route.log(event);
			}
		};
		var handler = maybeAddCallerInfo(name, false, sink, 1);
		var logger = ReplaceableLogger.of(level, handler);
		subscribe(name, router, logger, false);
		var oldInstance = unboundLoggerMap.putIfAbsent(name, logger);
		return oldInstance == null ? logger : oldInstance;
	}

	/*
	 * router here is whatever RootRouter was current at getLogger() time - during Spring
	 * Boot's pre-boot phase that is LogRouter.global() itself (the bootstrap RainbowGum's
	 * router() literally is the global router facade), so this subscribes to the *same*
	 * RouteChangePublisher that GlobalLogRouter transfers its queued subscribers into
	 * once a real router replaces the placeholder queue (see
	 * InternalRootRouter.setRouter/GlobalLogRouter._drain). r (below) is that replacement
	 * router - re-fetching the sink from it, not just the level, is what stops an
	 * already-created ReplaceableLogger from continuing to dispatch into the
	 * now-abandoned queue after the swap. eventLogger(name), not route(name, level), is
	 * used deliberately: the level is applied with the handler via set() instead, so
	 * gating it a second time at the sink would be redundant.
	 */
	private void subscribe(String name, RootRouter router, ReplaceableLogger changeable, boolean callerInfoEnabled) {
		router.onChange(new Consumer<RootRouter>() {

			@Override
			public void accept(RootRouter r) {
				var level = r.levelResolver().resolveLevel(name);
				var logger = r.eventLogger(name);
				var handler = maybeAddCallerInfo(name, callerInfoEnabled, logger, 1 + CALLER_WRAP_DEPTH);
				changeable.set(level, handler);
			}

		});
	}

	/*
	 * getLogger() wraps with LocationAwareForwardingLogger whenever callerInfoEnabled is
	 * true, and being a ForwardingLogger it adds exactly one extra frame to the plain
	 * (non-location-aware) SLF4J call path - see ForwardingLogger's default methods,
	 * which are one-line calls to delegate(). maybeAddCallerInfo's depth is only ever
	 * consulted when callerInfoEnabled is true (see below), which is the same condition
	 * that triggers that wrap, so adding this unconditionally here is safe even on paths
	 * (OffLogger) where the wrap never actually happens.
	 */
	private static final int CALLER_WRAP_DEPTH = 1;

	private LogEventHandler maybeAddCallerInfo(String loggerName, boolean callerInfoEnabled, LogEventLogger logger,
			int depth) {
		/*
		 * Every other registered context store (scoped key values, JBoss MDC, ...) goes
		 * underneath MDC, and the application's USER contributor above it. With nothing
		 * else registered the contributor hands back MDC's own unchanged.
		 */
		if (callerInfoEnabled) {
			return LogEventHandler.ofCallerInfo(loggerName, logger, mdc, depth, contributor);
		}
		return LogEventHandler.of(loggerName, logger, mdc, contributor);
	}

	/*
	 * Router callbacks can replace a bootstrap handler before the global gum change
	 * reaches this factory. All handlers share this delegate so those already created
	 * also receive the real configuration's contributors once it is bound.
	 */
	private static final class ChangingContributor implements KeyValuesSupport {

		private volatile KeyValuesContributor delegate;

		private volatile KeyValues.@Nullable JsonSerializer jsonSerializer;

		ChangingContributor(RainbowGum gum) {
			this.delegate = resolve(gum);
			this.jsonSerializer = KeyValues.JsonSerializer.of(gum.config());
		}

		void setRainbowGum(@Nullable RainbowGum gum) {
			delegate = resolve(gum);
			jsonSerializer = KeyValues.JsonSerializer.of(gum.config());
		}

		@Override
		public KeyValues.@Nullable JsonSerializer jsonSerializer() {
			return jsonSerializer;
		}

		private static KeyValuesContributor resolve(@Nullable RainbowGum gum) {
			if (gum == null) {
				return KeyValues::of;
			}
			return KeyValuesContributor.of(gum.config().serviceRegistry(), KeyValuesContributor.Source.Standard.SLF4J);
		}

		@Override
		public KeyValues keyValues() {
			return delegate.keyValues();
		}

		@Override
		public KeyValues keyValues(KeyValues own) {
			return delegate.keyValues(own);
		}

		@Override
		public void clear() {
			delegate.clear();
		}

	}

	sealed interface LoggerDecorator {

		public Logger decorate(RainbowGum gum, Logger logger);

		public static LoggerDecorator of(RainbowGum gum) {
			var services = gum.config()
				.serviceRegistry()
				.find(LoggerDecoratorService.class)
				.stream()
				.sorted(Comparator.comparingInt(LoggerDecoratorService::order)
					.thenComparing(LoggerDecoratorService::name))
				.toList();
			if (services.isEmpty()) {
				return Noop.INSTANCE;
			}
			return new CompositeLoggerDecorator(services);
		}

		enum Noop implements LoggerDecorator {

			INSTANCE;

			@Override
			public Logger decorate(RainbowGum gum, Logger logger) {
				return logger;
			}

		}

		record CompositeLoggerDecorator(List<LoggerDecoratorService> services) implements LoggerDecorator {

			@Override
			public Logger decorate(RainbowGum gum, Logger logger) {

				int i = 0;
				for (var p : services) {
					if (!(logger instanceof DepthAwareLogger da)) {
						return logger;
					}
					var next = Objects.requireNonNull(p.decorate(gum, da, i));
					/*
					 * Deliberately identity, not equals(): "did this particular decorator
					 * actually replace the logger instance" (used to advance the depth
					 * index), not "is the new logger value-equal to the old one".
					 */
					@SuppressWarnings("ReferenceEquality")
					boolean replaced = next != logger;
					if (replaced) {
						i++;
					}
					logger = next;
				}
				return logger;
			}

		}

	}

}
