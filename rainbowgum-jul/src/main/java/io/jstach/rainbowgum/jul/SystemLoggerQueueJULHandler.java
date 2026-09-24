package io.jstach.rainbowgum.jul;

import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.jspecify.annotations.Nullable;

/**
 * JUL logger that uses global router.
 */
final class SystemLoggerQueueJULHandler extends Handler {

	/**
	 * Do nothing constuctor
	 */
	public SystemLoggerQueueJULHandler() {
	}

	@Override
	public void publish(@Nullable LogRecord rec) {
		JULBridge.publish(rec);
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
