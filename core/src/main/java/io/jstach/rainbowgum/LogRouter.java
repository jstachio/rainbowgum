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

import io.jstach.rainbowgum.LogAppender.AppenderProvider;
import io.jstach.rainbowgum.LogPublisher.PublisherProvider;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Route;
import io.jstach.rainbowgum.LogRouter.Router;

public sealed interface LogRouter extends AutoCloseable {

	public Route route(String loggerName, java.lang.System.Logger.Level level);

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

	public interface Route extends LogEventLogger {

		public boolean isEnabled();

		public static Route of(List<? extends Route> routes) {
			Route[] array = routes.stream().filter(Route::isEnabled).toList().toArray(new Route[] {});
			if (array.length == 0) {
				return Routes.NotFound;
			}
			if (array.length == 1) {
				return array[0];
			}
			return new CompositeRoute(array);
		}

		public enum Routes implements Route {

			NotFound {
				@Override
				public void log(LogEvent event) {
				}

				@Override
				public boolean isEnabled() {
					return false;
				}
			}

		}

	}

	public sealed interface RootRouter extends LogRouter {

		void start(LogConfig config);

		public LevelResolver levelResolver();

		default boolean isEnabled(String loggerName, java.lang.System.Logger.Level level) {
			return route(loggerName, level).isEnabled();
		}

		default void log(String loggerName, java.lang.System.Logger.Level level, String formattedMessage,
				@Nullable Throwable cause) {
			var route = route(loggerName, level);
			if (route.isEnabled()) {
				LogEvent event = LogEvent.of(level, loggerName, formattedMessage, cause);
				route.log(event);
			}
		}

		default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
			log(loggerName, level, message, null);
		}

		public static RootRouter of(List<? extends Router> routes, LevelResolver levelResolver) {

			if (routes.isEmpty()) {
				throw new IllegalArgumentException("atleast one route is required");
			}
			List<Router> sorted = new ArrayList<>();
			/*
			 * We add the async routers first
			 */
			Set<Router> matched = Collections.newSetFromMap(new IdentityHashMap<Router, Boolean>());
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
			routes.stream().map(Router::levelResolver).forEach(resolvers::add);
			resolvers.add(levelResolver);
			var globalLevelResolver = LevelResolver.of(resolvers);

			Router[] array = routes.toArray(new Router[] {});

			if (array.length == 1 && array[0].synchronous()) {
				return new SingleSyncRootRouter(array[0]);
			}
			return new CompositeLogRouter(array, globalLevelResolver);
		}

	}

	public sealed interface Router extends LogRouter, LogEventLogger {

		LevelResolver levelResolver();

		LogPublisher publisher();

		default boolean synchronous() {
			return publisher().synchronous();
		}

		default void log(LogEvent event) {
			publisher().log(event);
		}

		public static Builder builder(LogConfig config) {
			return new Builder(config);
		}

		public class Builder extends LevelResolver.AbstractBuilder<Builder> {

			private final LogConfig config;
			private List<AppenderProvider> appenders = new ArrayList<>();

			private Builder(LogConfig config) {
				this.config = config;
			}

			private PublisherProvider publisher;

			@Override
			protected Builder self() {
				return this;
			}
			
			public Builder appender(Consumer<LogAppender.Builder> consumer) {
				var builder = LogAppender.builder();
				consumer.accept(builder);
				this.appenders.add(builder.build());
				return this;
			}
			public Builder appender(AppenderProvider appender) {
				this.appenders.add(appender);
				return self();
			}

			public Builder publisher(PublisherProvider publisher) {
				this.publisher = publisher;
				return self();
			}

			Router build() {
				var levelResolver = buildLevelResolver(config.levelResolver());
				var publisher = this.publisher;
				List<AppenderProvider> appenders = new ArrayList<>(this.appenders);
				if (this.appenders.isEmpty()) {
					appenders.add(LogAppender.builder().output(LogOutput.ofStandardOut()).build());
				}
				if (publisher == null) {
					publisher = LogPublisher.SyncLogPublisher //
						.builder() //
						.build();
				}
				
				var apps = this.appenders.stream().map(a -> a.provide(config)).toList();
				var pub = this.publisher.provide(config, apps);
				
				return new SimpleRoute(pub, levelResolver);
			}

		}

	}

}

record CompositeRoute(Route[] routes) implements Route {

	@Override
	public void log(LogEvent event) {
		for (var r : routes) {
			r.log(event);
		}
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}

record SimpleRoute(LogPublisher publisher, LevelResolver levelResolver) implements Router, Route {

	@Override
	public void close() {
		publisher.close();
	}

	public Route route(String loggerName, java.lang.System.Logger.Level level) {
		if (levelResolver().isEnabled(loggerName, level)) {
			return this;
		}
		return Route.Routes.NotFound;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}

record SingleSyncRootRouter(Router router) implements RootRouter {

	@Override
	public void start(LogConfig config) {
		router.start(config);
	}

	@Override
	public void close() {
		router.close();
	}

	public LevelResolver levelResolver() {
		return router.levelResolver();
	}

	@Override
	public Route route(String loggerName, Level level) {
		return router.route(loggerName, level);
	}

}

record CompositeLogRouter(Router[] routers, LevelResolver levelResolver) implements RootRouter, Route {

	@Override
	public Route route(String loggerName, Level level) {
		for (var router : routers) {
			if (router.route(loggerName, level).isEnabled()) {
				return this;
			}
		}
		return Routes.NotFound;
	}

	public void log(LogEvent event) {
		for (var router : routers) {
			var route = router.route(event.loggerName(), event.level());
			if (route.isEnabled()) {
				/*
				 * We assume async routers are earlier in the array.
				 */
				if (!router.synchronous()) {
					/*
					 * Now all events are frozen from here onward to guarantee that the
					 * synchronous routers see the same thing as the async routers.
					 *
					 * Freeze is a noop if it already frozen.
					 */
					event = event.freeze();
				}
				router.log(event);
			}
		}
	}

	public boolean isEnabled() {
		return true;
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

enum GlobalLogRouter implements RootRouter, Route {

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
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void log(LogEvent event) {
		RootRouter d = delegate;
		if (d != null) {
			String loggerName = event.loggerName();
			var level = event.level();
			var route = d.route(event.loggerName(), event.level());
			if (route.isEnabled()) {
				route.log(event);
			}
			if (isShutdownEvent(loggerName, level)) {
				d.close();
			}
		}
		else {
			events.add(event);
			if (event.level().getSeverity() >= Level.ERROR.getSeverity()) {
				Errors.error(event);
			}
		}

	}

	@Override
	public Route route(String loggerName, Level level) {
		RootRouter d = delegate;
		if (d != null) {
			return d.route(loggerName, level);
		}
		if (INFO_RESOLVER.isEnabled(loggerName, level)) {
			return this;
		}
		return Routes.NotFound;
	}

	// @Override
	// public boolean isEnabled(String loggerName, Level level) {
	// RootRouter d = delegate;
	// if (d != null) {
	// return d.isEnabled(loggerName, level);
	// }
	// return INFO_RESOLVER.isEnabled(loggerName, level);
	// }

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
				delegate.route(e.loggerName(), e.level()).log(e);
			}
		}
		finally {
			drainLock.unlock();
		}
	}

	// @Override
	// public void route(LogEvent event) {
	// LogRouter d = delegate;
	// if (d != null) {
	// if (d.isEnabled(event.loggerName(), event.level())) {
	// d.log(event);
	// }
	// }
	// else {
	// events.add(event);
	// }
	// }

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
	}

}
