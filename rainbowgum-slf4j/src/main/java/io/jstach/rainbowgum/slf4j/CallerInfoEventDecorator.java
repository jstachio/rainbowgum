package io.jstach.rainbowgum.slf4j;

import java.lang.System.Logger.Level;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogEventLogger;

final class CallerInfoEventDecorator implements LogEventHandler {

	private final LogEventLogger logger;

	private final int depth;

	private final String loggerName;

	private final RainbowGumMDCAdapter mdc;

	private final KeyValuesContributor contributor;

	public CallerInfoEventDecorator(String loggerName, RainbowGumMDCAdapter mdc, LogEventLogger logger) {
		this(loggerName, mdc, logger, NoopKeyValuesContributor.INSTANCE, CALLER_DEPTH_DELTA);
	}

	public CallerInfoEventDecorator(String loggerName, RainbowGumMDCAdapter mdc, LogEventLogger logger,
			KeyValuesContributor contributor, int depth) {
		super();
		this.loggerName = loggerName;
		this.mdc = mdc;
		this.logger = logger;
		this.contributor = contributor;
		this.depth = depth;
	}

	@Override
	public boolean isCallerAware() {
		return true;
	}

	@Override
	public LogEventHandler withDepth(int depth) {
		return new CallerInfoEventDecorator(loggerName, mdc, logger, contributor, CALLER_DEPTH_DELTA + depth);
	}

	@Override
	public KeyValuesContributor contributor() {
		return contributor;
	}

	@Override
	public void handle(LogEvent event, @Nullable Caller caller) {
		handle(withCaller(event, caller));

	}

	private static LogEvent withCaller(LogEvent event, @Nullable Caller caller) {
		if (caller == null) {
			return event;
		}
		return LogEvent.withCaller(event, caller);
	}

	@Override
	public void handle(LogEvent event) {
		this.logger.log(event);
	}

	public void handle(LogEvent event, int depth) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(event, caller);
	}

	@Override
	public void handle(Level level, String msg) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(eventNoArg(level, msg, (Throwable) null), caller);
	}

	@Override
	public void handle(Level level, String format, Throwable throwable) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(eventNoArg(level, format, throwable), caller);
	}

	@Override
	public void handle(Level level, String format, Object arg) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(eventOneArg(level, format, arg), caller);
	}

	@Override
	public void handle(Level level, String format, Object arg1, Object arg2) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(eventTwoArg(level, format, arg1, arg2), caller);
	}

	@Override
	public void handleArray(Level level, String format, Object[] args) {
		var caller = stackWalker.walk(s -> s.skip(depth).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		handle(eventArgs(level, format, args), caller);
	}

	@Override
	public String toString() {
		return "CallerInfoEventDecorator [depth=" + depth + ", logger=" + logger + "]";
	}

	@Override
	public String loggerName() {
		return this.loggerName;
	}

	@Override
	public RainbowGumMDCAdapter mdc() {
		return this.mdc;
	}

}
