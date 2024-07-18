package io.jstach.rainbowgum.slf4j;

import java.lang.StackWalker.Option;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventLogger;

interface LogEventHandler extends EventCreator<Level>, LogEventLogger {

	@Override
	default java.lang.System.Logger.Level translateLevel(Level level) {
		return Levels.toSystemLevel(level);
	}

	default void log(LogEvent event) {
		handle(event);
	}

	void handle(LogEvent event);

	default void handle(LogEvent event, @Nullable Caller caller) {
		handle(event);
	}

	default void handle(Level level, String msg) {
		handle(event0(level, msg));
	}

	default void handle(Level level, String format, Throwable throwable) {
		handle(event(level, format, throwable));
	}

	default void handle(Level level, String format, Object arg) {
		handle(event1(level, format, arg));
	}

	default void handle(Level level, String format, Object arg1, Object arg2) {
		handle(event2(level, format, arg1, arg2));
	}

	default void handleArray(Level level, String format, Object[] args) {
		handle(eventArray(level, format, args));
	}

	public boolean isCallerAware();

	@Override
	default KeyValues keyValues() {
		var mdc = mdc();
		var m = mdc.mutableKeyValuesOrNull();
		if (m != null) {
			return m;
		}
		return KeyValues.of();
	}

	public RainbowGumMDCAdapter mdc();

	default LoggingEventBuilder eventBuilder(Level level) {
		return new RainbowGumEventBuilder(this, mdc(), translateLevel(level));
	}

	public LogEventHandler withDepth(int depth);

	static LogEventHandler of(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc) {
		record DefaultLogEventHandler(String loggerName, LogEventLogger logger,
				RainbowGumMDCAdapter mdc) implements LogEventHandler {

			@Override
			public void handle(LogEvent event) {
				logger.log(event);
			}

			@Override
			public LogEventHandler withDepth(int depth) {
				return this;
			}

			@Override
			public boolean isCallerAware() {
				return false;
			}
		}
		return new DefaultLogEventHandler(loggerName, logger, mdc);
	}

	static LogEventHandler ofCallerInfo(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc, int depth) {
		return new CallerInfoEventDecorator(loggerName, mdc, logger, depth + CALLER_DEPTH_DELTA);
	}

	static final int CALLER_DEPTH_DELTA = 2;

	static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

}
