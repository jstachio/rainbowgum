package io.jstach.rainbowgum.jul;

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

/**
 * JUL logger that uses global router.
 */
final class SystemLoggerQueueJULHandler extends Handler {

	private final LogMessageFormatter messageFormatter;

	/**
	 * Do nothing constuctor
	 */
	public SystemLoggerQueueJULHandler() {
		this(StandardMessageFormatter.JUL);
	}

	SystemLoggerQueueJULHandler(LogMessageFormatter messageFormatter) {
		this.messageFormatter = messageFormatter;
	}

	@Override
	public void publish(@Nullable LogRecord rec) {
		JULBridge.publish(rec, messageFormatter);
	}

	@Override
	public void flush() {

	}

	@Override
	public void close() {

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

	/*
	 * Installed with the same message formatter.
	 */
	static boolean isInstalled(LogMessageFormatter messageFormatter) {
		java.util.logging.Logger rootLogger = getRootLogger();
		Handler[] handlers = rootLogger.getHandlers();
		for (Handler handler : handlers) {
			if (handler instanceof SystemLoggerQueueJULHandler h && h.messageFormatter.equals(messageFormatter)) {
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
	 * @param messageFormatter formats the record's parameters.
	 */
	static void install(LogMessageFormatter messageFormatter) {
		removeHandlersForRootLogger();
		getRootLogger().addHandler(new SystemLoggerQueueJULHandler(messageFormatter));
	}

}
