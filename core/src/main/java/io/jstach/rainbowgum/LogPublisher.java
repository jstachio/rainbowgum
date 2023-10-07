package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.router.BlockingQueueAsyncLogPublisher;

public sealed interface LogPublisher extends LogEventLogger, AutoCloseable {

	public void start(LogConfig config);

	public boolean synchronous();

	public void close();

	abstract class AbstractBuilder<T> {

		protected List<LogAppender> appenders = new ArrayList<>();

		protected final LogConfig config;

		protected AbstractBuilder(LogConfig config) {
			super();
			this.config = config;
		}

		public T appenders(List<LogAppender> appenders) {
			this.appenders = appenders;
			return self();
		}

		public T appender(Provider<? extends LogAppender> appender) {
			this.appenders.add(appender.provide(config));
			return self();
		}

		public T appender(Consumer<LogAppender.Builder> consumer) {
			var builder = LogAppender.builder();
			consumer.accept(builder);
			this.appenders.add(builder.build().provide(config));
			return self();
		}

		protected List<LogAppender> appenders() {
			return this.appenders;
		}

		protected abstract T self();

	}

	non-sealed interface AsyncLogPublisher extends LogPublisher {

		@Override
		public void start(LogConfig config);

		@Override
		default boolean synchronous() {
			return false;
		}

		public static AsyncLogPublisher.Builder builder(LogConfig config) {
			return new Builder(config);
		}

		public static class Builder extends AbstractBuilder<AsyncLogPublisher.Builder> {

			private int bufferSize = 1024;

			private Builder(LogConfig config) {
				super(config);
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

		public static SyncLogPublisher.Builder builder(LogConfig config) {
			return new Builder(config);
		}

		@Override
		default void start(LogConfig config) {

		}

		default boolean synchronous() {
			return true;
		}

		public static class Builder extends AbstractBuilder<SyncLogPublisher.Builder> {

			private Builder(LogConfig config) {
				super(config);
			}

			@Override
			protected SyncLogPublisher.Builder self() {
				return this;
			}

			public LogPublisher.SyncLogPublisher build() {
				return new DefaultSyncLogPublisher(LogAppender.of(appenders));
			}

		}

	}

}

final class DefaultSyncLogPublisher implements LogPublisher.SyncLogPublisher {

	private final ThreadSafeLogAppender appender;

	public DefaultSyncLogPublisher(LogAppender appender) {
		super();
		this.appender = ThreadSafeLogAppender.of(appender);
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