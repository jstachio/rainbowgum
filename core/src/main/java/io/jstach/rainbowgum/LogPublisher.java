package io.jstach.rainbowgum;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.publisher.BlockingQueueAsyncLogPublisher;

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
			 * Default async buffer size.
			 */
			public static final int ASYNC_BUFFER_SIZE = 1024;

			/**
			 * Buffer Size Property for Async publishers.
			 */
			public static final String BUFFER_SIZE_PROPERTY = LogProperties.PUBLISHER_PREFIX + "bufferSize";

			private @Nullable Integer bufferSize;

			private Builder() {
			}

			/**
			 * Sets buffer size. Typically means how many events can be queued up. Default
			 * is {@value #ASYNC_BUFFER_SIZE}.
			 * @param bufferSize buffer size.
			 * @return this.
			 */
			public AsyncLogPublisher.Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public PublisherFactory build() {
				Integer bufferSize = this.bufferSize;
				return (name, config, appenders) -> asyncPublisher(name, config, appenders, bufferSize);
			}

			@Override
			protected AsyncLogPublisher.Builder self() {
				return this;
			}

			private static LogPublisher.AsyncLogPublisher asyncPublisher(String name, LogConfig config,
					List<? extends LogAppender> appenders, Integer bufferSize) {
				int _bufferSize = Property.builder()
					.toInt() //
					.buildWithName(BUFFER_SIZE_PROPERTY, name) //
					.get(config.properties()) //
					.value(ASYNC_BUFFER_SIZE);
				return BlockingQueueAsyncLogPublisher.of(appenders, _bufferSize);
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
			public PublisherFactory build() {
				return (name, config,
						appenders) -> new DefaultSyncLogPublisher(ThreadSafeLogAppender.of(LogAppender.of(appenders)));
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

}