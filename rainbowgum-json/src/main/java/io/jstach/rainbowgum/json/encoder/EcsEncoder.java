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
 * Common Schema (ECS) logging format</a> as produced by
 * <a href="https://github.com/elastic/ecs-logging-java">ecs-logging-java</a>. Field names
 * are the flattened, dotted ECS field names (e.g. <code>"log.level"</code>) rather than
 * nested JSON objects, matching how the reference implementation serializes them.
 * <p>
 * MDC / key values have no reserved ECS field, so - like the reference implementation -
 * they are written as top-level fields using their own key name. This means a key value
 * whose name collides with a reserved ECS field name (e.g. <code>message</code>) will
 * overwrite it; that footgun exists in the reference implementation too.
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

	private final boolean prettyprint;

	private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_INSTANT;

	EcsEncoder(@Nullable String serviceName, @Nullable String serviceVersion, @Nullable String serviceEnvironment,
			@Nullable String serviceNodeName, @Nullable String eventDataset, boolean prettyprint) {
		super();
		this.serviceName = serviceName;
		this.serviceVersion = serviceVersion;
		this.serviceEnvironment = serviceEnvironment;
		this.serviceNodeName = serviceNodeName;
		this.eventDataset = eventDataset;
		this.prettyprint = prettyprint;
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
	 * @param prettyPrint <code>true</code> will pretty print the JSON, default is false.
	 * @return encoder.
	 */
	@LogConfigurable(prefix = LogProperties.ENCODER_PREFIX)
	static EcsEncoder of(@LogConfigurable.KeyParameter String name, @Nullable String serviceName,
			@Nullable String serviceVersion, @Nullable String serviceEnvironment, @Nullable String serviceNodeName,
			@Nullable String eventDataset, @Nullable Boolean prettyPrint) {
		prettyPrint = prettyPrint == null ? false : prettyPrint;
		return new EcsEncoder(serviceName, serviceVersion, serviceEnvironment, serviceNodeName, eventDataset,
				prettyPrint);
	}

	@Override
	protected JsonBuffer doBuffer(BufferHints hints) {
		return new JsonBuffer(this.prettyprint, ExtendedFieldPrefix.AT);
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

}
