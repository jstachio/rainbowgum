package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.slf4j.LogEventHandler.EventHandlerChangeable;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class ReplaceableLogger implements ForwardingLogger, LevelChangeable, EventHandlerChangeable, DepthAwareLogger {

	private volatile LevelLogger logger;

	private ReplaceableLogger(LevelLogger logger) {
		super();
		this.logger = logger;
	}

	static ReplaceableLogger of(Level level, LogEventHandler handler) {
		var logger = LevelLogger.of(level, handler);
		return new ReplaceableLogger(logger);
	}

	@Override
	public Logger delegate() {
		return this.logger;
	}

	@Override
	public void setLevel(Level level) {
		this.logger = LevelLogger.of(level, logger.handler());
	}

	@Override
	public void setEventHandler(LogEventHandler eventHandler) {
		this.logger = LevelLogger.of(logger.level(), eventHandler);
	}

	@Override
	public ReplaceableLogger withDepth(int depth) {
		return new ReplaceableLogger(logger.withDepth(depth));
	}

	@Override
	public String toString() {
		return "ReplaceableLogger [logger=" + logger + "]";
	}

}
