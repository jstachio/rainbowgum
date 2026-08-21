package io.jstach.rainbowgum;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.RainbowGumVersion;

/**
 * Logging about logging. Currently not really public API.
 *
 * @author agentgt
 * @hidden
 */
@SuppressWarnings("InvalidBlockTag")
public final class MetaLog {

	private MetaLog() {
	}

	/**
	 * Logs an error in the logging system.
	 * @param event event to log.
	 */
	public static void error(LogEvent event) {
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

	/**
	 * Resolves the Rainbow Gum documentation URL based on static version information.
	 * @return URL <strong>with no trailing slash!</strong>
	 */
	public static String documentBaseUrl() {
		String version = RainbowGumVersion.VERSION;
		if (version.endsWith("-SNAPSHOT")) {
			return "https://jstach.io/rainbowgum";
		}
		return "https://jstach.io/doc/rainbowgum/" + version + "/apidocs";

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