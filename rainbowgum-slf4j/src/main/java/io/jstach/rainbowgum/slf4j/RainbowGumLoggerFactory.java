package io.jstach.rainbowgum.slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import io.jstach.rainbowgum.LogAppender;
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
			LogAppender appender = this.rainbowGum.router();
			var level = this.rainbowGum.config().logLevel(name);
			Logger newInstance = new RainbowGumLogger(name, appender, level);
			Logger oldInstance = loggerMap.putIfAbsent(name, newInstance);
			return oldInstance == null ? newInstance : oldInstance;
		}
	}

}
