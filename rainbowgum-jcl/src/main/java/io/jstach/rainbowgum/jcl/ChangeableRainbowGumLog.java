package io.jstach.rainbowgum.jcl;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.apache.commons.logging.Log;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter;

/**
 * Re-resolves the level and re-fetches the sink fresh on every call instead of caching
 * either - simpler than {@code io.jstach.rainbowgum.slf4j}'s
 * {@code ReplaceableLogger}/subscription approach, and correctly current by construction
 * rather than needing an explicit invalidation path. Worth the extra per-call lookup here
 * since a changeable logger name is, by definition, not the common case.
 */
final class ChangeableRainbowGumLog implements Log {

	private final String loggerName;

	private final LogRouter router;

	ChangeableRainbowGumLog(String loggerName, LogRouter router) {
		super();
		this.loggerName = loggerName;
		this.router = router;
	}

	boolean isLoggable(Level level) {
		return router.route(loggerName, LevelResolver.normalizeLevel(level)).isEnabled();
	}

	void log(Level level, Object obj) {
		log(level, obj, null);
	}

	void log(Level level, Object obj, @Nullable Throwable t) {
		level = LevelResolver.normalizeLevel(level);
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			@Nullable
			String formattedMessage = obj == null ? null : obj.toString();
			var currentThread = Thread.currentThread();
			LogEvent event = LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), level,
					loggerName, formattedMessage, KeyValues.of(), t);
			route.log(event);
		}
	}

	@Override
	public boolean isDebugEnabled() {
		return isLoggable(Level.DEBUG);
	}

	@Override
	public boolean isErrorEnabled() {
		return isLoggable(Level.ERROR);

	}

	@Override
	public boolean isFatalEnabled() {
		return isLoggable(Level.ERROR);
	}

	@Override
	public boolean isInfoEnabled() {
		return isLoggable(Level.INFO);
	}

	@Override
	public boolean isTraceEnabled() {
		return isLoggable(Level.TRACE);
	}

	@Override
	public boolean isWarnEnabled() {
		return isLoggable(Level.WARNING);
	}

	@Override
	public void trace(Object message) {
		log(Level.TRACE, message);
	}

	@Override
	public void trace(Object message, Throwable t) {
		log(Level.TRACE, message, t);

	}

	@Override
	public void debug(Object message) {
		log(Level.DEBUG, message);
	}

	@Override
	public void debug(Object message, Throwable t) {
		log(Level.DEBUG, message, t);
	}

	@Override
	public void info(Object message) {
		log(Level.INFO, message);
	}

	@Override
	public void info(Object message, Throwable t) {
		log(Level.INFO, message, t);

	}

	@Override
	public void warn(Object message) {
		log(Level.WARNING, message);
	}

	@Override
	public void warn(Object message, Throwable t) {
		log(Level.WARNING, message, t);
	}

	@Override
	public void error(Object message) {
		log(Level.ERROR, message);

	}

	@Override
	public void error(Object message, Throwable t) {
		log(Level.ERROR, message, t);
	}

	@Override
	public void fatal(Object message) {
		log(Level.ERROR, message);
	}

	@Override
	public void fatal(Object message, Throwable t) {
		log(Level.ERROR, message, t);

	}

}
