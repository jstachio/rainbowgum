package io.jstach.rainbowgum;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.TTLL;
import io.jstach.rainbowgum.format.Json5FormatterBuilder;
import io.jstach.rainbowgum.format.JsonFormatterBuilder;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder.Format;
import io.jstach.rainbowgum.format.LogfmtFormatterBuilder;
import io.jstach.rainbowgum.format.TTLLFormatterBuilder;

/**
 * Encoder registry. Only one encoder is registered by default which has the URI schema of
 * {@value TTLL#SCHEMA} and is used if no encoder is found for the {@link OutputType} of
 * the resolved output.
 */
public sealed interface LogEncoderRegistry extends EncoderProvider {

	/**
	 * Registers an encoder by uri scheme.
	 * @param scheme encoder name.
	 * @param encoder loaded encoder
	 */
	public void register(String scheme, EncoderProvider encoder);

	/**
	 * Associates a default formatter with a specific output type.
	 * @param outputType output type to use for finding best default formatter.
	 * @return formatter for output type.
	 */
	public LogProvider<? extends LogEncoder> encoderForOutputType(OutputType outputType);

	/**
	 * Sets a default formatter for a specific output type.
	 * @param outputType output type.
	 * @param formatter formatter.
	 */
	public void setEncoderForOutputType(OutputType outputType, LogProvider<? extends LogEncoder> formatter);

}

final class DefaultEncoderRegistry implements LogEncoderRegistry {

	/*
	 * The logfmt encoder's URI scheme.
	 */
	static final String LOGFMT_SCHEME = "logfmt";

	/*
	 * The core JSON5 encoder's URI scheme.
	 */
	static final String JSON5_SCHEME = "json5";

	/*
	 * The core JSON encoder's URI scheme.
	 */
	static final String JSON_SCHEME = "json";

	private final Map<String, EncoderProvider> providers = new ConcurrentHashMap<>();

	/**
	 * Creates encoder registry
	 * @return encoder registry
	 */
	public static LogEncoderRegistry of() {
		var registry = new DefaultEncoderRegistry();
		registry.register(TTLL.SCHEMA,
				ref -> (name, config) -> LogEncoder
					.of(ttll(name, config).fromProperties(config.properties(), ref).build())
					.provide(name, config));
		registry.register(LOGFMT_SCHEME,
				ref -> (name, config) -> LogEncoder
					.of(logfmt(name, config).fromProperties(config.properties(), ref).build())
					.provide(name, config));
		registry.register(JSON5_SCHEME, DefaultEncoderRegistry::json5Encoder);
		registry.register(JSON_SCHEME,
				ref -> (name, config) -> LogEncoder
					.builder(new JsonFormatterBuilder(name).fromProperties(config.properties(), ref).build())
					.contentType(StandardContentType.APPLICATION_JSON)
					.build()
					.provide(name, config));
		return registry;
	}

	@Override
	public LogProvider<LogEncoder> provide(LogProviderRef ref) {
		var _ref = DefaultLogProviderRef.normalize(ref);
		var uri = _ref.uri();
		/*
		 * TODO file bug with checker as it should have found scheme to be null.
		 */
		String scheme = uri.getScheme();
		if (scheme == null) {
			throw new IllegalStateException("bug. uri was not normalized");
		}

		var provider = providers.get(scheme);
		if (provider == null) {
			throw LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.ENCODER, scheme, uri,
					providers.keySet());
		}

		return provider.provide(_ref);
	}

	@Override
	public void register(String scheme, EncoderProvider encoder) {
		/*
		 * TODO maybe check if one is already registered and do something?
		 */
		providers.put(scheme, encoder);
	}

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final EnumMap<OutputType, LogProvider<? extends LogEncoder>> formatters = new EnumMap<>(OutputType.class);

	/*
	 * Builders for the coloring formatters that honor the global ANSI mode: it sets the
	 * default color mode, while an explicit color property still wins.
	 */
	static TTLLFormatterBuilder ttll(String name, LogConfig config) {
		var b = new TTLLFormatterBuilder(name).serviceRegistry(config.serviceRegistry());
		var color = colorMode(config);
		if (color != null) {
			b.color(color);
		}
		return b;
	}

	static LogfmtFormatterBuilder logfmt(String name, LogConfig config) {
		var b = new LogfmtFormatterBuilder(name).serviceRegistry(config.serviceRegistry());
		var color = colorMode(config);
		if (color != null) {
			b.color(color);
		}
		return b;
	}

	static Json5FormatterBuilder json5(String name, LogConfig config) {
		var b = new Json5FormatterBuilder(name).serviceRegistry(config.serviceRegistry());
		var color = colorMode(config);
		if (color != null) {
			b.color(color);
		}
		return b;
	}

	/*
	 * JSON5 has no registered media type, so this uses the one in common use.
	 */
	static final ContentType JSON5_CONTENT_TYPE = ContentType.of("application/json5", StandardCharsets.UTF_8);

	/*
	 * The content type follows the format property, so a json5 encoder switched to JSON
	 * reports application/json.
	 */
	private static LogProvider<LogEncoder> json5Encoder(LogProviderRef ref) {
		return (name, config) -> {
			var builder = json5(name, config).fromProperties(config.properties(), ref);
			var properties = LogProperties.of(ref.uri(), builder.propertyPrefix(), config.properties(),
					ref.keyOrNull());
			var format = properties.forKey(builder.propertyPrefix() + "format")
				.ofString()
				.map(v -> LogProperty.enumValue(Format.class, v))
				.or(Format.JSON5)
				.validateNow(Json5FormatterBuilder.class);
			var contentType = format == Format.JSON ? StandardContentType.APPLICATION_JSON : JSON5_CONTENT_TYPE;
			return LogEncoder.builder(builder.build()).contentType(contentType).build().provide(name, config);
		};
	}

	private static TTLL.@Nullable ColorMode colorMode(LogConfig config) {
		return switch (config.globals().ansiMode()) {
			case AUTO -> null;
			case ON -> TTLL.ColorMode.FORCE;
			case OFF -> TTLL.ColorMode.OFF;
		};
	}

	/**
	 * Associates a default formatter with a specific output type
	 * @param outputType output type to use for finding best default formatter.
	 * @return encoder for output type.
	 */
	@Override
	public LogProvider<? extends LogEncoder> encoderForOutputType(OutputType outputType) {
		lock.readLock().lock();
		try {
			var formatter = formatters.get(outputType);
			if (formatter == null) {
				if (outputType == OutputType.CONSOLE_OUT || outputType == OutputType.CONSOLE_ERR) {
					return LogEncoder.ofTTLL();
				}
				/*
				 * Never color a file or other non console output unless asked: ANSI
				 * detection only knows about the console.
				 */
				return (name, config) -> LogEncoder
					.of(ttll(name, config).color(TTLL.ColorMode.OFF).fromProperties(config.properties()).build())
					.provide(name, config);
			}
			return Objects.requireNonNull(formatter);
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Sets a default formatter for a specific output type.
	 * @param outputType output type.
	 * @param formatter formatter.
	 */
	@Override
	public void setEncoderForOutputType(OutputType outputType, LogProvider<? extends LogEncoder> formatter) {
		lock.writeLock().lock();
		try {
			formatters.put(outputType, formatter);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

}
