package io.jstach.rainbowgum.slf4j;

import java.lang.StackWalker.Option;
import java.lang.System.Logger.Level;

import org.jspecify.annotations.Nullable;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogEventLogger;

interface LogEventHandler extends LogEventFactory, LogEventLogger {

	/**
	 * Implemented by loggers whose dispatch target can be swapped after creation
	 * (currently only {@link ReplaceableLogger}), so a route change (e.g. the global
	 * router being replaced once a real RainbowGum loads) can rebind an already-created
	 * logger's handler instead of leaving it pointed at whatever it resolved to at
	 * creation time - the queued placeholder router, in the common pre-boot case.
	 */
	interface EventHandlerChangeable {

		void setEventHandler(LogEventHandler eventHandler);

	}

	default void log(LogEvent event) {
		handle(event);
	}

	void handle(LogEvent event);

	default void handle(LogEvent event, @Nullable Caller caller) {
		handle(event);
	}

	default void handle(Level level, String msg) {
		handle(eventNoArg(level, msg, (Throwable) null));
	}

	default void handle(Level level, String format, Throwable throwable) {
		handle(eventNoArg(level, format, throwable));
	}

	default void handle(Level level, String format, Object arg) {
		handle(eventOneArg(level, format, arg));
	}

	default void handle(Level level, String format, Object arg1, Object arg2) {
		handle(eventTwoArg(level, format, arg1, arg2));
	}

	default void handleArray(Level level, String format, Object[] args) {
		handle(eventArgs(level, format, args));
	}

	public boolean isCallerAware();

	@Override
	default KeyValues defaultKeyValues() {
		/*
		 * Do not copy here. The overwhelming majority of log calls use a synchronous
		 * publisher, especially now that virtual threads make blocking IO cheap, and a
		 * copy on every MDC-bearing log call would be pure garbage for that common case.
		 * Router.log() (LogRouter.java) freezes the event - which defensively copies the
		 * key values - only when the route is actually asynchronous, i.e. only when a
		 * copy is ever needed at all.
		 */
		return mdc().keyValues();
	}

	/**
	 * Like {@link #defaultKeyValues()} but a genuinely independent copy, safe for the
	 * caller to mutate further (e.g. to seed a fluent builder that will add more key
	 * values on top) - unlike {@link #defaultKeyValues()}, which is allowed to return
	 * MDC's live, not-yet-exposed instance for the common case where nothing further
	 * mutates it.
	 * @return a new mutable key values, safe to mutate.
	 */
	default MutableKeyValues copyDefaultKeyValues() {
		return mdc().copyMutableKeyValues();
	}

	public RainbowGumMDCAdapter mdc();

	default LoggingEventBuilder eventBuilder(org.slf4j.event.Level level) {
		return new RainbowGumEventBuilder(this, Levels.toSystemLevel(level));
	}

	public LogEventHandler withDepth(int depth);

	/**
	 * Merges {@code scopedDefaults}' own {@link LogEventFactory#defaultKeyValues()} (if
	 * not null) as the lowest-precedence source underneath {@code mdc}'s current
	 * key-values, which always win on a key collision - {@code null} (nothing registered)
	 * returns {@code mdc.keyValues()} unchanged, no allocation.
	 * @param mdc current thread's MDC.
	 * @param scopedDefaults optional lower-precedence source, or {@code null}.
	 * @return merged, read-only-by-convention key values.
	 */
	static KeyValues mergedDefaultKeyValues(RainbowGumMDCAdapter mdc, @Nullable LogEventFactory scopedDefaults) {
		if (scopedDefaults == null) {
			return mdc.keyValues();
		}
		var buf = MutableKeyValues.of();
		scopedDefaults.defaultKeyValues().forEach(buf);
		mdc.keyValues().forEach(buf);
		return buf;
	}

	/**
	 * Like {@link #mergedDefaultKeyValues(RainbowGumMDCAdapter, LogEventFactory)} but
	 * always a fresh, independently mutable copy - see {@link #copyDefaultKeyValues()}.
	 * @param mdc current thread's MDC.
	 * @param scopedDefaults optional lower-precedence source, or {@code null}.
	 * @return merged, mutable key values.
	 */
	static MutableKeyValues mergedCopyDefaultKeyValues(RainbowGumMDCAdapter mdc,
			@Nullable LogEventFactory scopedDefaults) {
		if (scopedDefaults == null) {
			return mdc.copyMutableKeyValues();
		}
		var buf = MutableKeyValues.of();
		scopedDefaults.defaultKeyValues().forEach(buf);
		mdc.keyValues().forEach(buf);
		return buf;
	}

	static LogEventHandler of(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc,
			@Nullable LogEventFactory scopedDefaults) {
		record DefaultLogEventHandler(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc,
				@Nullable LogEventFactory scopedDefaults) implements LogEventHandler {

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

			@Override
			public KeyValues defaultKeyValues() {
				return mergedDefaultKeyValues(mdc, scopedDefaults);
			}

			@Override
			public MutableKeyValues copyDefaultKeyValues() {
				return mergedCopyDefaultKeyValues(mdc, scopedDefaults);
			}
		}
		return new DefaultLogEventHandler(loggerName, logger, mdc, scopedDefaults);
	}

	static LogEventHandler ofCallerInfo(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc, int depth,
			@Nullable LogEventFactory scopedDefaults) {
		return new CallerInfoEventDecorator(loggerName, mdc, logger, scopedDefaults, depth + CALLER_DEPTH_DELTA);
	}

	static final int CALLER_DEPTH_DELTA = 2;

	static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

}
