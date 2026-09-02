package io.jstach.rainbowgum;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Logging about logging. This is the static, always-available entry point used by code
 * that cannot easily reach a {@link LogConfig} (e.g. before a {@link RainbowGum} is
 * bound). Where a live {@link RainbowGum} is bound this simply forwards to its
 * {@link LogConfig#alerts()} so alerts still end up in that instance's ring buffer;
 * otherwise it falls back to the same direct stderr reporting it has always done.
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
	public static void error(LogEvent event) {
		var gum = RainbowGum.getOrNull();
		if (gum != null) {
			gum.config().alerts().error(event);
			return;
		}
		FailsafeAppender.INSTANCE.log(event);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param throwable error to log.
	 */
	public static void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param message error message.
	 * @param throwable error to log.
	 */
	public static void error(Class<?> loggerName, String message, Throwable throwable) {
		var event = LogEvent.of(Level.ERROR, loggerName.getName(), message, throwable);
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
