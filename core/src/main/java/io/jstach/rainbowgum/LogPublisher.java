package io.jstach.rainbowgum;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;

/**
 * Publishers push logs to appenders either synchronously or asynchronously.
 */
public sealed interface LogPublisher extends LogEventLogger, LogLifecycle {

	/**
	 * If the publisher is synchronous.
	 * @return true if {@link LogPublisher.SyncLogPublisher}.
	 */
	public boolean synchronous();

	/**
	 * A factory for a publisher from config and appenders.
	 */
	public interface PublisherFactory {

		/**
		 * Create the log publisher from config and appenders.
		 * @param name used to identify where to pull config from.
		 * @param config log config.
		 * @param appenders appenders.
		 * @return publisher.
		 */
		LogPublisher create(String name, LogConfig config, List<? extends LogAppender> appenders);

		/**
		 * Provides a publisher factory by URI.
		 * @param scheme scheme will be used to resolve publisher factory.
		 * @return publisher factory.
		 */
		static PublisherFactory of(String scheme) {
			URI uri = URI.create(scheme + ":///");
			return of(uri);
		}

		/**
		 * Provides the default publisher factory.
		 * @return publisher factory.
		 */
		static PublisherFactory of() {
			return of(LogPublisherRegistry.DEFAULT_SCHEME);
		}

		/**
		 * Provides a publisher factory by URI.
		 * @param uri uri whose scheme will be used to resolve publisher factory.
		 * @return publisher factory.
		 */
		static PublisherFactory of(URI uri) {
			return (name, config, appenders) -> config.publisherRegistry()
				.provide(uri, name, config.properties())
				.create(name, config, appenders);
		}

		/**
		 * Provides the default async publisher.
		 * @param bufferSize maybe null provided as convenience as almost all async
		 * publishers have some buffer.
		 * @return async publisher.
		 */
		static PublisherFactory ofAsync(@Nullable Integer bufferSize) {
			String query = bufferSize == null ? ""
					: "?" + DefaultPublisherProviders.BUFFER_SIZE_NAME + "=" + bufferSize;
			URI uri = URI.create(LogPublisherRegistry.ASYNC_SCHEME + ":///" + query);
			return of(uri);
		}

		/**
		 * Provides the default registered sync publisher.
		 * @return sync publisher.
		 */
		static PublisherFactory ofSync() {
			return of(LogPublisherRegistry.SYNC_SCHEME);
		}

		/**
		 * Async builder.
		 * @return async builder.
		 */
		public static AsyncLogPublisher.Builder async() {
			return AsyncLogPublisher.builder();
		}

		/**
		 * Sync builder.
		 * @return sync builder.
		 */
		public static SyncLogPublisher.Builder sync() {
			return SyncLogPublisher.builder();
		}

	}

	/**
	 * SPI for custom publishers.
	 */
	public interface PublisherProvider extends PluginProvider<PublisherFactory, RuntimeException> {

		/**
		 * Provides a publisher factory by properties and uri.
		 * @param uri uri configuration of publisher
		 * @param name name to use for config finding.
		 * @param properties configuration properties
		 * @return publisher factory.
		 */
		@Override
		PublisherFactory provide(URI uri, String name, LogProperties properties);

	}

	/**
	 * Abstract publisher builder.
	 *
	 * @param <T> publisher builder type.
	 */
	abstract class AbstractBuilder<T> {

		/**
		 * do nothing.
		 */
		protected AbstractBuilder() {
			super();
		}

		/**
		 * This.
		 * @return this.
		 */
		protected abstract T self();

		/**
		 * Creates publisher provider.
		 * @return publisher provider
		 */
		public abstract PublisherFactory build();

	}

	/**
	 * Async publisher.
	 */
	non-sealed interface AsyncLogPublisher extends LogPublisher {

		@Override
		default boolean synchronous() {
			return false;
		}

		/**
		 * Async publisher builder.
		 * @return builder.
		 */
		public static AsyncLogPublisher.Builder builder() {
			return new Builder();
		}

		/**
		 * Async publisher builder.
		 */
		public static class Builder extends AbstractBuilder<AsyncLogPublisher.Builder> {

			/**
			 * Buffer Size Property for Async publishers.
			 */
			public static final String BUFFER_SIZE_PROPERTY = DefaultPublisherProviders.BUFFER_SIZE_PROPERTY;

			private @Nullable Integer bufferSize;

			private Builder() {
			}

			/**
			 * Sets buffer size. Typically means how many events can be queued up. Default
			 * is usually 1024.
			 * @param bufferSize buffer size.
			 * @return this.
			 */
			public AsyncLogPublisher.Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			@Override
			public PublisherFactory build() {
				Integer bufferSize = this.bufferSize;
				return PublisherFactory.ofAsync(bufferSize);
			}

			@Override
			protected AsyncLogPublisher.Builder self() {
				return this;
			}

		}

	}

	/**
	 * Synchronous publisher.
	 */
	non-sealed interface SyncLogPublisher extends LogPublisher {

		/**
		 * Sync publisher builder.
		 * @return builder.
		 */
		public static SyncLogPublisher.Builder builder() {
			return new Builder();
		}

		@Override
		default boolean synchronous() {
			return true;
		}

		/**
		 * Synchronous publisher builder.
		 */
		public static final class Builder extends AbstractBuilder<SyncLogPublisher.Builder> {

			private Builder() {
			}

			/**
			 * This builder.
			 * @return this builder.
			 */
			@Override
			protected SyncLogPublisher.Builder self() {
				return this;
			}

			/**
			 * Build a publisher provider.
			 * @return provider
			 */
			@Override
			public PublisherFactory build() {
				return PublisherFactory.ofSync();
			}

		}

	}

}

final class DefaultSyncLogPublisher implements LogPublisher.SyncLogPublisher {

	private final ThreadSafeLogAppender appender;

	public DefaultSyncLogPublisher(ThreadSafeLogAppender appender) {
		super();
		this.appender = appender;
	}

	@Override
	public void log(LogEvent event) {
		appender.append(event);
	}

	@Override
	public void start(LogConfig config) {
		appender.start(config);
	}

	@Override
	public void close() {
		appender.close();
	}

	@Override
	public String toString() {
		return "DefaultSyncLogPublisher[appender=" + appender + "]";
	}

}