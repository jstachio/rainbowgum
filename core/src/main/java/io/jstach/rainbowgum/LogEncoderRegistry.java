package io.jstach.rainbowgum;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.format.StandardEventFormatter;

/**
 * Encoder registry
 */
public sealed interface LogEncoderRegistry extends EncoderProvider {

	/**
	 * Finds an encoder by name.
	 * @param name encoder name.
	 * @return encoder
	 */
	public Optional<LogEncoder> encoder(String name);

	/**
	 * Registers an encoder by name.
	 * @param name encoder name.
	 * @param encoder loaded encoder
	 */
	public void register(String name, LogEncoder encoder);

	/**
	 * Registers an encoder by uri scheme.
	 * @param scheme encoder name.
	 * @param encoder loaded encoder
	 */
	public void register(String scheme, EncoderProvider encoder);

	/**
	 * Associates a default formatter with a specific output type
	 * @param outputType output type to use for finding best default formatter.
	 * @return formatter for output type.
	 */
	public LogEncoder encoderForOutputType(OutputType outputType);

	/**
	 * Sets a default formatter for a specific output type.
	 * @param outputType output type.
	 * @param formatter formatter.
	 */
	public void setEncoderForOutputType(OutputType outputType, Supplier<? extends LogEncoder> formatter);

	/**
	 * Creates encoder registry
	 * @return encoder registry
	 */
	public static LogEncoderRegistry of() {
		return new DefaultEncoderRegistry();
	}

}

final class DefaultEncoderRegistry extends ProviderRegistry<LogEncoder.EncoderProvider, LogEncoder, RuntimeException>
		implements LogEncoderRegistry {

	private Map<String, LogEncoder> encoders = new ConcurrentHashMap<>();

	@Override
	public Optional<LogEncoder> encoder(String name) {
		return Optional.ofNullable(encoders.get(name));
	}

	@Override
	public void register(String name, LogEncoder encoder) {
		encoders.put(name, encoder);
	}

	private static URI normalize(URI uri) {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		try {
			if (scheme == null) {
				if (path == null) {
					throw new IllegalArgumentException("URI is not proper: " + uri);
				}
				if (path.startsWith("./") || path.startsWith("/")) {
					uri = new URI("name://" + path);
				}
				else {
					uri = new URI(path + ":///");
				}
			}
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI is not proper: " + uri);
		}
		return uri;
	}

	@Override
	public LogEncoder provide(URI uri, String name, LogProperties properties) {
		uri = normalize(uri);
		/*
		 * TODO file bug with checker as it should have found scheme to be null.
		 */
		String scheme = uri.getScheme();
		if (scheme == null) {
			throw new IllegalStateException("bug. uri was not normalized");
		}
		if (scheme.equals("name")) {
			String _name = uri.getHost();
			if (_name == null) {
				_name = name;
			}
			return _encoder(uri, name, _name);

		}
		var provider = providers.get(scheme);
		if (provider == null) {
			throw new NoSuchElementException("No encoder found. URI=" + uri);
		}
		return provider.provide(uri, name, properties);
	}

	private LogEncoder _encoder(URI uri, String name, String resolvedName) {
		return encoder(resolvedName).orElseThrow(() -> new NoSuchElementException(
				"No encoder found. resolved='" + resolvedName + "', name='" + name + "', uri='" + uri + "'"));
	}

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private final EnumMap<OutputType, Supplier<? extends LogEncoder>> formatters = new EnumMap<>(OutputType.class);

	/**
	 * Associates a default formatter with a specific output type
	 * @param outputType output type to use for finding best default formatter.
	 * @return encoder for output type.
	 */
	@Override
	public LogEncoder encoderForOutputType(OutputType outputType) {
		lock.readLock().lock();
		try {
			var formatter = formatters.get(outputType);
			if (formatter == null) {
				return LogEncoder.of(StandardEventFormatter.builder().build());
			}
			return Objects.requireNonNull(formatter.get());
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
	public void setEncoderForOutputType(OutputType outputType, Supplier<? extends LogEncoder> formatter) {
		lock.writeLock().lock();
		try {
			formatters.put(outputType, formatter);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

}
