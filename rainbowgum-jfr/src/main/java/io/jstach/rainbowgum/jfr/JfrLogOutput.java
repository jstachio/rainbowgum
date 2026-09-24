package io.jstach.rainbowgum.jfr;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.WriteMethod;

/**
 * A {@link LogOutput} that commits each log event as a JFR event (see
 * {@link RainbowGumLogEvent}) instead of writing anywhere.
 * <p>
 * The output prefers {@link WriteMethod#STRING}, so formatter-backed encoders hand the
 * formatted message directly to {@link #write(LogEvent, String)} without first turning it
 * into bytes:
 *
 * <pre>{@code
 * logging.appender.myappender.output=jfr:///
 * }</pre>
 *
 * Per-event-type enablement, thresholds, and stack trace capture are controlled the same
 * way as any other JFR event, e.g. with {@code -XX:StartFlightRecording} or a
 * {@code .jfc} settings file - not through Rainbow Gum properties. If a Flight Recorder
 * session that enables the relevant event type is not running, {@link #write} does
 * (cheaply) nothing.
 * <p>
 * This output also implements {@link LogEncoder} itself, rendering just the message (no
 * timestamp/level/logger prefix - the JFR event already carries those as separate
 * fields), so leaving {@code encoder} unset when {@code output=jfr:///} picks this up
 * automatically instead of the default TTLL encoder formatting a whole line only to throw
 * most of it away.
 */
public final class JfrLogOutput implements LogOutput, LogEncoder {

	/**
	 * JFR output URI scheme.
	 */
	public static final String JFR_SCHEME = "jfr";

	private static final URI JFR_URI = URI.create(JFR_SCHEME + ":///");

	private static final LogFormatter KEY_VALUES_FORMATTER = LogFormatter.builder().encodedKeyValues().build();

	private final LogEncoder encoder;

	/**
	 * Creates a JFR output.
	 * @param encoder used only to render the message once per event (see
	 * {@link #write(LogEvent, String)}) - this output never looks at the resulting
	 * content type or bytes otherwise.
	 */
	public JfrLogOutput(LogEncoder encoder) {
		this.encoder = encoder;
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
	public BufferHints bufferHints() {
		return WriteMethod.STRING;
	}

	@Override
	public Buffer buffer(BufferHints hints) {
		return encoder.buffer(hints);
	}

	@Override
	public void encode(LogEvent event, Buffer buffer) {
		encoder.encode(event, buffer);
	}

	@Override
	public void write(LogEvent event, String s) {
		var jfrEvent = create(event.level());
		if (jfrEvent == null || !jfrEvent.isEnabled()) {
			return;
		}
		jfrEvent.message = s;
		jfrEvent.logger = event.loggerName();
		var keyValues = event.keyValues();
		if (!keyValues.isEmpty()) {
			var encodedKeyValues = new StringBuilder();
			KEY_VALUES_FORMATTER.format(encodedKeyValues, event);
			jfrEvent.keyValues = encodedKeyValues.toString();
		}
		var t = event.throwableOrNull();
		if (t != null) {
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			jfrEvent.throwable = stackTrace.toString();
		}
		jfrEvent.commit();
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		var charset = contentType.charsetOrNull();
		if (charset == null) {
			charset = StandardCharsets.UTF_8;
		}
		write(event, new String(bytes, off, len, charset));
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
