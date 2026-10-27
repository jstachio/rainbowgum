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
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder;
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
 * logging.appender.myappender.output.uri=jfr:///
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
 * logging.appender.myappender.output.uri=jfr:///var/log/myapp.jfr
 * }</pre>
 *
 * In this mode JFR's own per-event enablement/threshold filtering is bypassed entirely -
 * whether an event is captured is decided solely by Rainbow Gum's normal level resolver
 * (the same one that already decides whether {@link #write} is called at all), not by JFR
 * settings.
 * <p>
 * This output also implements {@link LogOutput.ProvidesEncoder}, supplying (as an
 * overridable default, see {@link LogOutput.ProvidesEncoder.Policy#DEFAULT}) an encoder
 * that renders the message followed by any key values, e.g. {@code some message -
 * {key=value}} - no timestamp/level/logger prefix, since the JFR event already carries
 * those as separate fields - so leaving {@code encoder} unset when {@code output=jfr:///}
 * picks this up automatically instead of the default TTLL encoder formatting a whole line
 * only to throw most of it away.
 */
public final class JfrLogOutput implements LogOutput, LogOutput.ProvidesEncoder {

	/**
	 * JFR output URI scheme.
	 */
	public static final String JFR_SCHEME = "jfr";

	private static final URI JFR_URI = URI.create(JFR_SCHEME + ":///");

	private static final LogFormatter KEY_VALUES_FORMATTER = new KeyValuesFormatterBuilder()
		.format(KeyValuesFormatterBuilder.Format.LOGFMT)
		.build();

	/**
	 * Renders the message followed by any key values in logfmt, e.g. {@code some message
	 * - {key=value other="a b"}}, or just the message when there are none. Shared with
	 * {@link JfrAlertListener}, which renders alerts the same way.
	 */
	static final LogFormatter MESSAGE_FORMATTER = LogFormatter.of((output, event) -> {
		event.formattedMessage(output);
		if (!event.keyValues().isEmpty()) {
			output.append(" - {");
			KEY_VALUES_FORMATTER.format(output, event);
			output.append('}');
		}
	});

	private final URI uri;

	private final @Nullable Path destination;

	private volatile @Nullable Recording recording;

	/**
	 * Creates a JFR output that emits events only for whatever externally started Flight
	 * Recorder session (if any) is currently running, without managing a recording of its
	 * own.
	 */
	public JfrLogOutput() {
		this(JFR_URI, null);
	}

	/**
	 * Creates a JFR output.
	 * @param uri the output's own configured URI, returned as-is by {@link #uri()}.
	 * @param destination if not null, a file this output starts and owns its own
	 * {@link Recording} against for as long as the output is open (see {@link #start},
	 * {@link #close}); if null, events are only captured by whatever externally started
	 * recording (if any) is already running.
	 */
	public JfrLogOutput(URI uri, @Nullable Path destination) {
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
		String path = uri.getRawPath();
		if (path == null || path.length() <= 1) {
			return null;
		}
		/* Through a file URI, since the decoded path on Windows is /C:/... */
		return Path.of(URI.create("file://" + path));
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
	public LogEncoder encoder(String name, LogConfig config) {
		return LogEncoder.of(MESSAGE_FORMATTER).provide(name, config);
	}

	@Override
	public void write(LogEvent event, String s) {
		switch (event.level()) {
			case TRACE -> emitTrace(event, s);
			case DEBUG -> emitDebug(event, s);
			case INFO -> emitInfo(event, s);
			case WARNING -> emitWarn(event, s);
			case ERROR -> emitError(event, s);
			case ALL, OFF -> {
			}
		}
	}

	/*
	 * One method per event type, each allocating its event and never returning it, so the
	 * JIT can eliminate the allocation when the event is disabled. A factory returning
	 * the event merges the five allocations into one value, which defeats escape
	 * analysis: measured 40 bytes and about 14 ns per call with no recording, against
	 * none and about 1.5 ns this way. The methods are here, not on RainbowGumLogEvent:
	 * verifying a method of the event base class that refers to its subclasses loads them
	 * while the base class is still being linked, and JFR fails to instrument an event
	 * class loaded then when a recording is already running (-XX:StartFlightRecording).
	 */
	private static void emitTrace(LogEvent event, String s) {
		var e = new RainbowGumLogEvent.TraceEvent();
		if (e.isEnabled()) {
			fill(e, event, s);
			e.commit();
		}
	}

	private static void emitDebug(LogEvent event, String s) {
		var e = new RainbowGumLogEvent.DebugEvent();
		if (e.isEnabled()) {
			fill(e, event, s);
			e.commit();
		}
	}

	private static void emitInfo(LogEvent event, String s) {
		var e = new RainbowGumLogEvent.InfoEvent();
		if (e.isEnabled()) {
			fill(e, event, s);
			e.commit();
		}
	}

	private static void emitWarn(LogEvent event, String s) {
		var e = new RainbowGumLogEvent.WarnEvent();
		if (e.isEnabled()) {
			fill(e, event, s);
			e.commit();
		}
	}

	private static void emitError(LogEvent event, String s) {
		var e = new RainbowGumLogEvent.ErrorEvent();
		if (e.isEnabled()) {
			fill(e, event, s);
			e.commit();
		}
	}

	private static void fill(RainbowGumLogEvent e, LogEvent event, String s) {
		e.message = s;
		e.logger = event.loggerName();
		e.throwable = stackTraceOrNull(event);
	}

	static @Nullable String stackTraceOrNull(LogEvent event) {
		var t = event.throwableOrNull();
		if (t == null) {
			return null;
		}
		var stackTrace = new StringBuilder();
		ThrowableFormatter.appendThrowable(stackTrace, t);
		return stackTrace.toString();
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		var charset = contentType.charsetOrNull();
		if (charset == null) {
			charset = StandardCharsets.UTF_8;
		}
		write(event, new String(bytes, off, len, charset));
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
