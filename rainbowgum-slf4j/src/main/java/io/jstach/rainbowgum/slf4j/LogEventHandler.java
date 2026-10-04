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
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
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

	@Override
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

	/**
	 * The other registered key values contributors, merged with {@link #mdc()}'s current
	 * key values by {@link #defaultKeyValues()} and {@link #copyDefaultKeyValues()}: MDC
	 * wins over all of them except the application's own USER contributor. Defaults to a
	 * no-op contributor so no call site ever needs to null check. A real one is only ever
	 * supplied at handler-construction time in {@link RainbowGumLoggerFactory}.
	 * @return contributor, never {@code null}.
	 */
	default KeyValuesContributor contributor() {
		return NoopKeyValuesContributor.INSTANCE;
	}

	@Override
	default KeyValues defaultKeyValues() {
		/*
		 * The contributor hands back mdc().keyValues() unchanged when nothing else
		 * contributes (the overwhelming majority of log calls) - no composite, no copy,
		 * for that common case. Router.log() (LogRouter.java) freezes the event - which
		 * defensively copies the key values - only when the route is actually
		 * asynchronous, i.e. only when a copy is ever needed at all.
		 */
		return contributor().keyValues(mdc().keyValues());
	}

	/**
	 * Like {@link #defaultKeyValues()} but a genuinely independent copy, safe for the
	 * caller to mutate further (e.g. to seed a fluent builder that will add more key
	 * values on top) - unlike {@link #defaultKeyValues()}, which is allowed to return
	 * MDC's live, not-yet-exposed instance for the common case where nothing further
	 * mutates it.
	 * @return a new mutable key values, safe to mutate.
	 */
	@SuppressWarnings("ReferenceEquality") // unchanged MDC means nothing else contributed
	default MutableKeyValues copyDefaultKeyValues() {
		var own = mdc().keyValues();
		var merged = contributor().keyValues(own);
		if (merged == own) {
			return mdc().copyMutableKeyValues();
		}
		var buf = MutableKeyValues.of();
		merged.forEach(buf);
		return buf;
	}

	public RainbowGumMDCAdapter mdc();

	default LoggingEventBuilder eventBuilder(org.slf4j.event.Level level) {
		return new RainbowGumEventBuilder(this, Levels.toSystemLevel(level));
	}

	public LogEventHandler withDepth(int depth);

	static LogEventHandler of(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc,
			KeyValuesContributor contributor) {
		record DefaultLogEventHandler(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc,
				KeyValuesContributor contributor) implements LogEventHandler {

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
		return new DefaultLogEventHandler(loggerName, logger, mdc, contributor);
	}

	static LogEventHandler ofCallerInfo(String loggerName, LogEventLogger logger, RainbowGumMDCAdapter mdc, int depth,
			KeyValuesContributor contributor) {
		return new CallerInfoEventDecorator(loggerName, mdc, logger, contributor, depth + CALLER_DEPTH_DELTA);
	}

	static final int CALLER_DEPTH_DELTA = 2;

	static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

}
