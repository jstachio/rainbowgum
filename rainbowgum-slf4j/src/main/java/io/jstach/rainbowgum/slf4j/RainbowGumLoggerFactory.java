package io.jstach.rainbowgum.slf4j;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class RainbowGumLoggerFactory implements ILoggerFactory {

	private final ConcurrentMap<String, Logger> loggerMap;

	/*
	 * Spring Boot (and anything else with its own pre-boot bootstrap sequence) can
	 * install a bootstrap RainbowGum, have SLF4J's ServiceLoader-triggered initialize()
	 * capture it here, and only later RainbowGum.set(...) the "real" one - this factory
	 * would otherwise be stuck forever routing new getLogger(...) calls through the stale
	 * bootstrap instance. onGlobalChange (below) keeps this current. Already-created
	 * Logger instances in loggerMap are deliberately NOT rebound here - that is a
	 * separate, lower-priority concern already partially covered by
	 * ChangeableLogger/ReplaceableLogger's own per-logger level subscription.
	 */
	private volatile RainbowGum rainbowGum;

	private final LoggerDecorator decorator;

	private final RainbowGumMDCAdapter mdc;

	/*
	 * Held here (as opposed to inline at the subscribe call below) so this factory keeps
	 * it strongly reachable - RainbowGum.onGlobalChange only holds a weak reference, so
	 * an inline lambda with no other strong referrer could become eligible for GC almost
	 * immediately after subscribing.
	 */
	private final Consumer<RainbowGum> onGlobalChange = gum -> this.rainbowGum = gum;

	public RainbowGumLoggerFactory(RainbowGum rainbowGum, RainbowGumMDCAdapter mdc) {
		super();
		this.loggerMap = new ConcurrentHashMap<>();
		this.rainbowGum = rainbowGum;
		this.decorator = LoggerDecorator.of(rainbowGum);
		this.mdc = mdc;
		RainbowGum.onGlobalChange(onGlobalChange);
	}

	@Override
	public Logger getLogger(String name) {
		Logger simpleLogger = loggerMap.get(name);
		if (simpleLogger != null) {
			return simpleLogger;
		}
		else {
			var currentRainbowGum = this.rainbowGum;
			var router = currentRainbowGum.router();
			var changePublisher = currentRainbowGum.config().changePublisher();

			DepthAwareLogger newLogger;
			var level = router.levelResolver().resolveLevel(name);
			var allowedChanges = changePublisher.allowedChanges(name);
			if (allowedChanges.contains(ChangeType.LEVEL)) {
				/*
				 * We get a logger that can log everything.
				 */
				LogEventLogger logger = router.route(name, System.Logger.Level.ERROR);
				// boolean callerInfo = allowedChanges.contains(ChangeType.CALLER);
				// ChangeableLogger changeable = new ChangeableLogger(name, logger, mdc,
				// Levels.toSlf4jInt(level),
				// callerInfo);
				var handler = maybeAddCallerInfo(name, allowedChanges, logger, 1);
				var changeable = ReplaceableLogger.of(Levels.toSlf4jLevel(level), handler);
				subscribe(name, router, changeable, allowedChanges);
				newLogger = changeable;
			}
			else {
				LogEventLogger logger = router.route(name, level);
				if (level == System.Logger.Level.OFF) {
					newLogger = new LevelLogger.OffLogger(name);
				}
				else {
					var slf4jLevel = Levels.toSlf4jLevel(level);
					LogEventHandler handler = maybeAddCallerInfo(name, allowedChanges, logger, 0);
					newLogger = LevelLogger.of(slf4jLevel, handler);
				}
			}
			Logger decorated = decorator.decorate(currentRainbowGum, newLogger);
			Logger oldInstance = loggerMap.putIfAbsent(name, decorated);
			return oldInstance == null ? decorated : oldInstance;
		}
	}

	private void  subscribe(String name, RootRouter router, ReplaceableLogger changeable, Set<ChangeType> allowedChanges) {
		var consumer =  new Consumer<RootRouter> (){

			@Override
			public void accept(RootRouter r) {
				var level = r.levelResolver().resolveLevel(name);
				var slf4jLevel = Levels.toSlf4jLevel(level);
				changeable.setLevel(slf4jLevel);
				var logger = r.route(name, level);
				var handler = maybeAddCallerInfo(name, allowedChanges, logger, 1);
				changeable.setEventHandler(handler);
			}

		};
		router.onChange(consumer);
		// router.onChange(r -> {
		// var slf4jLevel = Levels.toSlf4jLevel(r.levelResolver().resolveLevel(name));
		// changeable.setLevel(slf4jLevel);
		// });
	}

	private LogEventHandler maybeAddCallerInfo(String loggerName, Set<ChangeType> allowedChanges, LogEventLogger logger,
			int depth) {
		LogEventHandler _logger;
		if (allowedChanges.contains(ChangeType.CALLER)) {
			_logger = LogEventHandler.ofCallerInfo(loggerName, logger, mdc, depth);
		}
		else {
			_logger = LogEventHandler.of(loggerName, logger, mdc);
		}
		return _logger;
	}

	sealed interface LoggerDecorator {

		public Logger decorate(RainbowGum gum, Logger logger);

		public static LoggerDecorator of(RainbowGum gum) {
			var array = gum.config()
				.serviceRegistry()
				.find(LoggerDecoratorService.class)
				.toArray(i -> new LoggerDecoratorService[i]);
			Arrays.sort(array,
					Comparator.comparingInt(LoggerDecoratorService::order).thenComparing(LoggerDecoratorService::name));
			if (array.length == 0) {
				return Noop.INSTANCE;
			}
			return new CompositeLoggerDecorator(array);
		}

		enum Noop implements LoggerDecorator {

			INSTANCE;

			@Override
			public Logger decorate(RainbowGum gum, Logger logger) {
				return logger;
			}

		}

		record CompositeLoggerDecorator(LoggerDecoratorService[] services) implements LoggerDecorator {

			@Override
			public Logger decorate(RainbowGum gum, Logger logger) {

				int i = 0;
				for (var p : services) {
					if (!(logger instanceof DepthAwareLogger da)) {
						return logger;
					}
					var next = Objects.requireNonNull(p.decorate(gum, da, i));
					if (next != logger) {
						i++;
					}
					logger = next;
				}
				return logger;
			}

		}

	}

}
