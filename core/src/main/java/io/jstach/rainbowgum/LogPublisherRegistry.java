package io.jstach.rainbowgum;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.publisher.BlockingQueueAsyncLogPublisher;

/**
 * Registry of publishers
 */
public sealed interface LogPublisherRegistry extends LogPublisher.PublisherProvider {

	/**
	 * Register a publisher provider by URI scheme.
	 * @param scheme uri scheme.
	 * @param publisherProvider provider.
	 */
	public void register(String scheme, LogPublisher.PublisherProvider publisherProvider);

	/**
	 * This is URI scheme for the async publisher used by the publisher builder. Call
	 * {@link #register(String, io.jstach.rainbowgum.LogPublisher.PublisherProvider)} with
	 * this scheme to replace the default async publisher.
	 */
	public static String ASYNC_SCHEME = "async";

	/**
	 * This is the URI scheme for the sync publisher builder to find a sync publisher.
	 * Call {@link #register(String, io.jstach.rainbowgum.LogPublisher.PublisherProvider)}
	 * with this scheme to replace the default sync publisher.
	 */
	public static String SYNC_SCHEME = "sync";

	/**
	 * This is the URI scheme for the default publisher.
	 * {@link #register(String, io.jstach.rainbowgum.LogPublisher.PublisherProvider)} with
	 * this scheme to replace the default publisher.
	 */
	public static String DEFAULT_SCHEME = "default";

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

	/**
	 * Creates a publisher registry instance.
	 * @return publisher registry.
	 */
	static LogPublisherRegistry of() {
		var r = new DefaultPublisherRegistry();
		for (var p : DefaultPublisherProviders.values()) {
			r.register(p.scheme(), p);
		}
		return r;
	}

}

enum DefaultPublisherProviders implements LogPublisher.PublisherProvider {

	DEFAULT {
		@Override
		public String scheme() {
			return LogPublisherRegistry.DEFAULT_SCHEME;
		}

		@Override
		protected PublisherFactory provide(String name, LogProperties properties) {
			return (n, config, appenders) -> new DefaultSyncLogPublisher(appenders.asSingle());
		}
	},
	SYNC {
		@Override
		public String scheme() {
			return LogPublisherRegistry.SYNC_SCHEME;
		}

		@Override
		protected PublisherFactory provide(String name, LogProperties properties) {
			return (n, config, appenders) -> new DefaultSyncLogPublisher(appenders.asSingle());
		}

	},
	ASYNC {

		@Override
		public String scheme() {
			return LogPublisherRegistry.ASYNC_SCHEME;
		}

		@Override
		protected PublisherFactory provide(String name, LogProperties properties) {
			int _bufferSize = Property.builder()
				.ofInt() //
				.buildWithName(BUFFER_SIZE_PROPERTY, name) //
				.get(properties) //
				.value(ASYNC_BUFFER_SIZE);
			return (n, config, appenders) -> BlockingQueueAsyncLogPublisher.of(appenders.asSingle(), _bufferSize);
		}
	};

	public abstract String scheme();

	protected abstract PublisherFactory provide(String name, LogProperties properties);

	@Override
	public PublisherFactory provide(URI uri, String name, LogProperties properties) {
		String prefix = LogProperties.interpolateKey(LogProperties.PUBLISHER_PREFIX, Map.of(LogProperties.NAME, name));
		LogProperties combined = LogProperties.of(uri, prefix, properties);
		return provide(name, combined);
	}

	/**
	 * Default async buffer size.
	 */
	public static final int ASYNC_BUFFER_SIZE = 1024;

	public static final String BUFFER_SIZE_NAME = "bufferSize";

	/**
	 * Buffer Size Property for Async publishers.
	 */
	public static final String BUFFER_SIZE_PROPERTY = LogProperties.PUBLISHER_PREFIX + BUFFER_SIZE_NAME;

}
