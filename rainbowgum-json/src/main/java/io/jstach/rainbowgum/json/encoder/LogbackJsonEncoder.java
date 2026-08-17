package io.jstach.rainbowgum.json.encoder;

import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;
import io.jstach.rainbowgum.json.JsonBuffer.JSONToken;

/**
 * A JSON encoder resembling
 * <a href="https://logback.qos.ch/manual/encoders.html#JsonEncoder">Logback's own
 * opinionated <code>ch.qos.logback.classic.encoder.JsonEncoder</code></a>.
 * <p>
 * Fields written: <code>timestamp</code> (epoch millis), <code>nanoseconds</code>
 * (nanosecond-of-second, for sub-millisecond precision), <code>level</code>,
 * <code>threadName</code>, <code>loggerName</code>, <code>mdc</code> (nested object of
 * the event's key values), <code>message</code> (already formatted), and
 * <code>throwable</code> (nested object with <code>className</code>, <code>message</code>
 * and a <code>stepArray</code> of stack frame objects, recursing into <code>cause</code>
 * if present).
 * <p>
 * Unlike Logback's encoder, which is independently configurable per field via
 * {@code <withXxx>} elements, <code>sequenceNumber</code> is intentionally not
 * implemented (Rainbow Gum has no context-wide sequence number generator, see
 * {@code %lsn}/{@code %sn} in the pattern module discussion), <code>context</code> is
 * omitted (Rainbow Gum has no equivalent to Logback's {@code LoggerContext}), and
 * <code>kvpList</code> is omitted because Rainbow Gum does not distinguish MDC-origin key
 * values from SLF4J fluent {@code addKeyValue} ones - both already end up merged into
 * {@link LogEvent#keyValues()}, which this encoder writes as <code>mdc</code>.
 */
public final class LogbackJsonEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	/**
	 * Logback JSON encoder URI scheme.
	 */
	public static final String LOGBACK_SCHEME = "logback";

	private final boolean prettyprint;

	LogbackJsonEncoder(boolean prettyprint) {
		super();
		this.prettyprint = prettyprint;
	}

	/**
	 * Creates a Logback JSON encoder using a lambda for easier registration. The builder
	 * will have properties loaded after the consumer has configured the builder.
	 * @param consumer lambda to configure builder.
	 * @return encoder provider.
	 */
	public static LogProvider<LogbackJsonEncoder> of(Consumer<LogbackJsonEncoderBuilder> consumer) {
		return (s, c) -> {
			var b = new LogbackJsonEncoderBuilder(s);
			consumer.accept(b);
			return b.fromProperties(c.properties()).build();
		};
	}

	/**
	 * Creates a Logback JSON encoder.
	 * @param name property name prefix.
	 * @param prettyPrint <code>true</code> will pretty print the JSON, default is false.
	 * @return encoder.
	 */
	@LogConfigurable(prefix = LogProperties.ENCODER_PREFIX)
	static LogbackJsonEncoder of(@LogConfigurable.KeyParameter String name, @Nullable Boolean prettyPrint) {
		prettyPrint = prettyPrint == null ? false : prettyPrint;
		return new LogbackJsonEncoder(prettyPrint);
	}

	@Override
	protected JsonBuffer doBuffer(BufferHints hints) {
		return new JsonBuffer(this.prettyprint, ExtendedFieldPrefix.UNDERSCORE);
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
		index = buffer.writeLong("timestamp", now.toEpochMilli(), index, 0);
		index = buffer.writeInt("nanoseconds", now.getNano(), index, 0);
		index = buffer.write("level", LevelFormatter.toString(event.level()), index);
		index = buffer.write("threadName", event.threadName(), index);
		index = buffer.write("loggerName", event.loggerName(), index);

		int mdcIndex = buffer.writeObjectStart("mdc", index, 0);
		var kvs = event.keyValues();
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			mdcIndex = buffer.write(kvs.key(i), kvs.valueOrNull(i), mdcIndex, 0);
		}
		buffer.writeObjectEnd();
		index++;

		index = buffer.write("message", formattedMessage.toString(), index);

		if (t != null) {
			index = writeThrowable("throwable", t, buffer, index);
		}

		if (index > 0 && prettyprint) {
			buffer.writeLineFeed();
		}
		buffer.write(JSONToken.OBJECT_END);
		buffer.writeLineFeed();
	}

	private static int writeThrowable(String field, Throwable t, JsonBuffer buffer, int index) {
		int throwableIndex = buffer.writeObjectStart(field, index, 0);
		throwableIndex = buffer.write("className", t.getClass().getName(), throwableIndex, 0);
		throwableIndex = buffer.write("message", t.getMessage(), throwableIndex, 0);

		int arrayIndex = buffer.writeArrayStart("stepArray", throwableIndex, 0);
		for (var frame : t.getStackTrace()) {
			int frameIndex = buffer.writeArrayElementObjectStart(arrayIndex);
			frameIndex = buffer.write("className", frame.getClassName(), frameIndex, 0);
			frameIndex = buffer.write("methodName", frame.getMethodName(), frameIndex, 0);
			frameIndex = buffer.write("fileName", frame.getFileName(), frameIndex, 0);
			frameIndex = buffer.writeInt("lineNumber", frame.getLineNumber(), frameIndex, 0);
			buffer.writeArrayElementObjectEnd();
			arrayIndex++;
		}
		buffer.writeArrayEnd();
		throwableIndex++;

		var cause = t.getCause();
		if (cause != null && cause != t) {
			writeThrowable("cause", cause, buffer, throwableIndex);
		}

		buffer.writeObjectEnd();
		return index + 1;
	}

}
