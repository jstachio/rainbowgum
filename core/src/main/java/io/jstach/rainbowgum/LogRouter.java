package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNullElse;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
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
	 * Root router is a router that has child routers.
	 */
	sealed interface RootRouter extends LogRouter permits InternalRootRouter {

		/**
		 * Level resolver to find levels for a log name.
		 * @return level resolver.
		 */
		public LevelResolver levelResolver();

		/**
		 * Gets an un-cached System Logger.
		 * @param loggerName logger name usually a class.
		 * @return System Logger.
		 */
		default Logger getLogger(String loggerName) {
			return logger(this, loggerName);
		}

		/**
		 * Low level convenience method for direct logging.
		 * @param loggerName logger name.
		 * @param level level.
		 * @param formattedMessage already formatted message.
		 * @param cause error at event time.
		 */
		default void log(String loggerName, java.lang.System.Logger.Level level, String formattedMessage,
				@Nullable Throwable cause) {
			var route = route(loggerName, level);
			if (route.isEnabled()) {
				LogEvent event = LogEvent.of(level, loggerName, formattedMessage, cause);
				route.log(event);
			}
		}

		private static Logger logger(RootRouter router, String loggerName) {
			if (!(router instanceof InternalRootRouter r)) {
				throw new IllegalArgumentException("bug");
			}
			return new DefaultSystemLogger(loggerName, r);
		}

	}

	/**
	 * Router routes messages to a publisher.
	 */
	sealed interface Router extends LogRouter, LogEventLogger {

		/**
		 * The router name given if no router is explicitely declared.
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
				return (pub, lr, n, c) -> new AbstractRouter(pub, lr) {
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

			private RouterFactory factory = (publisher, levelResolver, n, c) -> new SimpleRouter(publisher,
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
				var routerConfigLevelResolver = ConfigLevelResolver.of(config.properties(), routerLevelPrefix);
				var levelResolver = buildLevelResolver(List.of(routerConfigLevelResolver, config.levelResolver()));
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

		private final LogPublisher publisher;

		private final LevelResolver levelResolver;

		/**
		 * Router tuple of publisher and resolver.
		 * @param publisher not <code>null</code>.
		 * @param levelResolver not <code>null</code>.
		 */
		protected AbstractRouter(LogPublisher publisher, LevelResolver levelResolver) {
			super();
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

	}

}

record SimpleRouter(LogPublisher publisher, LevelResolver levelResolver) implements Router, Route {

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
		List<LevelResolver> resolvers = new ArrayList<>();
		routes.stream().map(Router::levelResolver).forEach(resolvers::add);
		resolvers.add(levelResolver);
		var globalLevelResolver = InternalLevelResolver.cached(InternalLevelResolver.of(resolvers));

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

	default void log(String loggerName, java.lang.System.Logger.Level level, String message) {
		log(loggerName, level, message, null);
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

enum FailsafeAppender implements LogAppender {

	INSTANCE;

	@Override
	public void append(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			var err = System.err;
			if (err != null) {
				err.append("[ERROR] - logging ");
				event.formattedMessage(err);

				var throwable = event.throwableOrNull();
				if (throwable != null) {
					throwable.printStackTrace(err);
				}
			}
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		for (int i = 0; i < count; i++) {
			append(events[i]);
		}
	}

	@Override
	public void close() {
	}

	@Override
	public void start(LogConfig config) {
	}

}

final class QueueEventsRouter implements InternalRootRouter, Route {

	private final ConcurrentLinkedQueue<LogEvent> events = new ConcurrentLinkedQueue<>();

	private static final LevelResolver INFO_RESOLVER = InternalLevelResolver.of(Level.INFO);

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
	public void log(String loggerName, java.lang.System.Logger.Level level, String message, @Nullable Throwable cause) {
		InternalRootRouter d = delegate;
		d.log(loggerName, level, message, cause);
		if (isShutdownEvent(loggerName, level)) {
			d.close();
		}
	}

	@Override
	public void drain(InternalRootRouter delegate) {
		drainLock.lock();
		try {
			var original = this.delegate;
			this.delegate = Objects.requireNonNull(delegate);
			original.drain(delegate);
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
		this.delegate.close();
	}

}

@SuppressWarnings("null") // TODO eclipse bug
class DefaultSystemLogger implements System.Logger {

	private final String name;

	private final InternalRootRouter router;

	public DefaultSystemLogger(String name, InternalRootRouter router) {
		super();
		this.name = name;
		this.router = router;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public boolean isLoggable(Level level) {
		return router.isEnabled(name, level);
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg, @Nullable Throwable thrown) {
		String message = requireNonNullElse(msg, "");
		router.log(name, level, message, thrown);
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
		// TODO Eclipse Null bug
		@NonNull
		String message = requireNonNullElse(format, "");
		String formattedMessage;
		if (params != null && params.length > 0 && !message.isBlank()) {
			formattedMessage = MessageFormat.format(message, params);
		}
		else {
			formattedMessage = message;
		}
		router.log(name, level, formattedMessage, null);
	}

}
