package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.router.BlockingQueueRouter;
import io.jstach.rainbowgum.router.LockingQueueRouter;

public sealed interface LogRouter extends LogEventLogger, AutoCloseable {

	LevelResolver levelResolver();

	default boolean isEnabled(String loggerName, java.lang.System.Logger.Level level) {
		return levelResolver().isEnabled(loggerName, level);
	}

	default void log(String loggerName, java.lang.System.Logger.Level level, String message,
			@Nullable Throwable cause) {
		log(LogEvent.of(level, loggerName, message, cause));
	}

	default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
		log(LogEvent.of(level, loggerName, message, null));
	}

	void log(LogEvent event);

	default void start(LogConfig config) {
	}

	@Override
	public void close();

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

	abstract class AbstractBuilder<T> extends LevelResolver.AbstractBuilder<T> {

		protected List<LogAppender> appenders = new ArrayList<>();

		protected AbstractBuilder() {
			super();
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

	public sealed interface RootRouter extends LogRouter {

		void start(LogConfig config);

		public static LogRouter of(List<? extends LogRouter> routers, LevelResolver levelResolver) {
			List<LogRouter> sorted = new ArrayList<>();
			/*
			 * We add the async routers first
			 */
			Set<LogRouter> matched = Collections.newSetFromMap(new IdentityHashMap<LogRouter, Boolean>());
			for (var r : routers) {
				if (r instanceof RootRouter) {
					throw new IllegalArgumentException("no root inside another router");
				}
				if (r instanceof AsyncLogRouter a) {
					matched.add(a);
					sorted.add(a);
				}
			}
			for (var r : routers) {
				if (r instanceof SyncLogRouter a && !matched.contains(a)) {
					sorted.add(a);
				}
			}
			return CompositeLogRouter.of(sorted, levelResolver);
		}

	}

	public non-sealed interface SyncLogRouter extends LogRouter {

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private Builder() {
				super();
			}

			@Override
			protected Builder self() {
				return this;
			}

			public SyncLogRouter build() {
				return new LockingQueueRouter(LogAppender.of(appenders), buildLevelResolver());
			}

		}

	}

	public non-sealed interface AsyncLogRouter extends LogRouter {

		@Override
		public void start(LogConfig config);

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder extends AbstractBuilder<Builder> {

			private int bufferSize = 1024;

			private Builder() {
				super();
			}

			public Builder bufferSize(int bufferSize) {
				this.bufferSize = bufferSize;
				return this;
			}

			public AsyncLogRouter build() {
				return BlockingQueueRouter.of(LogAppender.of(appenders), buildLevelResolver(), bufferSize);
			}

			@Override
			protected Builder self() {
				return this;
			}

		}

	}

}

record CompositeLogRouter(LogRouter[] routers, LevelResolver levelResolver) implements RootRouter {

	public static RootRouter of(List<? extends LogRouter> routers, LevelResolver levelResolver) {
		List<LevelResolver> resolvers = new ArrayList<>();
		routers.stream().map(LogRouter::levelResolver).forEach(resolvers::add);
		resolvers.add(levelResolver);
		var resolver = LevelResolver.of(resolvers);
		LogRouter[] array = routers.toArray(new LogRouter[] {});
		return new CompositeLogRouter(array, resolver);
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		for (var r : routers) {
			r.log(event);
		}

	}

	@Override
	public void start(LogConfig config) {
		for (var r : routers) {
			r.start(config);
		}
	}

	@Override
	public void close() {
		for (var r : routers) {
			r.close();
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

enum GlobalLogRouter implements RootRouter {

	INSTANCE;

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private static final LevelResolver INFO_RESOLVER = LevelResolver.of(Level.INFO);

	private volatile @Nullable LogRouter delegate = null;

	@Override
	public LevelResolver levelResolver() {
		LogRouter d = delegate;
		if (d != null) {
			return d.levelResolver();
		}
		return INFO_RESOLVER;
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
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
	}

}
