package io.jstach.rainbowgum.jul;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogRouter;

/**
 * JUL logger that uses global router.
 */
public final class SystemLoggerQueueJULHandler extends Handler {

	private static final int TRACE_LEVEL_THRESHOLD = java.util.logging.Level.FINEST.intValue();

	private static final int DEBUG_LEVEL_THRESHOLD = java.util.logging.Level.FINE.intValue();

	private static final int INFO_LEVEL_THRESHOLD = java.util.logging.Level.INFO.intValue();

	private static final int WARN_LEVEL_THRESHOLD = java.util.logging.Level.WARNING.intValue();

	/**
	 * Do nothing constuctor
	 */
	public SystemLoggerQueueJULHandler() {
	}

	@Override
	public void publish(@SuppressWarnings("exports") @Nullable LogRecord rec) {
		if (rec == null) {
			return;
		}

		int lv = rec.getLevel().intValue();
		final Level level;
		if (TRACE_LEVEL_THRESHOLD >= lv) {
			level = Level.TRACE;
		}
		else if (DEBUG_LEVEL_THRESHOLD >= lv) {
			level = Level.DEBUG;
		}
		else if (INFO_LEVEL_THRESHOLD >= lv) {
			level = Level.INFO;
		}
		else if (WARN_LEVEL_THRESHOLD >= lv) {
			level = Level.WARNING;
		}
		else {
			level = Level.ERROR;
		}

		String loggerName = rec.getLoggerName();
		if (loggerName == null) {
			return;
		}
		@Nullable
		Throwable cause = rec.getThrown();
		var router = LogRouter.global();
		var route = router.route(loggerName, level);
		if (route.isEnabled()) {
			@Nullable
			String msg = alreadyFormattedOrNull(rec);
			Object[] args;
			if (msg != null) {
				args = null;
			}
			else {
				msg = Objects.requireNonNullElse(rec.getMessage(), "");
				args = rec.getParameters();
			}

			Instant timestamp = rec.getInstant();
			long threadId = rec.getLongThreadID();
			String threadName = "";
			long currentThreadId = Thread.currentThread().threadId();
			if (currentThreadId == threadId) {
				threadName = Thread.currentThread().getName();
			}
			var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, msg, KeyValues.of(), cause,
					StandardMessageFormatter.JUL, args);
			route.log(event);

		}
	}

	@Override
	public void flush() {

	}

	@Override
	public void close() {

	}

	private static @Nullable String alreadyFormattedOrNull(LogRecord record) {
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
			}
		}
		return null;
	}

	/**
	 * Checks to see if this handler is installed
	 * @return true if it is.
	 */
	public static boolean isInstalled() {
		java.util.logging.Logger rootLogger = getRootLogger();
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			if (handler instanceof SystemLoggerQueueJULHandler) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Call to remove all handlers.
	 */
	public static void removeHandlersForRootLogger() {
		java.util.logging.Logger rootLogger = getRootLogger();
		java.util.logging.Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			rootLogger.removeHandler(handler);
		}
	}

	private static java.util.logging.Logger getRootLogger() {
		var logger = LogManager.getLogManager().getLogger("");
		if (logger == null) {
			throw new IllegalStateException("log manager return null for root logger");
		}
		return logger;
	}

	/**
	 * Installs.
	 */
	public static void install() {
		removeHandlersForRootLogger();
		getRootLogger().addHandler(new SystemLoggerQueueJULHandler());
	}

}
