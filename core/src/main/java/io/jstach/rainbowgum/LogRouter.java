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

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Route;
import io.jstach.rainbowgum.LogRouter.Router;

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
	 * @apiNote using the builder is slightly slower and more garbage than just manually
	 * checking {@link Route#isEnabled()} and constructing the event using the LogEvent
	 * static "<code>of</code>" factory methods.
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
		 * Gets an uncached System Logger.
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
		public static String DEFAULT_ROUTER_NAME = "default";

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
		 * Router builder.
		 */
		public final class Builder extends LevelResolver.AbstractBuilder<Builder> {

			private final LogConfig config;

			private final String name;

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
				this.appenders.add(appender);
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
			 * Builds the router.
			 * @return router.
			 */
			Router build() {
				var levelResolver = buildLevelResolver(config.levelResolver());
				var publisher = this.publisher;
				String name = this.name;

				List<LogConfig.Provider<LogAppender>> appenders = new ArrayList<>(this.appenders);
				if (appenders.isEmpty()) {
					DefaultAppenderRegistry.defaultAppenders(config) //
						.stream() //
						.forEach(appenders::add);
				}
				publisher = Property.builder() //
					.toURI() //
					.map(u -> config.publisherRegistry().provide(u, name, config.properties()))
					.buildWithName(LogProperties.ROUTER_PUBLISHER_PROPERTY, name)
					.get(config.properties())
					.value(() -> LogPublisher.SyncLogPublisher.builder().build());

				var apps = appenders.stream().map(a -> a.provide(name, config)).toList();
				var pub = publisher.create(name, config, apps);

				return new SimpleRouter(pub, levelResolver);
			}

		}

	}

}

record SimpleRouter(LogPublisher publisher, LevelResolver levelResolver) implements Router, Route {

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
			throw new IllegalArgumentException();
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

	public LevelResolver levelResolver() {
		return router.levelResolver();
	}

	@Override
	public Route route(String loggerName, Level level) {
		return router.route(loggerName, level);
	}

	public void log(LogEvent event) {
		router.log(event.freeze());
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
			System.err.append("[ERROR] - logging ");
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
		if (event.level().getSeverity() >= Level.ERROR.getSeverity()) {
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

enum GlobalLogRouter implements InternalRootRouter, Route {

	INSTANCE;

	private volatile InternalRootRouter delegate = new QueueEventsRouter();

	private final ReentrantLock drainLock = new ReentrantLock();

	static boolean isShutdownEvent(String loggerName, java.lang.System.Logger.Level level) {
		return loggerName.equals(Defaults.SHUTDOWN) && Boolean.parseBoolean(System.getProperty(Defaults.SHUTDOWN));
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

	public void log(String loggerName, java.lang.System.Logger.Level level, String message, @Nullable Throwable cause) {
		InternalRootRouter d = delegate;
		d.log(loggerName, level, message, cause);
		if (isShutdownEvent(loggerName, level)) {
			d.close();
		}
	}

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
