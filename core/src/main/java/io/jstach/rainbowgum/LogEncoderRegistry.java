package io.jstach.rainbowgum;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

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
		StandardEventFormatter.builder().build().register(registry);
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
			throw new LogProviderRef.NotFoundException("No encoder found. URI=" + uri);
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
				return LogEncoder.ofTTLL();
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
