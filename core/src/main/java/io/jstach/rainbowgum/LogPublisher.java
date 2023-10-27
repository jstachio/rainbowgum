package io.jstach.rainbowgum;

import java.util.List;

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
	public interface PublisherProvider {

		/**
		 * Create the log publisher from config and appenders.
		 * @param config log config.
		 * @param appenders appenders.
		 * @return publisher.
		 */
		LogPublisher provide(LogConfig config, List<? extends LogAppender> appenders);

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
	 * Abstract publisher builder.
	 *
	 * @param <T> publisher builder type.
	 */
	abstract class AbstractBuilder<T> {

		protected AbstractBuilder() {
			super();
		}

		protected abstract T self();

		public abstract PublisherProvider build();

	}

	/**
	 * Async publisher.
	 */
	non-sealed interface AsyncLogPublisher extends LogPublisher {

		@Override
		default boolean synchronous() {
			return false;
		}

		public static AsyncLogPublisher.Builder builder() {
			return new Builder();
		}

		public static class Builder extends AbstractBuilder<AsyncLogPublisher.Builder> {

			private int bufferSize = 1024;

			private Builder() {
			}

			public AsyncLogPublisher.Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public PublisherProvider build() {
				int bufferSize = this.bufferSize;
				return (config, appenders) -> config.defaults().asyncPublisher(appenders, bufferSize);
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

		public static SyncLogPublisher.Builder builder() {
			return new Builder();
		}

		@Override
		default void start(LogConfig config) {

		}

		default boolean synchronous() {
			return true;
		}

		public static class Builder extends AbstractBuilder<SyncLogPublisher.Builder> {

			private Builder() {
			}

			@Override
			protected SyncLogPublisher.Builder self() {
				return this;
			}

			public PublisherProvider build() {
				return (config,
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
	public void close() {
		appender.close();
	}

}