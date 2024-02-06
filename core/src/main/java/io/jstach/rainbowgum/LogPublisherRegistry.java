package io.jstach.rainbowgum;

import java.net.URI;
import java.util.Optional;

import io.jstach.rainbowgum.LogPublisher.PublisherFactory;

/**
 * Registry of publishers
 */
public interface LogPublisherRegistry extends LogPublisher.PublisherProvider {

	/**
	 * Creates a publisher registry instance.
	 * @return publisher registry.
	 */
	public static LogPublisherRegistry of() {
		return new DefaultPublisherRegistry();
	}

}

final class DefaultPublisherRegistry
		extends ProviderRegistry<LogPublisher.PublisherProvider, PublisherFactory, RuntimeException>
		implements LogPublisherRegistry {

	@Override
	public PublisherFactory provide(URI uri, String name, LogProperties properties) {
		String scheme = uri.getScheme();
		if (scheme == null) {
			throw new IllegalArgumentException("URI is missing scheme. uri: " + uri);
		}
		var provider = Optional.ofNullable(providers.get(scheme)).orElseThrow();
		return provider.provide(uri, name, properties);
	}

}
