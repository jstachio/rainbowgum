package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Route;

public sealed interface LogRouter extends LogEventLogger, AutoCloseable {

	public boolean isEnabled(String loggerName, java.lang.System.Logger.Level level);

	void log(LogEvent event);

	default void start(LogConfig config) {
	}

	@Override
	public void close();

	public static RootRouter global() {
		return GlobalLogRouter.INSTANCE;
	}

	static void setRouter(RootRouter router) {
		if (GlobalLogRouter.INSTANCE == router) {
			throw new IllegalArgumentException();
		}
		GlobalLogRouter.INSTANCE.drain(router);
	}

	public sealed interface RootRouter extends LogRouter {

		void start(LogConfig config);

		public LevelResolver levelResolver();

		default void log(String loggerName, java.lang.System.Logger.Level level, String formattedMessage,
				@Nullable Throwable cause) {
			if (isEnabled(loggerName, level)) {
				LogEvent event = LogEvent.of(level, loggerName, formattedMessage, cause);
				log(event);
			}
		}

		default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
			log(loggerName, level, message, null);
		}

		public static RootRouter of(List<? extends Route> routes, LevelResolver levelResolver) {

			if (routes.isEmpty()) {
				throw new IllegalArgumentException("atleast one route is required");
			}
			List<Route> sorted = new ArrayList<>();
			/*
			 * We add the async routers first
			 */
			Set<Route> matched = Collections.newSetFromMap(new IdentityHashMap<Route, Boolean>());
			for (var r : routes) {
				if (!r.synchronous()) {
					matched.add(r);
					sorted.add(r);
				}
			}
			for (var r : routes) {
				if (r.synchronous() && !matched.contains(r)) {
					sorted.add(r);
				}
			}

			List<LevelResolver> resolvers = new ArrayList<>();
			routes.stream().map(Route::levelResolver).forEach(resolvers::add);
			resolvers.add(levelResolver);
			var globalLevelResolver = LevelResolver.of(resolvers);

			Route[] array = routes.toArray(new Route[] {});

			if (array.length == 1 && array[0].synchronous()) {
				return new SingleSyncRootRouter(array[0]);
			}
			return new CompositeLogRouter(array, globalLevelResolver);
		}

	}

	public sealed interface Route extends LogEventLogger {

		LevelResolver levelResolver();

		LogPublisher publisher();

		default boolean synchronous() {
			return publisher().synchronous();
		}

		default boolean isEnabled(String name, Level level) {
			return levelResolver().isEnabled(name, level);
		}

		default void log(LogEvent event) {
			publisher().log(event);
		}

		public static Builder builder(LogConfig config) {
			return new Builder(config);
		}

		public class Builder extends LevelResolver.AbstractBuilder<Builder> {

			private final LogConfig config;

			private Builder(LogConfig config) {
				this.config = config;
			}

			private LogPublisher publisher;

			@Override
			protected Builder self() {
				return this;
			}

			public Builder publisher(LogPublisher publisher) {
				this.publisher = publisher;
				return self();
			}

			public Builder sync(Consumer<LogPublisher.SyncLogPublisher.Builder> consumer) {
				var builder = LogPublisher.SyncLogPublisher.builder(config);
				consumer.accept(builder);
				return publisher(builder.build());
			}

			public Builder async(Consumer<LogPublisher.AsyncLogPublisher.Builder> consumer) {
				var builder = LogPublisher.AsyncLogPublisher.builder(config);
				consumer.accept(builder);
				return publisher(builder.build());
			}

			public Route build() {
				var levelResolver = buildLevelResolver(config.levelResolver());
				var publisher = this.publisher;
				if (publisher == null) {
					publisher = LogPublisher.SyncLogPublisher //
						.builder(config) //
						.appender(LogAppender.builder().output(LogOutput.ofStandardOut()).build())
						.build();
				}
				return new SimpleRoute(publisher, levelResolver);
			}

		}

	}

}

record SimpleRoute(LogPublisher publisher, LevelResolver levelResolver) implements Route {

}

record SingleSyncRootRouter(Route route) implements RootRouter {

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return route.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		if (route.isEnabled(event.loggerName(), event.level())) {
			route.log(event);
		}
	}

	@Override
	public void start(LogConfig config) {
		route.publisher().start(config);
	}

	@Override
	public void close() {
		route.publisher().close();
	}

	public LevelResolver levelResolver() {
		return route.levelResolver();
	}

}

record CompositeLogRouter(Route[] routes, LevelResolver levelResolver) implements RootRouter {

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		LogEvent frozen = null;
		for (var r : routes) {
			if (r.isEnabled(event.loggerName(), event.level())) {
				if (frozen != null) {
					r.log(frozen);

				}
				else if (!r.synchronous()) {
					if (frozen == null) {
						frozen = event.freeze();
					}
					r.log(frozen);
				}
				else {
					r.log(event);
				}
			}
		}

	}

	@Override
	public void start(LogConfig config) {
		for (var r : routes) {
			r.publisher().start(config);
		}
	}

	@Override
	public void close() {
		for (var r : routes) {
			r.publisher().close();
		}
	}
}

enum FailsafeAppender implements LogAppender {

	INSTANCE;

	@Override
	public void append(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			event.formattedMessage(System.err);
			var throwable = event.throwable();
			if (throwable != null) {
				throwable.printStackTrace(System.err);
			}
		}
	}

	@Override
	public void close() {
	}

}

enum GlobalLogRouter implements RootRouter {

	INSTANCE;

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private static final LevelResolver INFO_RESOLVER = LevelResolver.of(Level.INFO);

	private volatile @Nullable RootRouter delegate = null;

	private final ReentrantLock drainLock = new ReentrantLock();

	static boolean isShutdownEvent(String loggerName, java.lang.System.Logger.Level level) {
		return loggerName.equals(Defaults.SHUTDOWN) && Boolean.parseBoolean(System.getProperty(Defaults.SHUTDOWN));
	}

	@Override
	public LevelResolver levelResolver() {
		RootRouter d = delegate;
		if (d != null) {
			return d.levelResolver();
		}
		return INFO_RESOLVER;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		LogRouter d = delegate;
		if (d != null) {
			return d.isEnabled(loggerName, level);
		}
		return INFO_RESOLVER.isEnabled(loggerName, level);
	}

	public void log(String loggerName, java.lang.System.Logger.Level level, String message, @Nullable Throwable cause) {
		RootRouter d = delegate;
		if (d != null) {
			d.log(loggerName, level, message, cause);
			if (isShutdownEvent(loggerName, level)) {
				d.close();
			}
		}
		else {
			var event = LogEvent.of(level, loggerName, message, cause);
			events.add(event);
			if (event.level().getSeverity() >= Level.ERROR.getSeverity()) {
				Errors.error(event);
			}
		}
	}

	public void drain(RootRouter delegate) {
		drainLock.lock();
		try {
			this.delegate = Objects.requireNonNull(delegate);
			LogEvent e;
			while ((e = this.events.poll()) != null) {
				if (delegate.isEnabled(e.loggerName(), e.level())) {
					delegate.log(e);
				}
			}
		}
		finally {
			drainLock.unlock();
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
