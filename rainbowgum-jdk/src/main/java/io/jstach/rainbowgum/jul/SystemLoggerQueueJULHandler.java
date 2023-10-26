package io.jstach.rainbowgum.jul;

import java.lang.System.Logger.Level;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter;

public class SystemLoggerQueueJULHandler extends Handler {

	private static final int TRACE_LEVEL_THRESHOLD = java.util.logging.Level.FINEST.intValue();

	private static final int DEBUG_LEVEL_THRESHOLD = java.util.logging.Level.FINE.intValue();

	private static final int INFO_LEVEL_THRESHOLD = java.util.logging.Level.INFO.intValue();

	private static final int WARN_LEVEL_THRESHOLD = java.util.logging.Level.WARNING.intValue();

	@Override
	public void publish(@Nullable LogRecord record) {
		if (record == null) {
			return;
		}

		int lv = record.getLevel().intValue();
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

		String loggerName = record.getLoggerName();
		if (loggerName == null) {
			return;
		}
		String message = record.getMessage();
		if (message == null) {
			message = "";
		}
		@Nullable
		Throwable cause = record.getThrown();

		LogRouter.global().log(loggerName, level, message, cause);
	}

	@Override
	public void flush() {

	}

	@Override
	public void close() throws SecurityException {

	}

	public static void removeHandlersForRootLogger() {
		java.util.logging.Logger rootLogger = getRootLogger();
		java.util.logging.Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			rootLogger.removeHandler(handler);
		}
	}

	@SuppressWarnings("null")
	private static java.util.logging.Logger getRootLogger() {
		return LogManager.getLogManager().getLogger("");
	}

	public static void install() {
		removeHandlersForRootLogger();
		LogManager.getLogManager().getLogger("").addHandler(new SystemLoggerQueueJULHandler());
	}

}
