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
			LogEventLogger logger = this.rainbowGum.router();
			var level = this.rainbowGum.config().levelResolver().resolveLevel(name);

			Logger newLogger;
			if (level == System.Logger.Level.OFF) {
				newLogger = new LevelLogger.OffLogger(name);
			}
			else {
				var slf4jLevel = Levels.toSlf4jLevel(level);
				newLogger = LevelLogger.of(slf4jLevel, name, logger);

			}
			Logger oldInstance = loggerMap.putIfAbsent(name, newLogger);
			return oldInstance == null ? newLogger : oldInstance;
		}
	}

}
