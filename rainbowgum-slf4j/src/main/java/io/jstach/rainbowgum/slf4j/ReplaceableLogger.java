package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.slf4j.LogEventHandler.EventHandlerChangeable;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

class ReplaceableLogger
		implements ForwardingLogger, LevelChangeable, EventHandlerChangeable, DepthAwareLogger, HandlerSource {

	/*
	 * OFF has no SLF4J enum value. Keep the system level and handler even while disabled.
	 */
	private record State(System.Logger.Level level, LogEventHandler handler, Logger delegate) {
		static State of(System.Logger.Level level, LogEventHandler handler) {
			Logger delegate = level == System.Logger.Level.OFF ? new LevelLogger.OffLogger(handler.loggerName())
					: LevelLogger.of(Levels.toSlf4jLevel(level), handler);
			return new State(level, handler, delegate);
		}
	}

	private volatile State state;

	private ReplaceableLogger(System.Logger.Level level, LogEventHandler handler) {
		this.state = State.of(level, handler);
	}

	static ReplaceableLogger of(Level level, LogEventHandler handler) {
		return of(Levels.toSystemLevel(level), handler);
	}

	static ReplaceableLogger of(System.Logger.Level level, LogEventHandler handler) {
		return new ReplaceableLogger(level, handler);
	}

	@Override
	public Logger delegate() {
		return state.delegate();
	}

	@Override
	public void setLevel(Level level) {
		setLevel(Levels.toSystemLevel(level));
	}

	void setLevel(System.Logger.Level level) {
		state = State.of(level, state.handler());
	}

	@Override
	public void setEventHandler(LogEventHandler eventHandler) {
		state = State.of(state.level(), eventHandler);
	}

	/*
	 * One write, so a logging thread never sees the new level with the old router's
	 * handler.
	 */
	void set(System.Logger.Level level, LogEventHandler eventHandler) {
		state = State.of(level, eventHandler);
	}

	@Override
	public LogEventHandler currentHandler() {
		return state.handler();
	}

	@Override
	public ReplaceableLogger withDepth(int depth) {
		var current = state;
		return new ReplaceableLogger(current.level(), current.handler().withDepth(depth));
	}

	@Override
	public String toString() {
		return "ReplaceableLogger [logger=" + state.delegate() + "]";
	}

}
