package io.jstach.rainbowgum;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;
import io.jstach.rainbowgum.format.TTLL;
import io.jstach.rainbowgum.format.TTLLFormatterBuilder;

/**
 * Encoder registry. Only one encoder is registered by default which has the URI schema of
 * {@value AbstractStandardEventFormatter#SCHEMA} and is used if no encoder is found for
 * the {@link OutputType} of the resolved output.
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

	private final Map<String, EncoderProvider> providers = new ConcurrentHashMap<>();

	/**
	 * Creates encoder registry
	 * @return encoder registry
	 */
	public static LogEncoderRegistry of() {
		var registry = new DefaultEncoderRegistry();
		registry.register(AbstractStandardEventFormatter.SCHEMA,
				ref -> (name, config) -> LogEncoder
					.of(ttll(name, config).fromProperties(config.properties(), ref).build())
					.provide(name, config));
		registry.register(LogfmtFormatter.SCHEME,
				ref -> (name, config) -> LogEncoder
					.of(new LogfmtFormatterBuilder(name).fromProperties(config.properties(), ref).build())
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
			throw LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.ENCODER, scheme, uri);
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
	 * A TTLL builder that honors the global ANSI disable property: when it is set the
	 * color theme defaults to off, while an explicit color property still wins.
	 */
	static TTLLFormatterBuilder ttll(String name, LogConfig config) {
		var b = new TTLLFormatterBuilder(name);
		boolean ansiDisabled = config.properties()
			.forKey(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY)
			.ofBoolean()
			.or(false)
			.validateNow(LogEncoderRegistry.class);
		if (ansiDisabled) {
			b.color(TTLL.ColorTheme.OFF);
		}
		return b;
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
					.of(ttll(name, config).color(TTLL.ColorTheme.OFF).fromProperties(config.properties()).build())
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
