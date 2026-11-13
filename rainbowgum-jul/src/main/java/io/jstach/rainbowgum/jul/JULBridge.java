package io.jstach.rainbowgum.jul;

import java.time.Instant;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.LogRecord;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogRouter;

/**
 * Translates {@code java.util.logging.LogRecord} into Rainbow Gum
 * {@link io.jstach.rainbowgum.LogEvent} and routes it through {@link LogRouter#global()}.
 * Shared by two independent JUL integration strategies: this module's own
 * {@link java.util.logging.Handler}-based bridge ({@code SystemLoggerQueueJULHandler},
 * installed on the default JUL root logger), and the separate
 * <code>rainbowgum-jul-logmanager</code> module's {@code java.util.logging.LogManager}
 * replacement (which routes every {@code java.util.logging.Logger} call directly,
 * bypassing JUL's normal Handler/Level dispatch entirely). Kept here, exported, so
 * neither integration duplicates the level mapping or event construction.
 */
public final class JULBridge {

	private JULBridge() {
	}

	/*
	 * JUL has no context of its own so every registered contributor (SLF4J MDC, scoped
	 * key values, ...) applies.
	 */
	private static final KeyValuesContributor KEY_VALUES = KeyValuesContributor.global();

	/*
	 * Set while this thread routes a JUL record or checks a JUL level. Rainbow Gum can
	 * log through JUL while it does that, for example when its properties come from a
	 * framework that logs every lookup through JUL. Such a nested record is dropped, as
	 * Rainbow Gum drops other logging done while it logs, because routing it would
	 * resolve the same level again until the stack overflows.
	 */
	private static final ThreadLocal<@Nullable Boolean> ACTIVE = new ThreadLocal<>();

	private static final int TRACE_LEVEL_THRESHOLD = java.util.logging.Level.FINEST.intValue();

	private static final int DEBUG_LEVEL_THRESHOLD = java.util.logging.Level.FINE.intValue();

	private static final int INFO_LEVEL_THRESHOLD = java.util.logging.Level.INFO.intValue();

	private static final int WARN_LEVEL_THRESHOLD = java.util.logging.Level.WARNING.intValue();

	/**
	 * Maps a {@code java.util.logging.Level} to the closest
	 * {@link java.lang.System.Logger.Level}, the same threshold buckets JUL's own builtin
	 * levels (FINEST/FINE/INFO/WARNING/SEVERE) fall into, so custom intermediate levels
	 * still translate sensibly.
	 * @param level JUL level, including custom levels with arbitrary
	 * {@link java.util.logging.Level#intValue()}.
	 * @return closest matching System.Logger level.
	 */
	public static System.Logger.Level toLevel(java.util.logging.Level level) {
		int lv = level.intValue();
		if (Integer.MIN_VALUE == lv) {
			return System.Logger.Level.TRACE;
		}
		else if (TRACE_LEVEL_THRESHOLD >= lv) {
			return System.Logger.Level.TRACE;
		}
		else if (DEBUG_LEVEL_THRESHOLD >= lv) {
			return System.Logger.Level.DEBUG;
		}
		else if (INFO_LEVEL_THRESHOLD >= lv) {
			return System.Logger.Level.INFO;
		}
		else if (WARN_LEVEL_THRESHOLD >= lv) {
			return System.Logger.Level.WARNING;
		}
		else if (Integer.MAX_VALUE == lv) {
			return System.Logger.Level.OFF;
		}
		return System.Logger.Level.ERROR;
	}

	/**
	 * Whether a logger name at a given JUL level is currently enabled, per Rainbow Gum's
	 * own level resolution rather than anything cached on a JUL
	 * {@code Logger}/{@code Handler}.
	 * @param loggerName logger name, as JUL would pass to
	 * {@link java.util.logging.Logger#getName()}.
	 * @param level JUL level being checked.
	 * @return true if a record at this name/level would actually be routed, and false
	 * when asked from inside Rainbow Gum while this thread routes or checks a record.
	 */
	public static boolean isLoggable(String loggerName, java.util.logging.Level level) {
		if (ACTIVE.get() != null) {
			return false;
		}
		ACTIVE.set(Boolean.TRUE);
		try {
			return LogRouter.global().route(loggerName, toLevel(level)).isEnabled();
		}
		finally {
			ACTIVE.remove();
		}
	}

	/**
	 * Translates a JUL {@link LogRecord} into a Rainbow Gum event and routes it, if the
	 * record's logger name/level is currently enabled. A {@code null} record or a record
	 * with a {@code null} logger name is silently ignored, matching
	 * {@link java.util.logging.Handler#publish(LogRecord)}'s own contract for
	 * {@code null}. A record published on a thread that is already routing one, from
	 * inside Rainbow Gum, is dropped.
	 * @param rec JUL log record, possibly {@code null}.
	 * @param messageFormatter formats the record's parameters, usually from
	 * {@link LogMessageFormatter#ofJUL(io.jstach.rainbowgum.LogProperties)}.
	 */
	public static void publish(@Nullable LogRecord rec, LogMessageFormatter messageFormatter) {
		if (rec == null) {
			return;
		}
		String loggerName = rec.getLoggerName();
		if (loggerName == null || ACTIVE.get() != null) {
			return;
		}
		ACTIVE.set(Boolean.TRUE);
		try {
			route(rec, loggerName, messageFormatter);
		}
		finally {
			ACTIVE.remove();
		}
	}

	private static void route(LogRecord rec, String loggerName, LogMessageFormatter messageFormatter) {
		var level = toLevel(rec.getLevel());
		var router = LogRouter.global();
		var route = router.route(loggerName, level);
		if (!route.isEnabled()) {
			return;
		}
		@Nullable Throwable cause = rec.getThrown();
		@Nullable String msg = getMessage(rec);
		var args = rec.getParameters();

		Instant timestamp = rec.getInstant();
		long threadId = rec.getLongThreadID();
		String threadName = "";
		/*
		 * Ambient key values are only meaningful on the thread that made the record. A
		 * handler publishing on another thread (async) would otherwise attach the
		 * publishing thread's context to someone else's record.
		 */
		KeyValues keyValues = KeyValues.of();
		long currentThreadId = Thread.currentThread().threadId();
		if (currentThreadId == threadId) {
			threadName = Thread.currentThread().getName();
			keyValues = KEY_VALUES.keyValues();
		}
		// TODO fix caller info
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, msg, keyValues, cause,
				messageFormatter, args);
		route.log(event);
	}

	private static @Nullable String getMessage(LogRecord record) {
		String message = record.getMessage();
		if (message == null) {
			return null;
		}
		ResourceBundle bundle = record.getResourceBundle();
		if (bundle != null) {
			try {
				return bundle.getString(message);
			}
			catch (MissingResourceException e) {
				// no translation found - fall through and use the raw message instead
			}
		}
		return message;
	}

}
