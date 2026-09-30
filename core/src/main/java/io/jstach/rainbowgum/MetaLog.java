package io.jstach.rainbowgum;

import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

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
	 * Reports a logging system event at its original level.
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

@SuppressWarnings("ImmutableEnumChecker")
enum FailsafeAppender implements LogEventLogger {

	INSTANCE;

	private final LogFormatter formatter = LogFormatter.builder() //
		.text("[")
		.level()
		.text("] - RAINBOW_GUM")
		.text(" - ")
		.add(LogFormatter.of((b, e) -> {
			b.append(gumLoggerName(e.loggerName()));
		}))
		.text(" - ")
		.message()
		.textIfThrowable(" ")
		.throwable()
		.newline()
		.build();

	private static String gumLoggerName(String value) {
		String prefix = "io.jstach.rainbowgum.";
		if (value.startsWith(prefix)) {
			value = value.substring(prefix.length());
		}
		return value;
	}

	@Override
	public void log(LogEvent event) {
		var err = MetaLog.output.get();
		if (err != null) {
			var sb = new StringBuilder();
			formatter.format(sb, event);
			err.append(sb);
		}
	}

}
