package io.jstach.rainbowgum.slf4j;

import java.lang.StackWalker.StackFrame;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LocationAwareLogger;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

/**
 * Wraps a caller-info-enabled logger with {@link LocationAwareLogger} so that bridges
 * (jcl-over-slf4j in particular, the motivating case) get correct caller info out of
 * their single "load bearing"
 * {@link #log(Marker, String, int, String, Object[], Throwable)} call instead of falling
 * back to a plain, arity-losing dispatch.
 * <p>
 * Only used when {@code ChangeType.CALLER} is enabled for a logger name (see
 * {@code RainbowGumLoggerFactory}) - the fixed-depth {@link CallerInfoEventDecorator}
 * mechanism used for every other logging call is untouched and remains the fast default;
 * this class exists only for the one entry point where the caller's depth is genuinely
 * unknown ahead of time.
 */
final class LocationAwareForwardingLogger
		implements ForwardingLogger, LocationAwareLogger, DepthAwareLogger, EventCreator<Level> {

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
	public System.Logger.Level translateLevel(Level level) {
		return Levels.toSystemLevel(level);
	}

	@Override
	public KeyValues keyValues() {
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
		var event = eventArray(slf4jLevel, message, argArray == null ? EMPTY_ARGS : argArray, t);
		handlerSource.currentHandler().handle(event, caller);
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
