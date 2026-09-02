package io.jstach.rainbowgum.json.encoder;

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
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;
import io.jstach.rainbowgum.json.JsonBuffer.JSONToken;

/**
 * A JSON encoder in
 * <a href="https://www.elastic.co/guide/en/ecs-logging/java/current/index.html">Elastic
 * Common Schema (ECS) logging format</a>.
 * <p>
 * Two shapes are supported, controlled by {@link #structured()}:
 * <ul>
 * <li><strong>Flattened (default)</strong>: field names are the flattened, dotted ECS
 * field names (e.g. <code>"log.level"</code>) rather than nested JSON objects, matching
 * how the reference <a href="https://github.com/elastic/ecs-logging-java">
 * ecs-logging-java</a> implementation serializes them.</li>
 * <li><strong>Structured</strong> ({@link EcsEncoderBuilder#structured(Boolean)
 * structured=true}): fields are nested JSON objects (e.g.
 * <code>"log":{"level":...}</code>), matching
 * <a href="https://docs.spring.io/spring-boot/reference/features/logging.html">Spring
 * Boot's ECS structured logging format</a>.</li>
 * </ul>
 * MDC / key values have no reserved ECS field in either shape, so - like both reference
 * implementations - they are written as top-level fields using their own key name. This
 * means a key value whose name collides with a reserved ECS field name (e.g.
 * <code>message</code>) will overwrite it; that footgun exists in the reference
 * implementations too.
 */
public final class EcsEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	/**
	 * ECS encoder URI scheme.
	 */
	public static final String ECS_SCHEME = "ecs";

	/**
	 * The ECS schema version this encoder targets.
	 */
	public static final String ECS_VERSION = "1.2.0";

	private final @Nullable String serviceName;

	private final @Nullable String serviceVersion;

	private final @Nullable String serviceEnvironment;

	private final @Nullable String serviceNodeName;

	private final @Nullable String eventDataset;

	private final boolean structured;

	private final boolean prettyprint;

	private final int maxBufferSize;

	private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_INSTANT;

	EcsEncoder(@Nullable String serviceName, @Nullable String serviceVersion, @Nullable String serviceEnvironment,
			@Nullable String serviceNodeName, @Nullable String eventDataset, boolean structured, boolean prettyprint,
			int maxBufferSize) {
		super();
		this.serviceName = serviceName;
		this.serviceVersion = serviceVersion;
		this.serviceEnvironment = serviceEnvironment;
		this.serviceNodeName = serviceNodeName;
		this.eventDataset = eventDataset;
		this.structured = structured;
		this.prettyprint = prettyprint;
		this.maxBufferSize = maxBufferSize;
	}

	/**
	 * Whether fields are nested JSON objects (Spring Boot's ECS shape) instead of the
	 * default flattened, dotted field names (the reference ecs-logging-java shape).
	 * @return true if fields are nested.
	 */
	public boolean structured() {
		return this.structured;
	}

	/**
	 * Creates an ECS encoder using a lambda for easier registration. The builder will
	 * have properties loaded after the consumer has configured the builder.
	 * @param consumer lambda to configure builder.
	 * @return ECS encoder provider.
	 */
	public static LogProvider<EcsEncoder> of(Consumer<EcsEncoderBuilder> consumer) {
		return (s, c) -> {
			var b = new EcsEncoderBuilder(s);
			consumer.accept(b);
			return b.fromProperties(c.properties()).build();
		};
	}

	/**
	 * Creates ECS Encoder.
	 * @param name property name prefix.
	 * @param serviceName <code>service.name</code> field, or null to omit.
	 * @param serviceVersion <code>service.version</code> field, or null to omit.
	 * @param serviceEnvironment <code>service.environment</code> field, or null to omit.
	 * @param serviceNodeName <code>service.node.name</code> field, or null to omit.
	 * @param eventDataset <code>event.dataset</code> field, or null to omit.
	 * @param structured <code>true</code> nests fields as JSON objects (Spring Boot's ECS
	 * shape) instead of flattened dotted field names, default is false.
	 * @param prettyPrint <code>true</code> will pretty print the JSON, default is false.
	 * @param maxBufferSize maximum buffer size - a soft ceiling checked between events,
	 * not a hard cap enforced on any single event (see
	 * {@link LogEncoder.Buffer#isOversized()}). A negative value (the default) disables
	 * this entirely.
	 * @return encoder.
	 */
	@LogConfigurable(prefix = LogProperties.ENCODER_PREFIX)
	static EcsEncoder of(@LogConfigurable.KeyParameter String name, @Nullable String serviceName,
			@Nullable String serviceVersion, @Nullable String serviceEnvironment, @Nullable String serviceNodeName,
			@Nullable String eventDataset, @Nullable Boolean structured, @Nullable Boolean prettyPrint,
			@Nullable Integer maxBufferSize) {
		prettyPrint = prettyPrint == null ? false : prettyPrint;
		structured = structured == null ? false : structured;
		int _maxBufferSize = maxBufferSize == null ? -1 : maxBufferSize;
		return new EcsEncoder(serviceName, serviceVersion, serviceEnvironment, serviceNodeName, eventDataset,
				structured, prettyPrint, _maxBufferSize);
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

		int index = structured ? encodeStructured(event, buffer, formattedMessage)
				: encodeFlattened(event, buffer, formattedMessage);

		if (index > 0 && prettyprint) {
			buffer.writeLineFeed();
		}
		buffer.write(JSONToken.OBJECT_END);
		buffer.writeLineFeed();
	}

	private int encodeFlattened(LogEvent event, JsonBuffer buffer, StringBuilder formattedMessage) {
		var now = event.timestamp();
		var t = event.throwableOrNull();

		buffer.write(JSONToken.OBJECT_START);
		int index = 0;
		index = buffer.write("@timestamp", timeFormatter.format(now), index);
		index = buffer.write("log.level", LevelFormatter.toString(event.level()), index);
		index = buffer.write("message", formattedMessage.toString(), index);
		index = buffer.write("ecs.version", ECS_VERSION, index);
		index = buffer.write("service.name", serviceName, index);
		index = buffer.write("service.version", serviceVersion, index);
		index = buffer.write("service.environment", serviceEnvironment, index);
		index = buffer.write("service.node.name", serviceNodeName, index);
		index = buffer.write("event.dataset", eventDataset, index);
		index = buffer.write("log.logger", event.loggerName(), index);
		index = buffer.write("process.thread.name", event.threadName(), index);

		if (t != null) {
			index = buffer.write("error.type", t.getClass().getName(), index);
			index = buffer.write("error.message", t.getMessage(), index);
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			index = buffer.write("error.stack_trace", stackTrace.toString(), index);
		}

		index = writeKeyValues(event, buffer, index);
		return index;
	}

	private int encodeStructured(LogEvent event, JsonBuffer buffer, StringBuilder formattedMessage) {
		var now = event.timestamp();
		var t = event.throwableOrNull();

		buffer.write(JSONToken.OBJECT_START);
		int index = 0;
		index = buffer.write("@timestamp", timeFormatter.format(now), index);

		int logIndex = buffer.writeObjectStart("log", index, 0);
		logIndex = buffer.write("level", LevelFormatter.toString(event.level()), logIndex, 0);
		logIndex = buffer.write("logger", event.loggerName(), logIndex, 0);
		buffer.writeObjectEnd();
		index++;

		index = buffer.write("message", formattedMessage.toString(), index);

		int ecsIndex = buffer.writeObjectStart("ecs", index, 0);
		buffer.write("version", ECS_VERSION, ecsIndex, 0);
		buffer.writeObjectEnd();
		index++;

		boolean hasService = serviceName != null || serviceVersion != null || serviceEnvironment != null
				|| serviceNodeName != null;
		if (hasService) {
			int serviceIndex = buffer.writeObjectStart("service", index, 0);
			serviceIndex = buffer.write("name", serviceName, serviceIndex, 0);
			serviceIndex = buffer.write("version", serviceVersion, serviceIndex, 0);
			serviceIndex = buffer.write("environment", serviceEnvironment, serviceIndex, 0);
			if (serviceNodeName != null) {
				int nodeIndex = buffer.writeObjectStart("node", serviceIndex, 0);
				buffer.write("name", serviceNodeName, nodeIndex, 0);
				buffer.writeObjectEnd();
			}
			buffer.writeObjectEnd();
			index++;
		}

		if (eventDataset != null) {
			int eventIndex = buffer.writeObjectStart("event", index, 0);
			buffer.write("dataset", eventDataset, eventIndex, 0);
			buffer.writeObjectEnd();
			index++;
		}

		int processIndex = buffer.writeObjectStart("process", index, 0);
		int threadIndex = buffer.writeObjectStart("thread", processIndex, 0);
		buffer.write("name", event.threadName(), threadIndex, 0);
		buffer.writeObjectEnd();
		buffer.writeObjectEnd();
		index++;

		if (t != null) {
			int errorIndex = buffer.writeObjectStart("error", index, 0);
			errorIndex = buffer.write("type", t.getClass().getName(), errorIndex, 0);
			errorIndex = buffer.write("message", t.getMessage(), errorIndex, 0);
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			buffer.write("stack_trace", stackTrace.toString(), errorIndex, 0);
			buffer.writeObjectEnd();
			index++;
		}

		index = writeKeyValues(event, buffer, index);
		return index;
	}

	private static int writeKeyValues(LogEvent event, JsonBuffer buffer, int index) {
		var kvs = event.keyValues();
		for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
			String k = kvs.key(i);
			String v = kvs.valueOrNull(i);
			index = buffer.write(k, v, index);
		}
		return index;
	}

}
