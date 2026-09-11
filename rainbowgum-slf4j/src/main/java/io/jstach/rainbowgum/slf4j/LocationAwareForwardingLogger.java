package io.jstach.rainbowgum.slf4j;

import java.lang.StackWalker.StackFrame;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.LoggingEvent;
import org.slf4j.spi.LocationAwareLogger;
import org.slf4j.spi.LoggingEventAware;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

/**
 * Wraps a caller-info-enabled logger with {@link LocationAwareLogger} and
 * {@link LoggingEventAware} so that bridges - and, for {@link LoggingEventAware}, any
 * fluent-API builder other than this module's own {@link RainbowGumEventBuilder}, which
 * already resolves caller info correctly on its own - get correct caller info out of
 * their single "load bearing" call
 * ({@link #log(Marker, String, int, String, Object[], Throwable)} or
 * {@link #log(LoggingEvent)}) instead of falling back to a plain, arity-losing dispatch.
 * <p>
 * {@code LoggingEventAware} is checked first by SLF4J's own
 * {@code DefaultLoggingEventBuilder} (confirmed by decompiling it: <a href=
 * "https://github.com/qos-ch/slf4j/blob/master/slf4j-api/src/main/java/org/slf4j/spi/DefaultLoggingEventBuilder.java">
 * DefaultLoggingEventBuilder.log(LoggingEvent)</a>), before falling back to
 * {@code LocationAwareLogger} and then plain {@code Logger} - but that three-way chain
 * only ever runs when something other than this module's own {@code atInfo()}/etc.
 * overrides builds the event: {@link LevelLogger} already returns
 * {@link RainbowGumEventBuilder} directly for its own fluent calls, bypassing
 * {@code DefaultLoggingEventBuilder} (and this dispatch chain) entirely. So this only
 * matters when a third party constructs its own {@code LoggingEventBuilder} around a
 * {@code Logger} obtained from this factory - which is exactly the shape of the
 * jcl-over-slf4j scenario this module's {@code LocationAwareLogger} support already
 * handles, just for the fluent API instead of the arity-specific one.
 * <p>
 * Only used when {@code ChangeType.CALLER} is enabled for a logger name (see
 * {@code RainbowGumLoggerFactory}) - the fixed-depth {@link CallerInfoEventDecorator}
 * mechanism used for every other logging call, including this module's own fluent API via
 * {@link RainbowGumEventBuilder}, is untouched and remains the fast default; this class
 * exists only for entry points where the caller's depth is genuinely unknown ahead of
 * time.
 */
final class LocationAwareForwardingLogger
		implements ForwardingLogger, LocationAwareLogger, LoggingEventAware, DepthAwareLogger, LogEventFactory {

	private static final StackWalker STACK_WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	/*
	 * Defensive bound so a bridge whose fqcn never actually appears on the stack (a
	 * misconfigured bridge, or someone calling this method directly) cannot make this
	 * walk unbounded.
	 */
	private static final int MAX_FRAMES = 32;

	private static final Object[] EMPTY_ARGS = new Object[0];

	private final DepthAwareLogger delegate;

	private final HandlerSource handlerSource;

	private final String loggerName;

	private final RainbowGumMDCAdapter mdc;

	LocationAwareForwardingLogger(DepthAwareLogger delegate, HandlerSource handlerSource, String loggerName,
			RainbowGumMDCAdapter mdc) {
		this.delegate = delegate;
		this.handlerSource = handlerSource;
		this.loggerName = loggerName;
		this.mdc = mdc;
	}

	@Override
	public DepthAwareLogger delegate() {
		return delegate;
	}

	@Override
	public org.slf4j.Logger withDepth(int depth) {
		var rewrapped = delegate.withDepth(depth);
		if (rewrapped instanceof DepthAwareLogger da && rewrapped instanceof HandlerSource hs) {
			return new LocationAwareForwardingLogger(da, hs, loggerName, mdc);
		}
		throw new AssertionError(
				"withDepth() on a LocationAwareForwardingLogger delegate must preserve DepthAwareLogger and HandlerSource: "
						+ rewrapped);
	}

	@Override
	public String loggerName() {
		return loggerName;
	}

	@Override
	public KeyValues defaultKeyValues() {
		return mdc.keyValues();
	}

	@Override
	public void log(@Nullable Marker marker, String fqcn, int level, @Nullable String message,
			Object @Nullable [] argArray, @Nullable Throwable t) {
		var slf4jLevel = Levels.toSlf4jLevel(level);
		if (!delegate.isEnabledForLevel(slf4jLevel)) {
			return;
		}
		var caller = findCaller(fqcn);
		var sysLevel = Levels.toSystemLevel(slf4jLevel);
		var event = eventArgs(sysLevel, message, argArray == null ? EMPTY_ARGS : argArray, t);
		handlerSource.currentHandler().handle(event, caller);
	}

	@Override
	public void log(LoggingEvent event) {
		var level = event.getLevel();
		if (!delegate.isEnabledForLevel(level)) {
			return;
		}
		/*
		 * DefaultLoggingEventBuilder always sets this to its own fqcn before dispatch if
		 * nothing else set it first (confirmed by decompiling it), so this is null only
		 * for a LoggingEvent built by something other than that class - handled the same
		 * way as an fqcn that's never found: no caller info rather than a failure.
		 */
		var fqcn = event.getCallerBoundary();
		var caller = fqcn == null ? null : findCaller(fqcn);
		handlerSource.currentHandler().handle(toLogEvent(event), caller);
	}

	/*
	 * Not built via the eventArgs(...) convenience overload because defaultKeyValues()
	 * there is always just the ambient MDC - a third-party LoggingEventBuilder's own
	 * addKeyValue(...) calls need to be layered on top of that, the same way
	 * RainbowGumEventBuilder.kvs() layers its own addKeyValue(...) calls on top of a copy
	 * of the MDC.
	 */
	private LogEvent toLogEvent(LoggingEvent event) {
		var pairs = event.getKeyValuePairs();
		KeyValues keyValues = mdc.keyValues();
		if (!pairs.isEmpty()) {
			var mutable = mdc.copyMutableKeyValues();
			for (var kv : pairs) {
				mutable.putKeyValue(kv.key, kv.value == null ? null : kv.value.toString());
			}
			keyValues = mutable;
		}
		var thread = Thread.currentThread();
		var args = event.getArgumentArray();
		return LogEvent.ofAll(Instant.now(), thread.getName(), thread.threadId(),
				Levels.toSystemLevel(event.getLevel()), loggerName, event.getMessage(), keyValues, event.getThrowable(),
				messageFormatter(), args == null ? EMPTY_ARGS : args);
	}

	/*
	 * Mirrors tinylog's ModernJavaRuntime.DynamicStackFrameExtractor and Logback's
	 * CallerData.extract: skip frames until one's class name matches fqcn (the bridge),
	 * then skip the contiguous run of matching frames, and report the first frame after
	 * that run as the caller. Both are independent industry precedents for this exact
	 * "depth unknown, only the bridge's class name is known" situation - there is no
	 * cheaper known technique here, only the fixed-depth path (unaffected by this class)
	 * is cheap.
	 */
	private static @Nullable Caller findCaller(String fqcn) {
		return STACK_WALKER.walk(frames -> {
			var it = frames.limit(MAX_FRAMES).iterator();
			while (it.hasNext()) {
				if (fqcn.equals(it.next().getClassName())) {
					break;
				}
			}
			while (it.hasNext()) {
				StackFrame frame = it.next();
				if (!fqcn.equals(frame.getClassName())) {
					return Caller.of(frame);
				}
			}
			return null;
		});
	}

	@Override
	public String toString() {
		return "LocationAwareForwardingLogger [delegate=" + delegate + "]";
	}

}
