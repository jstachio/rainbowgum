package io.jstach.rainbowgum.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.RainbowGum;

class RainbowGumLoggerFactory implements ILoggerFactory {

	private final ConcurrentMap<String, Logger> loggerMap;

	private final RainbowGum rainbowGum;

	public RainbowGumLoggerFactory(RainbowGum rainbowGum) {
		super();
		this.loggerMap = new ConcurrentHashMap<>();
		this.rainbowGum = rainbowGum;
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

			Logger newLogger;
			var level = router.levelResolver().resolveLevel(name);
			if (changePublisher.isEnabled(name)) {
				/*
				 * We get a logger that can log everything.
				 */
				LogEventLogger logger = router.route(name, System.Logger.Level.ERROR);
				ChangeableLogger changeable = new ChangeableLogger(name, logger, Levels.toSlf4jLevel(level).toInt());
				changePublisher.subscribe(c -> {
					var slf4jLevel = Levels.toSlf4jLevel(router.levelResolver().resolveLevel(name));
					changeable.setLevel(slf4jLevel.toInt());
				});
				newLogger = changeable;
			}
			else {
				LogEventLogger logger = router.route(name, level);
				if (level == System.Logger.Level.OFF) {
					newLogger = new LevelLogger.OffLogger(name);
				}
				else {
					var slf4jLevel = Levels.toSlf4jLevel(level);
					newLogger = LevelLogger.of(slf4jLevel, name, logger);
				}
			}
			Logger oldInstance = loggerMap.putIfAbsent(name, newLogger);
			return oldInstance == null ? newLogger : oldInstance;
		}
	}

}
