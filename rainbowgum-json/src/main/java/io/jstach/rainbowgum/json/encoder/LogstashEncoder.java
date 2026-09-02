package io.jstach.rainbowgum.json.encoder;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;
import io.jstach.rainbowgum.json.JsonBuffer.JSONToken;

/**
 * A JSON encoder in the format produced by
 * <a href="https://github.com/logfellow/logstash-logback-encoder">
 * logstash-logback-encoder</a>'s <code>LogstashEncoder</code>.
 * <p>
 * Fields written: <code>@timestamp</code> (<code>ISO_OFFSET_DATE_TIME</code>, using
 * {@link #zoneId()} - defaulting to the system default zone, matching the reference
 * implementation), <code>@version</code> (constant <code>"1"</code>),
 * <code>message</code>, <code>logger_name</code>, <code>thread_name</code>,
 * <code>level</code>, <code>level_value</code> (Logback's conventional level integers:
 * TRACE=5000, DEBUG=10000, INFO=20000, WARN=30000, ERROR=40000), and - only if a
 * throwable was logged - <code>stack_trace</code> (the full stack trace as a single
 * string).
 * <p>
 * <code>tags</code> (from SLF4J markers) is not implemented since Rainbow Gum core has no
 * built-in {@code org.slf4j.Marker} support. MDC / key values have no reserved field so,
 * matching the reference implementation, they are written as top-level fields using their
 * own key name.
 */
public final class LogstashEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	/**
	 * Logstash encoder URI scheme.
	 */
	public static final String LOGSTASH_SCHEME = "logstash";

	private final ZoneId zoneId;

	private final boolean prettyprint;

	private final DateTimeFormatter timeFormatter;

	private final int maxBufferSize;

	LogstashEncoder(ZoneId zoneId, boolean prettyprint, int maxBufferSize) {
		super();
		this.zoneId = zoneId;
		this.prettyprint = prettyprint;
		this.timeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(zoneId);
		this.maxBufferSize = maxBufferSize;
	}

	/**
	 * The zone used to render <code>@timestamp</code>.
	 * @return zone id.
	 */
	public ZoneId zoneId() {
		return this.zoneId;
	}

	/**
	 * Creates a Logstash encoder using a lambda for easier registration. The builder will
	 * have properties loaded after the consumer has configured the builder.
	 * @param consumer lambda to configure builder.
	 * @return encoder provider.
	 */
	public static LogProvider<LogstashEncoder> of(Consumer<LogstashEncoderBuilder> consumer) {
		return (s, c) -> {
			var b = new LogstashEncoderBuilder(s);
			consumer.accept(b);
			return b.fromProperties(c.properties()).build();
		};
	}

	/**
	 * Creates a Logstash encoder.
	 * @param name property name prefix.
	 * @param zoneId zone used for <code>@timestamp</code>, defaults to the system default
	 * zone.
	 * @param prettyPrint <code>true</code> will pretty print the JSON, default is false.
	 * @param maxBufferSize maximum buffer size - a soft ceiling checked between events,
	 * not a hard cap enforced on any single event (see
	 * {@link LogEncoder.Buffer#isOversized()}). A negative value (the default) disables
	 * this entirely.
	 * @return encoder.
	 */
	@LogConfigurable(prefix = LogProperties.ENCODER_PREFIX)
	static LogstashEncoder of(@LogConfigurable.KeyParameter String name,
			@ConvertParameter("convertZoneId") @Nullable ZoneId zoneId, @Nullable Boolean prettyPrint,
			@Nullable Integer maxBufferSize) {
		prettyPrint = prettyPrint == null ? false : prettyPrint;
		zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
		int _maxBufferSize = maxBufferSize == null ? -1 : maxBufferSize;
		return new LogstashEncoder(zoneId, prettyPrint, _maxBufferSize);
	}

	static ZoneId convertZoneId(@Nullable String zoneId) {
		return zoneId == null ? ZoneId.systemDefault() : ZoneId.of(zoneId);
	}

	@Override
	protected JsonBuffer doBuffer(BufferHints hints) {
		return new JsonBuffer(this.prettyprint, ExtendedFieldPrefix.AT, maxBufferSize);
	}

	@Override
	protected void doEncode(LogEvent event, JsonBuffer buffer) {
		buffer.clear();
		var formattedMessage = buffer.getFormattedMessageBuilder();
		event.formattedMessage(formattedMessage);
		var now = event.timestamp();
		var t = event.throwableOrNull();

		buffer.write(JSONToken.OBJECT_START);
		int index = 0;
		index = buffer.write("@timestamp", timeFormatter.format(now), index);
		index = buffer.write("@version", "1", index);
		index = buffer.write("message", formattedMessage.toString(), index);
		index = buffer.write("logger_name", event.loggerName(), index);
		index = buffer.write("thread_name", event.threadName(), index);
		index = buffer.write("level", LevelFormatter.toString(event.level()), index);
		index = buffer.writeInt("level_value", levelValue(event.level()), index, 0);

		if (t != null) {
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			index = buffer.write("stack_trace", stackTrace.toString(), index);
		}

		var kvs = event.keyValues();
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			String k = kvs.key(i);
			String v = kvs.valueOrNull(i);
			index = buffer.write(k, v, index);
		}

		if (index > 0 && prettyprint) {
			buffer.writeLineFeed();
		}
		buffer.write(JSONToken.OBJECT_END);
		buffer.writeLineFeed();
	}

	private static int levelValue(java.lang.System.Logger.Level level) {
		/*
		 * Logback's conventional level integers, which is what logstash-logback-encoder
		 * writes as level_value.
		 */
		return switch (level) {
			case TRACE -> 5000;
			case DEBUG -> 10000;
			case INFO -> 20000;
			case WARNING -> 30000;
			case ERROR -> 40000;
			case ALL -> Integer.MIN_VALUE;
			case OFF -> Integer.MAX_VALUE;
		};
	}

}
