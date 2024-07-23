package io.jstach.rainbowgum;

import java.net.URI;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogPublisher.PublisherProvider;
import io.jstach.rainbowgum.publisher.BlockingQueueAsyncLogPublisher;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Registry of publishers. Rainbow Gum registers default publishers with the following
 * schemes:
 * <ul>
 * <li>{@value #SYNC_SCHEME} - default sync publisher</li>
 * <li>{@value #ASYNC_SCHEME} - default async publisher</li>
 * <li>{@value #DEFAULT_SCHEME} - by default this is the same as
 * {@link #SYNC_SCHEME}.</li>
 * </ul>
 * Plugins may override the above so that the above is automatically replaced with other
 * providers via the service loader.
 *
 * @see RainbowGumServiceProvider
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

	/**
	 * Because publisher configurators through service provider discovery can replace the
	 * default schemes of {@link #ASYNC_SCHEME}, {@link #SYNC_SCHEME} all the builtin core
	 * providers are prefixed with this value so that they can still be used even if
	 * overriden.
	 */
	public static String BUILTIN_SCHEME_PREFIX = "core.";

	/**
	 * Default async buffer size.
	 */
	public static final int ASYNC_BUFFER_SIZE = 1024;

	/**
	 * Buffer Size property name.
	 */
	public static final String BUFFER_SIZE_NAME = "bufferSize";

	/**
	 * Buffer Size Property for Async publishers.
	 */
	public static final String BUFFER_SIZE_PROPERTY = LogProperties.PUBLISHER_PREFIX + BUFFER_SIZE_NAME;

}

final class DefaultPublisherRegistry implements LogPublisherRegistry {

	ConcurrentHashMap<String, PublisherProvider> providers = new ConcurrentHashMap<String, LogPublisher.PublisherProvider>();

	@Override
	public PublisherFactory provide(LogProviderRef ref) {
		ref = DefaultLogProviderRef.normalize(ref);
		var uri = ref.uri();
		String scheme = uri.getScheme();
		if (scheme == null) {
			throw new IllegalArgumentException("URI is missing scheme. uri: " + uri);
		}
		var provider = Optional.ofNullable(providers.get(scheme)).orElseThrow();
		return provider.provide(ref);
	}

	@Override
	public void register(String scheme, LogPublisher.PublisherProvider publisherProvider) {
		providers.put(scheme, publisherProvider);
	}

	/**
	 * Creates a publisher registry instance.
	 * @return publisher registry.
	 */
	static LogPublisherRegistry of() {
		var r = new DefaultPublisherRegistry();
		for (var p : DefaultPublisherProviders.values()) {
			r.register(p.scheme(), p);
			r.register(BUILTIN_SCHEME_PREFIX + p.scheme(), p);
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
				.buildWithName(LogPublisherRegistry.BUFFER_SIZE_PROPERTY, name) //
				.get(properties) //
				.value(LogPublisherRegistry.ASYNC_BUFFER_SIZE);
			return (n, config, appenders) -> BlockingQueueAsyncLogPublisher
				.of(appenders.flags(EnumSet.of(LogAppender.AppenderFlag.REUSE_BUFFER)).asSingle(), _bufferSize);
		}
	};

	@Override
	public PublisherFactory provide(LogProviderRef ref) {
		return (n, config, appenders) -> provide(ref.uri(), n, config.properties()).create(n, config, appenders);
	}

	public abstract String scheme();

	protected abstract PublisherFactory provide(String name, LogProperties properties);

	public PublisherFactory provide(URI uri, String name, LogProperties properties) {
		String prefix = LogProperties.interpolateKey(LogProperties.PUBLISHER_PREFIX, Map.of(LogProperties.NAME, name));
		LogProperties combined = LogProperties.of(uri, prefix, properties);
		return provide(name, combined);
	}

}
