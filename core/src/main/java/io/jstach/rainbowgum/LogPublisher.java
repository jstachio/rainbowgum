package io.jstach.rainbowgum;

import io.jstach.rainbowgum.router.BlockingQueueAsyncLogPublisher;
import io.jstach.rainbowgum.router.LockingSyncLogPublisher;

public sealed interface LogPublisher extends LogEventLogger, AutoCloseable {

	public void start(LogConfig config);

	public boolean synchronous();

	public void close();

	non-sealed interface AsyncLogPublisher extends LogPublisher {

		@Override
		public void start(LogConfig config);

		@Override
		default boolean synchronous() {
			return false;
		}

		public static AsyncLogPublisher.Builder builder() {
			return new Builder();
		}

		public static class Builder extends LogRouter.AbstractBuilder<AsyncLogPublisher.Builder> {

			private int bufferSize = 1024;

			private Builder() {
				super();
			}

			public AsyncLogPublisher.Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public LogPublisher.AsyncLogPublisher build() {
				return BlockingQueueAsyncLogPublisher.of(LogAppender.of(appenders), bufferSize);
			}

			@Override
			protected AsyncLogPublisher.Builder self() {
				return this;
			}

		}

	}

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

		public static class Builder extends LogRouter.AbstractBuilder<SyncLogPublisher.Builder> {

			private Builder() {
				super();
			}

			@Override
			protected SyncLogPublisher.Builder self() {
				return this;
			}

			public LogPublisher.SyncLogPublisher build() {
				return new LockingSyncLogPublisher(LogAppender.of(appenders));
			}

		}

	}

}