package io.jstach.rainbowgum.slf4j.spi;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareEventBuilder;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareLogger;

/**
 * A base class for {@link LoggerDecoratorService} implementations that need to filter or
 * enrich log calls without depending on {@link org.slf4j.helpers.AbstractLogger}'s
 * internal dispatch, whose stack depth to the real caller is an unversioned SLF4J
 * implementation detail rather than something Rainbow Gum controls or tests.
 * <p>
 * Every convenience method ({@code info(String)}, {@code error(String,Object)}, marker
 * variants, ...) funnels through exactly one internal dispatch method before
 * {@link #isEnabled(Level)} and {@link #decorate(LoggingEventBuilder)} run, so the frame
 * count from any convenience method to caller-info capture is fixed and verified once by
 * this class's own tests. Subclasses never need to reason about stack depth.
 * <p>
 * The fluent {@code atInfo()} style API ({@link #makeLoggingEventBuilder(Level)}) is a
 * transparent pass-through to {@link #delegate()} and does <strong>not</strong> invoke
 * either hook, since there is no populated event to filter or decorate yet at that point.
 */
public abstract class AbstractFilteringLogger implements Logger, DepthAwareLogger {

	/*
	 * Frames between builder.log() and the real caller: this class's own normalizedLog(),
	 * plus the public convenience method (info(), error(), marker variant, ...) that
	 * called it. Both are declared in this class - never delegate one convenience method
	 * to another, or that adds an untracked frame. Fixed and verified by
	 * AbstractFilteringLoggerTest.
	 */
	private static final int DEPTH = 2;

	private final DepthAwareLogger delegate;

	/**
	 * Creates a filtering logger wrapping the given delegate.
	 * @param delegate logger to wrap.
	 */
	protected AbstractFilteringLogger(DepthAwareLogger delegate) {
		this.delegate = delegate;
	}

	/**
	 * The wrapped logger that ultimately receives log calls.
	 * @return delegate.
	 */
	public DepthAwareLogger delegate() {
		return delegate;
	}

	/**
	 * Cheap pre-check run before any {@link LoggingEventBuilder} is built, analogous to
	 * {@code isInfoEnabled()}. Override to filter out events (sampling, per-logger
	 * overrides, ...) without paying for message formatting or key-value collection.
	 * @param level level of the event.
	 * @return true if enabled and {@link #decorate(LoggingEventBuilder)} may run.
	 */
	protected boolean isEnabled(Level level) {
		return true;
	}

	/**
	 * Called with the already-populated builder (message, arguments and cause already set
	 * from whatever convenience method arity the caller used). Override to add key
	 * values, rewrite the message, etc.
	 * <p>
	 * <strong>Do not call {@code builder.log()} directly from this method</strong> -
	 * doing so adds an untracked stack frame and caller info will point at the wrong
	 * line. To mutate and let it log normally, just mutate {@code builder} and return
	 * {@code true}. To log it yourself (e.g. conditionally, or more than once) use
	 * {@link #log(LoggingEventBuilder)} instead of {@code builder.log()}, and return
	 * {@code false} so this class does not log it again. To drop the event entirely, do
	 * neither and return {@code false}.
	 * @param builder builder about to be logged.
	 * @return true to have this class call {@code builder.log()} after this method
	 * returns; false if you called {@link #log(LoggingEventBuilder)} yourself or want to
	 * drop the event.
	 */
	protected boolean decorate(LoggingEventBuilder builder) {
		return true;
	}

	/**
	 * Logs a builder obtained from a {@link #decorate(LoggingEventBuilder)} override that
	 * returned {@code false}, with caller-info depth adjusted for the extra frames both
	 * {@link #decorate(LoggingEventBuilder)} and this method itself add. Never call
	 * {@code builder.log()} directly from {@link #decorate(LoggingEventBuilder)}.
	 * @param builder builder to log, previously handed to
	 * {@link #decorate(LoggingEventBuilder)}.
	 */
	protected final void log(LoggingEventBuilder builder) {
		/*
		 * +2, not +1: the call chain here is normalizedLog() -> decorate() -> log() ->
		 * builder.log(), i.e. both decorate() and this method are extra frames beyond the
		 * normalizedLog()+convenience-method pair DEPTH already accounts for.
		 */
		DepthAwareEventBuilder.setDepth(builder, DEPTH + 2);
		builder.log();
	}

	@Override
	public String getName() {
		return delegate.getName();
	}

	/**
	 * A no-op: unlike {@link org.slf4j.helpers.AbstractLogger}-based decorators, this
	 * class's caller-info depth is a fixed internal constant rather than derived from the
	 * wrap-count
	 * {@link LoggerDecoratorService#decorate(RainbowGum, DepthAwareLogger, int)} passes
	 * in, so there is nothing to recreate.
	 * @param depth ignored.
	 * @return this.
	 */
	@Override
	public Logger withDepth(int depth) {
		return this;
	}

	@Override
	public boolean isEnabledForLevel(Level level) {
		return delegate.isEnabledForLevel(level) && isEnabled(level);
	}

	@Override
	public LoggingEventBuilder makeLoggingEventBuilder(Level level) {
		if (!delegate.isEnabledForLevel(level)) {
			return NOPLoggingEventBuilder.singleton();
		}
		return delegate.makeLoggingEventBuilder(level);
	}

	private void normalizedLog(Level level, @Nullable String message, @Nullable Object arg1, @Nullable Object arg2,
			@Nullable Object @Nullable [] argArray, @Nullable Throwable throwable) {
		if (!delegate.isEnabledForLevel(level) || !isEnabled(level)) {
			return;
		}
		var builder = DepthAwareEventBuilder.setDepth(delegate.makeLoggingEventBuilder(level), DEPTH);
		if (message != null) {
			builder.setMessage(message);
		}
		if (arg1 != null) {
			builder.addArgument(arg1);
		}
		if (arg2 != null) {
			builder.addArgument(arg2);
		}
		if (argArray != null) {
			for (var a : argArray) {
				builder.addArgument(a);
			}
		}
		if (throwable != null) {
			builder.setCause(throwable);
		}
		if (decorate(builder)) {
			builder.log();
		}
	}

	@Override
	public boolean isTraceEnabled() {
		return isEnabledForLevel(Level.TRACE);
	}

	@Override
	public boolean isTraceEnabled(Marker marker) {
		return isTraceEnabled();
	}

	@Override
	public void trace(String msg) {
		normalizedLog(Level.TRACE, msg, null, null, null, null);
	}

	@Override
	public void trace(String format, Object arg) {
		normalizedLog(Level.TRACE, format, arg, null, null, null);
	}

	@Override
	public void trace(String format, Object arg1, Object arg2) {
		normalizedLog(Level.TRACE, format, arg1, arg2, null, null);
	}

	@Override
	public void trace(String format, Object... arguments) {
		normalizedLog(Level.TRACE, format, null, null, arguments, null);
	}

	@Override
	public void trace(String msg, Throwable t) {
		normalizedLog(Level.TRACE, msg, null, null, null, t);
	}

	@Override
	public void trace(Marker marker, String msg) {
		normalizedLog(Level.TRACE, msg, null, null, null, null);
	}

	@Override
	public void trace(Marker marker, String format, Object arg) {
		normalizedLog(Level.TRACE, format, arg, null, null, null);
	}

	@Override
	public void trace(Marker marker, String format, Object arg1, Object arg2) {
		normalizedLog(Level.TRACE, format, arg1, arg2, null, null);
	}

	@Override
	public void trace(Marker marker, String format, Object... argArray) {
		normalizedLog(Level.TRACE, format, null, null, argArray, null);
	}

	@Override
	public void trace(Marker marker, String msg, Throwable t) {
		normalizedLog(Level.TRACE, msg, null, null, null, t);
	}

	@Override
	public LoggingEventBuilder atTrace() {
		return makeLoggingEventBuilder(Level.TRACE);
	}

	@Override
	public boolean isDebugEnabled() {
		return isEnabledForLevel(Level.DEBUG);
	}

	@Override
	public boolean isDebugEnabled(Marker marker) {
		return isDebugEnabled();
	}

	@Override
	public void debug(String msg) {
		normalizedLog(Level.DEBUG, msg, null, null, null, null);
	}

	@Override
	public void debug(String format, Object arg) {
		normalizedLog(Level.DEBUG, format, arg, null, null, null);
	}

	@Override
	public void debug(String format, Object arg1, Object arg2) {
		normalizedLog(Level.DEBUG, format, arg1, arg2, null, null);
	}

	@Override
	public void debug(String format, Object... arguments) {
		normalizedLog(Level.DEBUG, format, null, null, arguments, null);
	}

	@Override
	public void debug(String msg, Throwable t) {
		normalizedLog(Level.DEBUG, msg, null, null, null, t);
	}

	@Override
	public void debug(Marker marker, String msg) {
		normalizedLog(Level.DEBUG, msg, null, null, null, null);
	}

	@Override
	public void debug(Marker marker, String format, Object arg) {
		normalizedLog(Level.DEBUG, format, arg, null, null, null);
	}

	@Override
	public void debug(Marker marker, String format, Object arg1, Object arg2) {
		normalizedLog(Level.DEBUG, format, arg1, arg2, null, null);
	}

	@Override
	public void debug(Marker marker, String format, Object... argArray) {
		normalizedLog(Level.DEBUG, format, null, null, argArray, null);
	}

	@Override
	public void debug(Marker marker, String msg, Throwable t) {
		normalizedLog(Level.DEBUG, msg, null, null, null, t);
	}

	@Override
	public LoggingEventBuilder atDebug() {
		return makeLoggingEventBuilder(Level.DEBUG);
	}

	@Override
	public boolean isInfoEnabled() {
		return isEnabledForLevel(Level.INFO);
	}

	@Override
	public boolean isInfoEnabled(Marker marker) {
		return isInfoEnabled();
	}

	@Override
	public void info(String msg) {
		normalizedLog(Level.INFO, msg, null, null, null, null);
	}

	@Override
	public void info(String format, Object arg) {
		normalizedLog(Level.INFO, format, arg, null, null, null);
	}

	@Override
	public void info(String format, Object arg1, Object arg2) {
		normalizedLog(Level.INFO, format, arg1, arg2, null, null);
	}

	@Override
	public void info(String format, Object... arguments) {
		normalizedLog(Level.INFO, format, null, null, arguments, null);
	}

	@Override
	public void info(String msg, Throwable t) {
		normalizedLog(Level.INFO, msg, null, null, null, t);
	}

	@Override
	public void info(Marker marker, String msg) {
		normalizedLog(Level.INFO, msg, null, null, null, null);
	}

	@Override
	public void info(Marker marker, String format, Object arg) {
		normalizedLog(Level.INFO, format, arg, null, null, null);
	}

	@Override
	public void info(Marker marker, String format, Object arg1, Object arg2) {
		normalizedLog(Level.INFO, format, arg1, arg2, null, null);
	}

	@Override
	public void info(Marker marker, String format, Object... argArray) {
		normalizedLog(Level.INFO, format, null, null, argArray, null);
	}

	@Override
	public void info(Marker marker, String msg, Throwable t) {
		normalizedLog(Level.INFO, msg, null, null, null, t);
	}

	@Override
	public LoggingEventBuilder atInfo() {
		return makeLoggingEventBuilder(Level.INFO);
	}

	@Override
	public boolean isWarnEnabled() {
		return isEnabledForLevel(Level.WARN);
	}

	@Override
	public boolean isWarnEnabled(Marker marker) {
		return isWarnEnabled();
	}

	@Override
	public void warn(String msg) {
		normalizedLog(Level.WARN, msg, null, null, null, null);
	}

	@Override
	public void warn(String format, Object arg) {
		normalizedLog(Level.WARN, format, arg, null, null, null);
	}

	@Override
	public void warn(String format, Object arg1, Object arg2) {
		normalizedLog(Level.WARN, format, arg1, arg2, null, null);
	}

	@Override
	public void warn(String format, Object... arguments) {
		normalizedLog(Level.WARN, format, null, null, arguments, null);
	}

	@Override
	public void warn(String msg, Throwable t) {
		normalizedLog(Level.WARN, msg, null, null, null, t);
	}

	@Override
	public void warn(Marker marker, String msg) {
		normalizedLog(Level.WARN, msg, null, null, null, null);
	}

	@Override
	public void warn(Marker marker, String format, Object arg) {
		normalizedLog(Level.WARN, format, arg, null, null, null);
	}

	@Override
	public void warn(Marker marker, String format, Object arg1, Object arg2) {
		normalizedLog(Level.WARN, format, arg1, arg2, null, null);
	}

	@Override
	public void warn(Marker marker, String format, Object... argArray) {
		normalizedLog(Level.WARN, format, null, null, argArray, null);
	}

	@Override
	public void warn(Marker marker, String msg, Throwable t) {
		normalizedLog(Level.WARN, msg, null, null, null, t);
	}

	@Override
	public LoggingEventBuilder atWarn() {
		return makeLoggingEventBuilder(Level.WARN);
	}

	@Override
	public boolean isErrorEnabled() {
		return isEnabledForLevel(Level.ERROR);
	}

	@Override
	public boolean isErrorEnabled(Marker marker) {
		return isErrorEnabled();
	}

	@Override
	public void error(String msg) {
		normalizedLog(Level.ERROR, msg, null, null, null, null);
	}

	@Override
	public void error(String format, Object arg) {
		normalizedLog(Level.ERROR, format, arg, null, null, null);
	}

	@Override
	public void error(String format, Object arg1, Object arg2) {
		normalizedLog(Level.ERROR, format, arg1, arg2, null, null);
	}

	@Override
	public void error(String format, Object... arguments) {
		normalizedLog(Level.ERROR, format, null, null, arguments, null);
	}

	@Override
	public void error(String msg, Throwable t) {
		normalizedLog(Level.ERROR, msg, null, null, null, t);
	}

	@Override
	public void error(Marker marker, String msg) {
		normalizedLog(Level.ERROR, msg, null, null, null, null);
	}

	@Override
	public void error(Marker marker, String format, Object arg) {
		normalizedLog(Level.ERROR, format, arg, null, null, null);
	}

	@Override
	public void error(Marker marker, String format, Object arg1, Object arg2) {
		normalizedLog(Level.ERROR, format, arg1, arg2, null, null);
	}

	@Override
	public void error(Marker marker, String format, Object... argArray) {
		normalizedLog(Level.ERROR, format, null, null, argArray, null);
	}

	@Override
	public void error(Marker marker, String msg, Throwable t) {
		normalizedLog(Level.ERROR, msg, null, null, null, t);
	}

	@Override
	public LoggingEventBuilder atError() {
		return makeLoggingEventBuilder(Level.ERROR);
	}

}
