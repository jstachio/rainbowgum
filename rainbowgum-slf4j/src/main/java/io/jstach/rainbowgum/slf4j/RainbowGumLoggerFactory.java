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

	private final RainbowGum rainbowGum;

	private final LoggerDecorator decorator;

	private final RainbowGumMDCAdapter mdc;

	public RainbowGumLoggerFactory(RainbowGum rainbowGum, RainbowGumMDCAdapter mdc) {
		super();
		this.loggerMap = new ConcurrentHashMap<>();
		this.rainbowGum = rainbowGum;
		this.decorator = LoggerDecorator.of(rainbowGum);
		this.mdc = mdc;
	}

	@Override
	public Logger getLogger(String name) {
		Logger simpleLogger = loggerMap.get(name);
		if (simpleLogger != null) {
			return simpleLogger;
		}
		else {
			var router = this.rainbowGum.router();
			var changePublisher = this.rainbowGum.config().changePublisher();

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
				subscribe(name, router, changeable);
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
			Logger decorated = decorator.decorate(rainbowGum, newLogger);
			Logger oldInstance = loggerMap.putIfAbsent(name, decorated);
			return oldInstance == null ? decorated : oldInstance;
		}
	}

	private void subscribe(String name, RootRouter router, LevelChangeable changeable) {
		record Slf4JOnChange(String name, LevelChangeable changeable) implements Consumer<RootRouter> {

			@Override
			public void accept(RootRouter r) {
				var slf4jLevel = Levels.toSlf4jLevel(r.levelResolver().resolveLevel(name));
				changeable.setLevel(slf4jLevel);
			}

		}
		router.onChange(new Slf4JOnChange(name, changeable));
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
