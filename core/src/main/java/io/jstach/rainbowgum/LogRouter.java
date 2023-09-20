package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;

import org.eclipse.jdt.annotation.Nullable;

import com.lmax.disruptor.util.DaemonThreadFactory;

import io.jstach.rainbowgum.LogRouter.SyncLogRouter;
import io.jstach.rainbowgum.disruptor.DisruptorLogRouter;

public sealed interface LogRouter extends LogAppender, AutoCloseable {

	public static LogRouter global() {
		return GlobalLogRouter.INSTANCE;
	}

	static void setRouter(LogRouter router) {
		if (GlobalLogRouter.INSTANCE == router) {
			throw new IllegalArgumentException();
		}
		GlobalLogRouter.INSTANCE.drain(router);
	}

	public static void error(LogEvent event) {
		FailsafeAppender.INSTANCE.append(event);
	}

	public static void error(Class<?> loggerName, Throwable throwable) {
		error(loggerName, throwable.getMessage(), throwable);
	}

	public static void error(Class<?> loggerName, String message, Throwable throwable) {
		var event = LogEvent.of(Level.ERROR, loggerName.getName(), message, throwable);
		error(event);
	}

	boolean isEnabled(String loggerName, java.lang.System.Logger.Level level);

	default void log(String loggerName, java.lang.System.Logger.Level level, String message,
			@Nullable Throwable cause) {
		log(LogEvent.of(level, loggerName, message, cause));
	}

	default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
		log(LogEvent.of(level, loggerName, message, null));
	}

	@Override
	default void append(LogEvent event) {
		log(event);
	}

	void log(LogEvent event);

	default void start(LogConfig config) {
	}

	@Override
	public void close();

	public non-sealed interface SyncLogRouter extends LogRouter {

		public static Builder builder(LogConfig config) {
			return new Builder(config);
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private Builder(LogConfig config) {
				super(config);
			}

			@Override
			protected Builder self() {
				return this;
			}

			public SyncLogRouter build() {
				return new DefaultLogRouter(List.copyOf(appenders), config.levelResolver());
			}

		}

	}

	public non-sealed interface AsyncLogRouter extends LogRouter {

		@Override
		public void start(LogConfig config);

		public static Builder builder(LogConfig config) {
			return new Builder(config);
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;

			private int bufferSize = 1024;

			private Builder(LogConfig config) {
				super(config);
			}

			public Builder threadFactory(ThreadFactory threadFactory) {
				this.threadFactory = threadFactory;
				return this;
			}

			public Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public AsyncLogRouter build() {
				return DisruptorLogRouter.of(config.levelResolver(), List.copyOf(this.appenders()), threadFactory,
						bufferSize);
			}

			@Override
			protected Builder self() {
				return this;
			}

		}

	}

}

class DefaultLogRouter implements SyncLogRouter {

	private final List<? extends LogAppender> logAppenders;

	private final LevelResolver levelResolver;

	public DefaultLogRouter(List<? extends LogAppender> logAppenders, LevelResolver levelResolver) {
		super();
		this.logAppenders = logAppenders;
		this.levelResolver = levelResolver;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		for (var a : logAppenders) {
			a.append(event);
		}
	}

	@Override
	public void close() {
		for (var appender : logAppenders) {
			appender.close();
		}
	}

}

enum FailsafeAppender implements LogAppender {

	INSTANCE;

	@Override
	public void append(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			System.err.println(event.formattedMessage());
			var throwable = event.throwable();
			if (throwable != null) {
				throwable.printStackTrace(System.err);
			}
		}
	}

}

enum GlobalLogRouter implements LogRouter {

	INSTANCE;

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private volatile @Nullable LogRouter delegate = null;

	public boolean isEnabled(String loggerName, java.lang.System.Logger.Level level) {
		LogRouter d = delegate;
		if (d != null) {
			return d.isEnabled(loggerName, level);
		}
		return level.compareTo(System.Logger.Level.DEBUG) > 0;
	}

	public void log(String loggerName, java.lang.System.Logger.Level level, String message, @Nullable Throwable cause) {
		LogRouter d = delegate;
		if (d != null) {
			if (d.isEnabled(loggerName, level)) {
				d.log(loggerName, level, message, cause);
			}
		}
		else {
			events.add(LogEvent.of(level, loggerName, message, cause));
		}

	}

	public synchronized void drain(LogRouter delegate) {
		this.delegate = Objects.requireNonNull(delegate);
		LogEvent e;
		while ((e = this.events.poll()) != null) {
			if (delegate.isEnabled(e.loggerName(), e.level())) {
				delegate.log(e);
			}
		}
	}

	@Override
	public void log(LogEvent event) {
		LogRouter d = delegate;
		if (d != null) {
			if (d.isEnabled(event.loggerName(), event.level())) {
				d.log(event);
			}
		}
		else {
			events.add(event);
		}
	}

	@Override
	public void close() {
	}

}

abstract class AbstractBuilder<T> {

	protected List<LogAppender> appenders = new ArrayList<>();

	protected final LogConfig config;

	public AbstractBuilder(LogConfig config) {
		super();
		this.config = config;
	}

	public T appenders(List<LogAppender> appenders) {
		this.appenders = appenders;
		return self();
	}

	public T appender(LogAppender appender) {
		this.appenders.add(appender);
		return self();
	}

	protected List<LogAppender> appenders() {
		return this.appenders;
	}

	protected abstract T self();

}
