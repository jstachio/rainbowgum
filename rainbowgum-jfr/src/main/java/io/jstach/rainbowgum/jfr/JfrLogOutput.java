package io.jstach.rainbowgum.jfr;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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

import jdk.jfr.Recording;

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
 * If the configured URI has a real path (anything past the root {@code /}), this output
 * instead starts and owns its own {@link Recording} writing directly to that file for as
 * long as the output is open, with every event type unconditionally enabled:
 *
 * <pre>{@code
 * logging.appender.myappender.output=jfr:///var/log/myapp.jfr
 * }</pre>
 *
 * In this mode JFR's own per-event enablement/threshold filtering is bypassed entirely -
 * whether an event is captured is decided solely by Rainbow Gum's normal level resolver
 * (the same one that already decides whether {@link #write} is called at all), not by JFR
 * settings.
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

	private final URI uri;

	private final @Nullable Path destination;

	private volatile @Nullable Recording recording;

	/**
	 * Creates a JFR output that emits events only for whatever externally started Flight
	 * Recorder session (if any) is currently running, without managing a recording of its
	 * own.
	 * @param encoder used only to render the message once per event (see
	 * {@link #write(LogEvent, String)}) - this output never looks at the resulting
	 * content type or bytes otherwise.
	 */
	public JfrLogOutput(LogEncoder encoder) {
		this(encoder, JFR_URI, null);
	}

	/**
	 * Creates a JFR output.
	 * @param encoder used only to render the message once per event (see
	 * {@link #write(LogEvent, String)}) - this output never looks at the resulting
	 * content type or bytes otherwise.
	 * @param uri the output's own configured URI, returned as-is by {@link #uri()}.
	 * @param destination if not null, a file this output starts and owns its own
	 * {@link Recording} against for as long as the output is open (see {@link #start},
	 * {@link #close}); if null, events are only captured by whatever externally started
	 * recording (if any) is already running.
	 */
	public JfrLogOutput(LogEncoder encoder, URI uri, @Nullable Path destination) {
		this.encoder = encoder;
		this.uri = uri;
		this.destination = destination;
	}

	/**
	 * Extracts a recording destination file from a configured output URI, if any. The
	 * default {@code jfr:///} URI has path {@code "/"} (root, no real path segment),
	 * which must not be mistaken for a configured destination file.
	 * @param uri configured output URI.
	 * @return destination file, or null if {@code uri} has no real path.
	 */
	static @Nullable Path destinationOrNull(URI uri) {
		String path = uri.getPath();
		if (path == null || path.length() <= 1) {
			return null;
		}
		return Path.of(path);
	}

	@Override
	public URI uri() {
		return uri;
	}

	@Override
	public void start(LogConfig config) {
		var path = destination;
		if (path == null) {
			return;
		}
		var r = new Recording();
		r.enable(RainbowGumLogEvent.TraceEvent.class);
		r.enable(RainbowGumLogEvent.DebugEvent.class);
		r.enable(RainbowGumLogEvent.InfoEvent.class);
		r.enable(RainbowGumLogEvent.WarnEvent.class);
		r.enable(RainbowGumLogEvent.ErrorEvent.class);
		try {
			r.setDestination(path);
		}
		catch (IOException e) {
			r.close();
			throw new UncheckedIOException(e);
		}
		r.start();
		this.recording = r;
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
		var r = recording;
		if (r != null) {
			r.close();
		}
	}

}
