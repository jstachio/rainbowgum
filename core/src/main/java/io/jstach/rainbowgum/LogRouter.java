package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogProperty.Property;
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
	 * Creates a <strong>static</strong> router based on the level. Use a
	 * {@link LevelResolver} to resolve the level first.
	 * @param logger to publish events that are equal or above level.
	 * @param level threshold of the router.
	 * @return immutable static router.
	 * @apiNote This method is for facades that do not have named level methods but are
	 * passed the level on every log method like the System Logger.
	 */
	public static LogRouter ofLevel(LogEventLogger logger, Level level) {
		return new LevelRouter(level, logger);
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
	public sealed interface Route extends LogEventLogger {

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

			};

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

		/**
		 * Register for a router changes usually involving the level resolver being
		 * updated by config.
		 * @param router consumer that will be called with the updated router.
		 * @apiNote its best to use the router in the consumer accept argument and not
		 * this router even if they are usually the same.
		 */
		public void onChange(Consumer<? super RootRouter> router);

		/**
		 * If a logger name configuration is changeable.
		 * @param loggerName logger name.
		 * @return true if config changes are allowed.
		 */
		public boolean isChangeable(String loggerName);

	}

	/**
	 * Router routes messages to a publisher.
	 */
	sealed interface Router extends LogRouter, LogEventLogger {

		/**
		 * The router name given if no router is explicitly declared.
		 */
		public static final String DEFAULT_ROUTER_NAME = LogProperties.DEFAULT_NAME;

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
		public final class Builder extends LevelResolver.AbstractBuilder<Builder> implements LogConfig.ConfigSupport {

			private final LogConfig config;

			private final String name;

			private final EnumSet<RouteFlag> flags = EnumSet.noneOf(RouteFlag.class);

			private RouterFactory factory = (publisher, levelResolver, n, c) -> new SimpleRouter(n, publisher,
					levelResolver);

			private List<LogProvider<LogAppender>> appenders = new ArrayList<>();

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
			 * Gets the currently bound config.
			 * @return config.
			 */
			@Override
			public LogConfig config() {
				return this.config;
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
			public Builder appender(LogProvider<LogAppender> appender) {
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

				List<LogProvider<LogAppender>> appenders = new ArrayList<>(this.appenders);
				if (appenders.isEmpty()) {
					DefaultAppenderRegistry.appenders(config, name) //
						.stream() //
						.forEach(appenders::add);
				}

				if (publisher == null) {
					publisher = Property.builder() //
						.ofProviderRef() //
						.map(r -> config.publisherRegistry().provide(r)) //
						.buildWithName(LogProperties.ROUTE_PUBLISHER_PROPERTY, name)
						.get(config.properties())
						.value(() -> LogPublisher.SyncLogPublisher.builder().build());
				}

				var apps = new LogAppender.Appenders(name, config, appenders);
				var pub = publisher.create(name, config, apps);
				/*
				 * Register the publisher for lookup like status checks.
				 */
				config.serviceRegistry().put(LogPublisher.class, name, pub);
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
		 * @param name router name not <code>null</code>.
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
		public final void start(LogConfig config) {
			publisher.start(config);
		}

		@Override
		public final void close() {
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

record LevelRouter(Level loggerLevel, LogEventLogger logger) implements LogRouter, Route {

	@Override
	public void start(LogConfig config) {
	}

	@Override
	public void close() {
	}

	@Override
	public void log(LogEvent event) {
		logger.log(event);
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public Route route(String loggerName, Level level) {
		if (LevelResolver.checkEnabled(level, loggerLevel)) {
			return this;
		}
		return Routes.NotFound;
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

	static InternalRootRouter of(List<? extends Router> routes, LogConfig config) {

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

		RouteChangePublisher changePublisher = new RouteChangePublisher(
				s -> config.changePublisher().allowedChanges(s).contains(ChangeType.LEVEL));
		if (array.length == 1) {
			var r = array[0];
			return r.synchronous() ? new SingleSyncRootRouter(r, changePublisher)
					: new SingleAsyncRootRouter(r, changePublisher);
		}
		return new CompositeLogRouter(array, globalLevelResolver, changePublisher);
	}

	default boolean isEnabled(String loggerName, java.lang.System.Logger.Level level) {
		return route(loggerName, level).isEnabled();

	}

	default void drain(InternalRootRouter delegate) {

	}

	@Override
	default void onChange(Consumer<? super RootRouter> router) {
		changePublisher().add(router);
	}

	public RouteChangePublisher changePublisher();

	@Override
	default boolean isChangeable(String loggerName) {
		return changePublisher().loggerChangeable.apply(loggerName);
	}

	final class RouteChangePublisher {

		private final Collection<Consumer<? super RootRouter>> consumers = new CopyOnWriteArrayList<Consumer<? super RootRouter>>();

		private final Function<String, Boolean> loggerChangeable;

		RouteChangePublisher(Function<String, Boolean> loggerChangeable) {
			super();
			this.loggerChangeable = loggerChangeable;
		}

		void add(Consumer<? super RootRouter> consumer) {
			consumers.add(consumer);
		}

		void publish(RootRouter router) {
			for (var c : consumers) {
				c.accept(router);
			}
		}

		void transferTo(RouteChangePublisher changePublisher) {
			changePublisher.consumers.addAll(consumers);
			this.consumers.clear();
		}

	}

}

record SingleSyncRootRouter(Router router, RouteChangePublisher changePublisher) implements InternalRootRouter {

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

record SingleAsyncRootRouter(Router router, RouteChangePublisher changePublisher) implements InternalRootRouter {

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

record CompositeLogRouter(Router[] routers, LevelResolver levelResolver,
		RouteChangePublisher changePublisher) implements InternalRootRouter, Route {

	@Override
	public Route route(String loggerName, Level level) {
		/*
		 * The assumption is the provided level resolver is a cached composite level
		 * resolver of all the routers.
		 */
		if (levelResolver.isEnabled(loggerName, level)) {
			return this;
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
				route.log(event);
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

	private final RouteChangePublisher changePublisher = new RouteChangePublisher(s -> true);

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

	@Override
	public RouteChangePublisher changePublisher() {
		return this.changePublisher;
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
	public RouteChangePublisher changePublisher() {
		return this.delegate.changePublisher();
	}

	@Override
	public void drain(InternalRootRouter delegate) {
		_drain(delegate);
	}

	private InternalRootRouter _drain(InternalRootRouter delegate) {
		if (delegate == this) {
			throw new IllegalStateException("bug");
		}
		drainLock.lock();
		try {
			var original = this.delegate;
			if (original instanceof QueueEventsRouter q) {
				q.changePublisher().transferTo(delegate.changePublisher());
				delegate.changePublisher().publish(delegate);
			}
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
