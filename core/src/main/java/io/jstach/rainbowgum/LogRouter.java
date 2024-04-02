package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Route;
import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Routes messages to a publisher by providing a {@link Route} from a logger name and
 * level.
 */
public sealed interface LogRouter extends LogLifecycle {

	/**
	 * Finds a route.
	 * @param loggerName topic.
	 * @param level level.
	 * @return route never <code>null</code>.
	 */
	public Route route(String loggerName, java.lang.System.Logger.Level level);

	/**
	 * Creates (or reuses in the case of logging off) an event builder.
	 * @param loggerName logger name of the event.
	 * @param level level that the event should be set to.
	 * @return builder.
	 * @apiNote using the builder is slightly slower and possibly more garbage (if the
	 * builder is not marked for escape analysis) than just manually checking
	 * {@link Route#isEnabled()} and constructing the event using the LogEvent static
	 * "<code>of</code>" factory methods.
	 */
	default LogEvent.Builder eventBuilder(String loggerName, Level level) {
		var route = route(loggerName, level);
		if (route.isEnabled()) {
			return new LogEventBuilder(route, level, loggerName);
		}
		return NoOpLogEventBuilder.NOOP;
	}

	/**
	 * Global router which is always available.
	 * @return global root router.
	 */
	public static RootRouter global() {
		return GlobalLogRouter.INSTANCE;
	}

	/**
	 * A route is similar to a SLF4J Logger or System Logger but has a much simpler
	 * contract.
	 *
	 * The proper usage of Route in most cases is to call {@link #isEnabled()} before
	 * calling {@link #log(LogEvent)}.
	 *
	 * That is {@link #log(LogEvent)} does not do any checking if the event is allowed
	 * furthermore by first checking if {@link #isEnabled()} is true one can decide
	 * whether or not to create a {@link LogEvent}.
	 */
	public interface Route extends LogEventLogger {

		/**
		 * Determines if {@link #log(LogEvent)} maybe called.
		 * @return true if log can be called.
		 */
		public boolean isEnabled();

		/**
		 * Route singletons and utilities.
		 */
		@CaseChanging
		public enum Routes implements Route {

			/**
			 * A route that is a NOOP and is always disabled.
			 */
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

	/**
	 * Router flags for adhoc router customization.
	 */
	public enum RouteFlag {

		/**
		 * The route will not use the global level resolver and will only use the level
		 * config directly on the router.
		 */
		IGNORE_GLOBAL_LEVEL_RESOLVER;

	}

	/**
	 * Root router is a router that has child routers.
	 */
	sealed interface RootRouter extends LogRouter permits InternalRootRouter {

		/**
		 * Level resolver to find levels for a log name.
		 * @return level resolver.
		 */
		public LevelResolver levelResolver();

	}

	/**
	 * Router routes messages to a publisher.
	 */
	sealed interface Router extends LogRouter, LogEventLogger {

		/**
		 * The router name given if no router is explicitly declared.
		 */
		public static final String DEFAULT_ROUTER_NAME = "default";

		/**
		 * Level resolver.
		 * @return level resolver.
		 */
		LevelResolver levelResolver();

		/**
		 * Log publisher.
		 * @return publisher.
		 */
		LogPublisher publisher();

		/**
		 * Whether or not the publisher is synchronous. A synchronous publisher generally
		 * blocks.
		 * @return true if the publisher is synchronous.
		 */
		default boolean synchronous() {
			return publisher().synchronous();
		}

		@Override
		default void log(LogEvent event) {
			publisher().log(event);
		}

		/**
		 * Creates a builder from config.
		 * @param name name of the route which the publisher will inherit.
		 * @param config config.
		 * @return builder.
		 */
		static Builder builder(String name, LogConfig config) {
			return new Builder(name, config);
		}

		/**
		 * Creates a router.
		 *
		 * @see AbstractRouter
		 */
		@FunctionalInterface
		public interface RouterFactory {

			/**
			 * Builder will call this factory if passed in.
			 * @param publisher publisher from builder.
			 * @param levelResolver level resolver from builder.
			 * @param name name from builder.
			 * @param config config from builder.
			 * @return router.
			 */
			public Router create(LogPublisher publisher, LevelResolver levelResolver, String name, LogConfig config);

			/**
			 * Creates a router from a function.
			 * @param function <strong>the function may return <code>null</code></strong>
			 * which indicates to ignore.
			 * @return factory.
			 */
			static RouterFactory of(@SuppressWarnings("exports") Function<LogEvent, @Nullable LogEvent> function) {
				return (pub, lr, n, c) -> new AbstractRouter(n, pub, lr) {
					@Override
					protected @Nullable LogEvent transformOrNull(LogEvent event) {
						return function.apply(event);
					}
				};
			}

		}

		/**
		 * Router builder.
		 */
		public final class Builder extends LevelResolver.AbstractBuilder<Builder> {

			private final LogConfig config;

			private final String name;

			private final EnumSet<RouteFlag> flags = EnumSet.noneOf(RouteFlag.class);

			private RouterFactory factory = (publisher, levelResolver, n, c) -> new SimpleRouter(n, publisher,
					levelResolver);

			private List<LogConfig.Provider<LogAppender>> appenders = new ArrayList<>();

			private Builder(String name, LogConfig config) {
				this.name = Objects.requireNonNull(name);
				this.config = config;
			}

			private @Nullable PublisherFactory publisher;

			@Override
			protected Builder self() {
				return this;
			}

			/**
			 * Adds a flag.
			 * @param flag flag added if not already added.
			 * @return this.
			 */
			public Builder flag(RouteFlag flag) {
				flags.add(flag);
				return this;
			}

			/**
			 * Adds an appender by giving an appender builder to a consumer.
			 * @param name appender name.
			 * @param consumer consumer.
			 * @return this builder.
			 */
			public Builder appender(String name, Consumer<LogAppender.Builder> consumer) {
				var builder = LogAppender.builder(name);
				consumer.accept(builder);
				this.appenders.add(builder.build());
				return this;
			}

			/**
			 * Adds an appender.
			 * @param appender appender provider.
			 * @return this builder.
			 */
			public Builder appender(LogConfig.Provider<LogAppender> appender) {
				this.appenders.add(Objects.requireNonNull(appender));
				return self();
			}

			/**
			 * Sets the publisher. Only one publisher to router.
			 * @param publisher publisher.
			 * @return this builder.
			 */
			public Builder publisher(PublisherFactory publisher) {
				this.publisher = publisher;
				return self();
			}

			/**
			 * Factory to use for creating the router.
			 * @param factory router factory.
			 * @return this.
			 */
			public Builder factory(RouterFactory factory) {
				this.factory = Objects.requireNonNull(factory);
				return this;
			}

			Router build() {
				return build(this.factory);
			}

			/**
			 * Builds the router.
			 * @param factory to create the router.
			 * @return router.
			 */
			Router build(RouterFactory factory) {
				String name = this.name;
				String routerLevelPrefix = LogProperties.interpolateNamedKey(LogProperties.ROUTE_LEVEL_PREFIX, name);
				var levelResolverBuilder = LevelResolver.builder();
				var currentConfig = buildLevelConfigOrNull();
				if (currentConfig != null) {
					levelResolverBuilder.config(currentConfig);
				}
				levelResolverBuilder.config(config.properties(), routerLevelPrefix);
				if (!flags.contains(RouteFlag.IGNORE_GLOBAL_LEVEL_RESOLVER)) {
					levelResolverBuilder.config(config.levelResolver());
				}
				var levelResolver = levelResolverBuilder.build();
				if (currentConfig == null) {
					/*
					 * If no config is provided in this route the global level might not
					 * be set.
					 */
					var level = levelResolver.resolveLevel("");
					Objects.requireNonNull(level);
					if (level == System.Logger.Level.ALL) {
						/*
						 * The global root level was not set or set to ALL. This is a bug
						 * if this happens as the builders and other places will turn ALL
						 * -> TRACE.
						 */
						levelResolverBuilder.config(StaticLevelResolver.INFO);
						levelResolver = levelResolverBuilder.build();
						// throw new IllegalStateException("Global Level Resolver should
						// not resolve to Level.ALL");
					}
				}

				var _levelResolver = levelResolver;
				/*
				 * This routers level resolver needs to be notified if config changes.
				 */
				config.changePublisher().subscribe(c -> {
					_levelResolver.clear();
				});
				var publisher = this.publisher;

				List<LogConfig.Provider<LogAppender>> appenders = new ArrayList<>(this.appenders);
				if (appenders.isEmpty()) {

					List<String> appenderNames = Property.builder() //
						.toList() //
						.orElse(List.of())
						.buildWithName(LogProperties.ROUTE_APPENDERS_PROPERTY, name)
						.get(config.properties())
						.value();

					DefaultAppenderRegistry.defaultAppenders(config, appenderNames) //
						.stream() //
						.forEach(appenders::add);
				}

				if (publisher == null) {
					publisher = Property.builder() //
						.toURI() //
						.map(u -> config.publisherRegistry().provide(u, name, config.properties()))
						.buildWithName(LogProperties.ROUTE_PUBLISHER_PROPERTY, name)
						.get(config.properties())
						.value(() -> LogPublisher.SyncLogPublisher.builder().build());
				}

				var apps = appenders.stream().map(a -> a.provide(name, config)).toList();
				var pub = publisher.create(name, config, apps);

				return factory.create(pub, levelResolver, name, config);
			}

		}

	}

	/**
	 * A user supplied router usually for filtering or transforming purposes. The method
	 * required to implement is {@link #transformOrNull(LogEvent)}.
	 *
	 * @see RouterFactory
	 */
	@SuppressWarnings("javadoc") // TODO Eclipse bug.
	public non-sealed abstract class AbstractRouter implements Router, Route {

		private final String name;

		private final LogPublisher publisher;

		private final LevelResolver levelResolver;

		/**
		 * Router tuple of publisher and resolver.
		 * @param publisher not <code>null</code>.
		 * @param levelResolver not <code>null</code>.
		 */
		protected AbstractRouter(String name, LogPublisher publisher, LevelResolver levelResolver) {
			super();
			this.name = name;
			this.publisher = publisher;
			this.levelResolver = levelResolver;
		}

		@Override
		public final boolean synchronous() {
			return Router.super.synchronous();
		}

		@Override
		public final void log(LogEvent event) {
			var e = transformOrNull(event);
			if (e != null) {
				Router.super.log(e);
			}
		}

		/**
		 * Implement for custom filtering or transformation.
		 * @param event usually from logging facade.
		 * @return event to be pushed to logger or not if <code>null</code>.
		 */
		protected abstract @Nullable LogEvent transformOrNull(LogEvent event);

		@Override
		public final Route route(String loggerName, Level level) {
			if (levelResolver().isEnabled(loggerName, level)) {
				return this;
			}
			return Route.Routes.NotFound;
		}

		@Override
		public void start(LogConfig config) {
			publisher.start(config);
		}

		@Override
		public void close() {
			publisher.close();

		}

		@Override
		public final boolean isEnabled() {
			return true;
		}

		@Override
		public final LevelResolver levelResolver() {
			return this.levelResolver;
		}

		@Override
		public final LogPublisher publisher() {
			return this.publisher;
		}

		@Override
		public String toString() {
			return getClass() + "[name=" + name + ", publisher=" + publisher + ", levelResolver=" + levelResolver + "]";
		}

	}

}

record SimpleRouter(String name, LogPublisher publisher, LevelResolver levelResolver) implements Router, Route {

	@Override
	public void close() {
		publisher.close();
	}

	@Override
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

	@Override
	public void start(LogConfig config) {
		publisher.start(config);
	}

}

sealed interface InternalRootRouter extends RootRouter {

	/**
	 * Sets the root router.
	 * @param router root router.
	 */
	static void setRouter(RootRouter router) {
		if (GlobalLogRouter.INSTANCE == router) {
			return;
		}
		GlobalLogRouter.INSTANCE.drain((InternalRootRouter) router);
	}

	static InternalRootRouter of(List<? extends Router> routes, LevelResolver levelResolver) {

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
				if (matched.add(r)) {
					sorted.add(r);
				}
			}
		}
		for (var r : routes) {
			if (r.synchronous()) {
				if (matched.add(r)) {
					sorted.add(r);
				}
			}
		}
		routes = sorted;
		LevelResolver.Builder resolverBuilder = LevelResolver.builder();
		routes.stream().map(Router::levelResolver).forEach(resolverBuilder::resolver);
		var globalLevelResolver = resolverBuilder.build();

		Router[] array = routes.toArray(new Router[] {});

		if (array.length == 1) {
			var r = array[0];
			return r.synchronous() ? new SingleSyncRootRouter(r) : new SingleAsyncRootRouter(r);
		}
		return new CompositeLogRouter(array, globalLevelResolver);
	}

	default boolean isEnabled(String loggerName, java.lang.System.Logger.Level level) {
		return route(loggerName, level).isEnabled();

	}

	default void drain(InternalRootRouter delegate) {

	}

}

record SingleSyncRootRouter(Router router) implements InternalRootRouter {

	@Override
	public void start(LogConfig config) {
		router.start(config);
	}

	@Override
	public void close() {
		router.close();
	}

	@Override
	public LevelResolver levelResolver() {
		return router.levelResolver();
	}

	@Override
	public Route route(String loggerName, Level level) {
		return router.route(loggerName, level);
	}

}

record SingleAsyncRootRouter(Router router) implements InternalRootRouter {

	@Override
	public void start(LogConfig config) {
		router.start(config);
	}

	@Override
	public void close() {
		router.close();
	}

	@Override
	public LevelResolver levelResolver() {
		return router.levelResolver();
	}

	@Override
	public Route route(String loggerName, Level level) {
		return router.route(loggerName, level);
	}

}

record CompositeLogRouter(Router[] routers, LevelResolver levelResolver) implements InternalRootRouter, Route {

	@Override
	public Route route(String loggerName, Level level) {
		for (var router : routers) {
			if (router.route(loggerName, level).isEnabled()) {
				return this;
			}
		}
		return Routes.NotFound;
	}

	@Override
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

	@Override
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

final class QueueEventsRouter implements InternalRootRouter, Route {

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private static final LevelResolver INFO_RESOLVER = StaticLevelResolver.of(Level.INFO);

	@Override
	public LevelResolver levelResolver() {
		return INFO_RESOLVER;
	}

	@Override
	public Route route(String loggerName, Level level) {
		if (INFO_RESOLVER.isEnabled(loggerName, level)) {
			return this;
		}
		return Routes.NotFound;
	}

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
		events.clear();
	}

	@Override
	public void log(LogEvent event) {
		events.add(event);
		if (event.level() == Level.ERROR) {
			MetaLog.error(event);
		}

	}

	@Override
	public void drain(InternalRootRouter delegate) {
		LogEvent e;
		while ((e = this.events.poll()) != null) {
			delegate.route(e.loggerName(), e.level()).log(e);
		}
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

}

@SuppressWarnings("ImmutableEnumChecker")
enum GlobalLogRouter implements InternalRootRouter, Route {

	INSTANCE;

	private volatile InternalRootRouter delegate = new QueueEventsRouter();

	private final ReentrantLock drainLock = new ReentrantLock();

	static boolean isShutdownEvent(String loggerName, java.lang.System.Logger.Level level) {
		return loggerName.equals(LogLifecycle.SHUTDOWN)
				&& Boolean.parseBoolean(System.getProperty(LogLifecycle.SHUTDOWN));
	}

	@Override
	public LevelResolver levelResolver() {
		return delegate.levelResolver();
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public void log(LogEvent event) {
		InternalRootRouter d = delegate;
		if (d instanceof QueueEventsRouter q) {
			q.log(event);
		}
		else {
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
	}

	@Override
	public Route route(String loggerName, Level level) {
		return this.delegate.route(loggerName, level);
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return this.delegate.isEnabled(loggerName, level);
	}

	@Override
	public void drain(InternalRootRouter delegate) {
		_drain(delegate);
	}

	private InternalRootRouter _drain(InternalRootRouter delegate) {
		drainLock.lock();
		try {
			var original = this.delegate;
			this.delegate = Objects.requireNonNull(delegate);
			original.drain(delegate);
			return original;
		}
		finally {
			drainLock.unlock();
		}
	}

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
		var d = _drain(new QueueEventsRouter());
		d.close();
	}

}
