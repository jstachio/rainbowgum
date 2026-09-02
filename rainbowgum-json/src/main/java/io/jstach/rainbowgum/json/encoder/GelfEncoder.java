package io.jstach.rainbowgum.json.encoder;

import static io.jstach.rainbowgum.json.JsonBuffer.EXTENDED_F;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;
import io.jstach.rainbowgum.json.JsonBuffer.JSONToken;

/**
 * A JSON encoder in
 * <a href="https://go2docs.graylog.org/5-2/getting_in_log_data/gelf.html">GELF JSON
 * format</a>.
 */
public final class GelfEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	/**
	 * GELF encoder URI scheme.
	 */
	public static final String GELF_SCHEME = "gelf";

	private final String host;

	private final KeyValues headers;

	private final boolean prettyprint;

	/**
	 * Default number of fractional second digits the <code>_time</code> field is rendered
	 * with, see {@link #of(String, String, Map, Boolean, Integer, Integer)}.
	 */
	public static final int DEFAULT_TIME_FRACTIONAL_DIGITS = 3;

	private final TimestampFormatter timeFormatter;

	private final int maxBufferSize;

	GelfEncoder(String host, KeyValues headers, boolean prettyprint, int timeFractionalDigits, int maxBufferSize) {
		super();
		this.host = host;
		this.headers = headers;
		this.prettyprint = prettyprint;
		this.timeFormatter = timeFormatter(timeFractionalDigits);
		this.maxBufferSize = maxBufferSize;
	}

	/*
	 * GELF's own "timestamp" field (a UNIX epoch double, see doEncode) is always
	 * millisecond precision by construction (derived from Instant.toEpochMilli()), so it
	 * needs no formatter and is unaffected by this. "_time" is a RainbowGum-only extra
	 * field giving a human-readable ISO-8601 instant alongside it - unlike "timestamp",
	 * Instant.now() on most JVMs has real nanosecond resolution, so formatting it with
	 * DateTimeFormatter.ISO_INSTANT (variable-width, 0-9 fractional digits depending on
	 * the actual instant) is both needlessly precise for a log timestamp and, because a
	 * fresh format() call happens on every single event, needlessly expensive. Fixing the
	 * width to timeFractionalDigits makes the output caching-safe the same way
	 * DefaultInstantFormatter.ISO's fix did (see LogFormatter/PatternFormatterFactory) -
	 * caching only kicks in at millisecond-or-coarser precision (<=3 digits), since finer
	 * precision genuinely varies within the same millisecond.
	 */
	private static TimestampFormatter timeFormatter(int fractionalDigits) {
		if (fractionalDigits < 0 || fractionalDigits > 9) {
			throw new IllegalArgumentException(
					"timeFractionalDigits must be between 0 and 9 inclusive: " + fractionalDigits);
		}
		var dtf = new DateTimeFormatterBuilder().appendInstant(fractionalDigits).toFormatter();
		return TimestampFormatter.of(dtf, fractionalDigits <= 3);
	}

	/**
	 * Creates a GELF encoder using a lambda for easier registration. The builder will
	 * have properties loaded after the consumer has configured the builder.
	 * @param consumer lambda to configure builder.
	 * @return GELF encoder provider.
	 */
	public static LogProvider<GelfEncoder> of(Consumer<GelfEncoderBuilder> consumer) {
		return (s, c) -> {
			var b = new GelfEncoderBuilder(s);
			consumer.accept(b);
			return b.fromProperties(c.properties()).build();
		};
	}

	/**
	 * Creates GELF Encoder.
	 * @param name property name prefix.
	 * @param host host field in GELF.
	 * @param headers additional headers that will be prefix with "_".
	 * @param prettyPrint <code>true</code> will pretty print the JSON, default is false.
	 * @param timeFractionalDigits number of fractional second digits (0-9) the
	 * non-standard <code>_time</code> field is rendered with, default
	 * {@value #DEFAULT_TIME_FRACTIONAL_DIGITS} (milliseconds). Values above 3 render
	 * genuine sub-millisecond precision (most JVMs' {@link Instant#now()} has real
	 * nanosecond resolution) but cannot be cached the way millisecond-or-coarser
	 * precision can, since it can legitimately differ between two events in the same
	 * millisecond.
	 * @param maxBufferSize maximum buffer size - a soft ceiling checked between events,
	 * not a hard cap enforced on any single event (see
	 * {@link LogEncoder.Buffer#isOversized()}). A negative value (the default) disables
	 * this entirely.
	 * @return encoder.
	 */
	@LogConfigurable(prefix = LogProperties.ENCODER_PREFIX)
	static GelfEncoder of(@LogConfigurable.KeyParameter String name, String host, //
			@Nullable Map<String, String> headers, //
			@Nullable Boolean prettyPrint, @Nullable Integer timeFractionalDigits, @Nullable Integer maxBufferSize) {
		prettyPrint = prettyPrint == null ? false : prettyPrint;
		host = Objects.requireNonNull(host);
		var _headers = KeyValues.of(headers == null ? Map.of() : headers);
		int _timeFractionalDigits = timeFractionalDigits == null ? DEFAULT_TIME_FRACTIONAL_DIGITS
				: timeFractionalDigits;
		int _maxBufferSize = maxBufferSize == null ? -1 : maxBufferSize;

		return new GelfEncoder(host, _headers, prettyPrint, _timeFractionalDigits, _maxBufferSize);
	}

	@Override
	protected JsonBuffer doBuffer(BufferHints hints) {
		return new JsonBuffer(this.prettyprint, ExtendedFieldPrefix.UNDERSCORE, maxBufferSize);
	}

	@Override
	protected void doEncode(LogEvent event, JsonBuffer buffer) {
		buffer.clear();
		var formattedMessage = buffer.getFormattedMessageBuilder();
		final String host = this.host;
		event.formattedMessage(formattedMessage);
		final String shortMessage = formattedMessage.toString();
		Instant now = event.timestamp();
		final double timeStamp = ((double) now.toEpochMilli()) / 1000;
		@Nullable
		String fullMessage = null;
		var t = event.throwableOrNull();
		if (t != null) {
			formattedMessage.append("\n");
			ThrowableFormatter.appendThrowable(formattedMessage, t);
			fullMessage = formattedMessage.toString();
		}
		int level = levelToSyslogLevel(event.level());
		buffer.write(JSONToken.OBJECT_START);
		int index = 0;
		index = buffer.write("host", host, index);
		index = buffer.write("short_message", shortMessage, index);
		index = buffer.write("full_message", fullMessage, index);
		index = buffer.writeDouble("timestamp", timeStamp, index, 0);
		index = buffer.writeInt("level", level, index, 0);
		StringBuilder timeBuilder = new StringBuilder(32);
		timeFormatter.formatTimestamp(timeBuilder, now);
		index = buffer.write("_time", timeBuilder.toString(), index);
		index = buffer.write("_level", LevelFormatter.toString(event.level()), index);
		index = buffer.write("_logger", event.loggerName(), index);
		index = buffer.write("_thread_name", event.threadName(), index);
		index = buffer.write("_thread_id", String.valueOf(event.threadId()), index);

		if (t != null) {
			String tn = t.getClass().getName();
			index = buffer.write("_throwable", tn, index);
		}

		var kvs = event.keyValues();

		/*
		 * output headers
		 */
		for (int i = headers.start(); i >= 0; i = headers.next(i)) {
			String k = headers.key(i);
			if (kvs.getValueOrNull(k) != null) {
				continue;
			}
			String v = headers.valueOrNull(i);
			index = buffer.write(k, v, index, EXTENDED_F);
		}

		/*
		 * output MDC
		 */
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			String k = kvs.key(i);
			String v = kvs.valueOrNull(i);
			index = buffer.write(k, v, index, EXTENDED_F);
		}

		index = buffer.write("version", "1.1", index);

		if (index > 0 && prettyprint) {
			buffer.writeLineFeed();
		}
		buffer.write(JSONToken.OBJECT_END);
		buffer.writeLineFeed();
	}

	private int levelToSyslogLevel(Level level) {
		/*
		 * FROM LOGBACK The syslog severity of a logging event is converted from the level
		 * of the logging event. The DEBUG level is converted to 7, INFO is converted to
		 * 6, WARN is converted to 4 and ERROR is converted to 3.
		 */
		int r = switch (level) {
			case ERROR -> 3;
			case DEBUG -> 7;
			case INFO -> 6;
			case TRACE -> 7;
			case WARNING -> 4;
			case ALL -> 7;
			case OFF -> 0;
		};
		return r;
	}

}
