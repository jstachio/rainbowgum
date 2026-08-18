package io.jstach.rainbowgum.jfr;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogOutput;

/**
 * A {@link LogOutput} that commits each log event as a JFR event (see
 * {@link RainbowGumLogEvent}) instead of writing bytes anywhere.
 * <p>
 * Because {@link LogOutput} is always paired with an
 * {@link io.jstach.rainbowgum.LogEncoder} by the appender machinery, some encoder still
 * has to run even though this output never looks at the encoded bytes - it reads the
 * fields it needs directly off the {@link LogEvent} passed alongside them. Pair this
 * output with the encoder registered under the same {@value #JFR_SCHEME} scheme (a
 * formatter with no formatters, which is a documented noop) to avoid paying for
 * formatting that is thrown away:
 *
 * <pre>{@code
 * logging.appender.myappender.output=jfr:///
 * logging.appender.myappender.encoder=jfr:///
 * }</pre>
 *
 * Per-event-type enablement, thresholds, and stack trace capture are controlled the same
 * way as any other JFR event, e.g. with {@code -XX:StartFlightRecording} or a
 * {@code .jfc} settings file - not through Rainbow Gum properties. If a Flight Recorder
 * session that enables the relevant event type is not running, {@link #write} does
 * (cheaply) nothing.
 */
public final class JfrLogOutput implements LogOutput {

	/**
	 * JFR output (and paired encoder) URI scheme.
	 */
	public static final String JFR_SCHEME = "jfr";

	private static final URI JFR_URI = URI.create(JFR_SCHEME + ":///");

	/**
	 * Creates a JFR output.
	 */
	public JfrLogOutput() {
	}

	@Override
	public URI uri() {
		return JFR_URI;
	}

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public OutputType type() {
		return OutputType.MEMORY;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		var jfrEvent = create(event.level());
		if (jfrEvent == null || !jfrEvent.isEnabled()) {
			return;
		}
		var message = new StringBuilder();
		event.formattedMessage(message);
		jfrEvent.message = message.toString();
		jfrEvent.logger = event.loggerName();
		var t = event.throwableOrNull();
		if (t != null) {
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			jfrEvent.throwable = stackTrace.toString();
		}
		jfrEvent.commit();
	}

	private static @Nullable RainbowGumLogEvent create(java.lang.System.Logger.Level level) {
		return switch (level) {
			case TRACE -> new RainbowGumLogEvent.TraceEvent();
			case DEBUG -> new RainbowGumLogEvent.DebugEvent();
			case INFO -> new RainbowGumLogEvent.InfoEvent();
			case WARNING -> new RainbowGumLogEvent.WarnEvent();
			case ERROR -> new RainbowGumLogEvent.ErrorEvent();
			case ALL, OFF -> null;
		};
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

}
