package io.jstach.rainbowgum;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Logging about logging. This is the static, always-available entry point used by code
 * that cannot easily reach a {@link LogConfig} (e.g. before a {@link RainbowGum} is
 * bound), and always reports directly (currently to stderr) rather than forwarding to a
 * bound {@link RainbowGum}'s {@link LogConfig#alerts()} - alerts is a small bounded ring
 * buffer meant to hold a curated, recent view of noteworthy logging-system problems, and
 * {@link MetaLog} is used from enough different low level failure paths that routing it
 * there risked flooding/evicting genuine alerts with whatever volume of things end up
 * calling this class.
 * <p>
 * Components that already have (or can easily capture) a {@link LogConfig} - for example
 * via {@link LogProvider} or {@link LogLifecycle#start(LogConfig)} - should prefer
 * {@link LogConfig#alerts()} directly instead of this class.
 *
 * @author agentgt
 */
final class MetaLog {

	private MetaLog() {
	}

	/**
	 * Logs an error in the logging system.
	 * @param event event to log.
	 */
	static void error(LogEvent event) {
		FailsafeAppender.INSTANCE.log(event);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param throwable error to log.
	 */
	static void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param message error message.
	 * @param throwable error to log.
	 */
	static void error(Class<?> loggerName, String message, Throwable throwable) {
		var currentThread = Thread.currentThread();
		var event = LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), Level.ERROR,
				loggerName.getName(), message, KeyValues.of(), throwable);
		error(event);
	}

	static Supplier<? extends @Nullable PrintStream> output = () -> System.err;

}

enum FailsafeAppender implements LogEventLogger {

	INSTANCE;

	@Override
	public void log(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			var err = MetaLog.output.get();
			if (err != null) {
				err.append("[ERROR] - RAINBOW_GUM ");
				StringBuilder sb = new StringBuilder();
				event.formattedMessage(sb);
				err.append(sb.toString());

				var throwable = event.throwableOrNull();
				if (throwable != null) {
					err.append(" ");
					throwable.printStackTrace(err);
				}
			}
		}
	}

}
