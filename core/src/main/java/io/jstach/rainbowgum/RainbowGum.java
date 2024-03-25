package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

//@formatter:off
/**
 * The main entry point and configuration of RainbowGum logging.
 * <p>
 * RainbowGum logging loads configuration through the service loader. While you can
 * manually set RainbowGum using {@link #set(Supplier)} it is better to register
 * implementations through the ServiceLoader so that RainbowGum will load prior to any
 * external logging.
 * <p>
 * To register a custom RainbowGum:
 *
 *
{@snippet class="snippets.RainbowGumProviderExample" region="provider" :

class RainbowGumProviderExample implements RainbowGumProvider {

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {

		Property<Integer> bufferSize = Property.builder()
			.map(Integer::parseInt)
			.orElse(1024)
			.build("logging.custom.async.bufferSize");

		Property<Provider<LogOutput>> output = Property.builder()
			.map(URI::create)
			.map(u -> LogOutput.of(u))
			.orElse(LogOutput.ofStandardOut())
			.build("logging.custom.output");

		var gum = RainbowGum.builder() //
			.route(r -> {
				r.publisher(PublisherFactory.async().bufferSize(bufferSize.get(config.properties()).value()).build());
				r.appender("console", a -> {
					a.output(output.get(config.properties()).value());					
				});
				r.level(Level.INFO);
			})
			.build();

		return Optional.of(gum);
	}

}
}
 */
//@formatter:on
@SuppressWarnings("InvalidInlineTag")
public sealed interface RainbowGum extends AutoCloseable, LogEventLogger {

	/**
	 * Gets the currently statically bound RainbowGum and will try to load and find one if
	 * there is none currently bound. <strong> This is a blocking operation as in locks
	 * are used and will block indefinitely till a gum has loaded. </strong> If that is
	 * not desired see {@link #getOrNull()}.
	 * @return current RainbowGum or new loaded one.
	 */
	public static RainbowGum of() {
		return RainbowGumHolder.get();
	}

	/**
	 * Gets the currently statically bound RainbowGum or <code>null</code> if none are
	 * <strong>finished binding</strong>. Unlike {@link #of()} this will never load or
	 * start an instance but rather just gets the currently bound one. It will also never
	 * block and does not wait if one is currently being loaded.
	 * @return current RainbowGum or <code>null</code>
	 */
	public static @Nullable RainbowGum getOrNull() {
		return RainbowGumHolder.current();
	}

	/**
	 * Provides the service loader default based RainbowGum.
	 * @return RainbowGum.
	 */
	public static RainbowGum defaults() {
		return RainbowGumServiceProvider.provide();
	}

	/**
	 * Sets the global default RainbowGum. The supplied {@link RainbowGum} must not
	 * already be started.
	 * @param supplier the supplier will be memoized when {@linkplain Supplier#get()
	 * accessed} and {@link RainbowGum#start()} will be called.
	 */
	public static void set(Supplier<RainbowGum> supplier) {
		RainbowGumHolder.set(supplier);
	}

	/**
	 * The config associated with this instance.
	 * @return config.
	 */
	public LogConfig config();

	/**
	 * The router that will route log messages to publishers.
	 * @return router
	 */
	public RootRouter router();

	/**
	 * Starts the rainbow gum and returns it. It is returned for try-with usage
	 * convenience.
	 * @return the started RainbowGum.
	 */
	default RainbowGum start() {
		router().start(config());
		return this;
	}

	/**
	 * Unique id of rainbow gum instance.
	 * @return random id created on creation.
	 */
	public UUID instanceId();

	/**
	 * Will close the RainbowGum and all registered components as well as removed from the
	 * shutdown hooks. If the rainbow gum is set as global it will no longer be global and
	 * replaced with the bootstrapping in memory queue. {@inheritDoc}
	 */
	@Override
	public void close();

	/**
	 * This append call is mainly for testing as it does not avoid making events that do
	 * not need to be made if no logging needs to be done. {@inheritDoc}
	 */
	@Override
	default void log(LogEvent event) {
		var r = router().route(event.loggerName(), event.level());
		if (r.isEnabled()) {
			r.log(event);
		}
	}

	/**
	 * Use to build a custom {@link RainbowGum} which will use the {@link LogConfig}
	 * provided by the service loader.
	 * @return builder.
	 */
	public static Builder builder() {
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);
		var config = RainbowGumServiceProvider.provideConfig(loader);
		return builder(config);
	}

	/**
	 * Use to build a custom {@link RainbowGum} with supplied config.
	 * @param config the config
	 * @return builder.
	 * @see #builder()
	 */
	public static Builder builder(LogConfig config) {
		return new Builder(config);
	}

	/**
	 * Use to build a custom {@link RainbowGum} with supplied config.
	 * @param config consumer that has first argument as config builder.
	 * @return builder.
	 * @see #builder()
	 * @apiNote this method is for ergonomic fluent reasons.
	 */
	public static Builder builder(Consumer<? super LogConfig.Builder> config) {
		var b = LogConfig.builder();
		config.accept(b);
		return builder(b.build());
	}

	/**
	 * RainbowGum Builder.
	 */
	public class Builder {

		private final LogConfig config;

		private final List<Router> routes = new ArrayList<>();

		private Builder(LogConfig config) {
			this.config = config;
		}

		/**
		 * Adds a router.
		 * @param route a router.
		 * @return builder.
		 */
		public Builder route(Router route) {
			this.routes.add(route);
			return this;
		}

		/**
		 * Adds a route by using a consumer of the route builder.
		 * @param name name of router.
		 * @param consumer consumer is passed router builder. The consumer does not need
		 * to call {@link Router#builder(String,LogConfig)}
		 * @return builder.
		 * @see io.jstach.rainbowgum.LogRouter.Router.Builder
		 */
		public Builder route(String name, Consumer<Router.Builder> consumer) {
			var builder = Router.builder(name, config);
			consumer.accept(builder);
			return route(builder.build());
		}

		/**
		 * Adds a route by using a consumer of the route builder.
		 * @param consumer consumer is passed router builder. The consumer does not need
		 * to call {@link Router#builder(String,LogConfig)}
		 * @return builder.
		 * @see io.jstach.rainbowgum.LogRouter.Router.Builder
		 */
		public Builder route(Consumer<Router.Builder> consumer) {
			var builder = Router.builder(Router.DEFAULT_ROUTER_NAME, config);
			consumer.accept(builder);
			return route(builder.build());
		}

		/**
		 * Builds an un-started {@link RainbowGum}.
		 * @return an un-started {@link RainbowGum}.
		 */
		public RainbowGum build() {
			return build(UUID.randomUUID());
		}

		/**
		 * Builds an un-started {@link RainbowGum}.
		 * @param instanceId unique id for rainbow gum instance.
		 * @return an un-started {@link RainbowGum}.
		 */
		private RainbowGum build(UUID instanceId) {
			var routes = this.routes;
			var config = this.config;
			if (routes.isEmpty()) {
				List<String> routeNames = Property.builder() //
					.toList()
					.orElse(List.of())
					.build(LogProperties.ROUTES_PROPERTY)
					.get(config.properties())
					.value();
				if (routeNames.isEmpty()) {
					routes = List.of(Router.builder(Router.DEFAULT_ROUTER_NAME, config).build());
				}
				else {
					routes = routeNames.stream().map(n -> Router.builder(n, config).build()).toList();
				}
			}
			var root = InternalRootRouter.of(routes, config.levelResolver());
			return new SimpleRainbowGum(config, root, instanceId);
		}

		/**
		 * Builds, starts and sets the RainbowGum as the global one picked up by logging
		 * facades.
		 * @return started and set rainbow that can be used in a try-close.
		 */
		public RainbowGum set() {
			UUID instanceId = UUID.randomUUID();
			RainbowGum.set(() -> build(instanceId));
			var gum = RainbowGum.of();
			/*
			 * TODO this is a hack. The holder lock should be used to make this not
			 * happen.
			 */
			if (!instanceId.equals(gum.instanceId())) {
				throw new IllegalStateException("Another rainbow gum registered itself as the global. "
						+ "This is rare reace condition and probably a bug");
			}
			return gum;
		}

		/**
		 * For returning an optional for the Provider contract.
		 * @return optional that always has a rainbow gum.
		 * @apiNote this method is for ergonomics.
		 */
		public Optional<RainbowGum> optional() {
			return Optional.of(this.build());
		}

		/**
		 * For returning an optional for the Provider contract.
		 * @param condition condition to check if this rainbow gum should be used.
		 * @return optional rainbow gum.
		 * @apiNote this method is for ergonomics.
		 */
		public Optional<RainbowGum> optional(Function<? super LogConfig, Boolean> condition) {
			var cond = condition.apply(config);
			if (cond) {
				return Optional.of(this.build());
			}
			return Optional.empty();
		}

	}

}

final class RainbowGumHolder {

	private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private static Supplier<RainbowGum> supplier = RainbowGumServiceProvider::provide;

	private static volatile @Nullable RainbowGum rainbowGum = null;

	static @Nullable RainbowGum current() {
		if (lock.readLock().tryLock()) {
			try {
				return rainbowGum;
			}
			finally {
				lock.readLock().unlock();
			}
		}
		return null;
	}

	static RainbowGum get() {
		lock.readLock().lock();
		try {
			var r = rainbowGum;
			if (r != null) {
				return r;
			}
		}
		finally {
			lock.readLock().unlock();
		}
		if (lock.writeLock().isHeldByCurrentThread()) {
			throw new IllegalStateException("RainbowGum component tried to log too early. "
					+ "This is usually caused by dependencies calling logging.");
		}
		lock.writeLock().lock();
		try {
			var r = rainbowGum;
			if (r != null) {
				return r;
			}
			r = supplier.get();
			start(r);
			rainbowGum = r;
			return r;

		}
		finally {
			lock.writeLock().unlock();
		}

	}

	static boolean remove(RainbowGum gum) {
		lock.writeLock().lock();
		try {
			var original = rainbowGum;
			if (original != gum) {
				return false;
			}
			rainbowGum = null;
			supplier = RainbowGumServiceProvider::provide;
			return true;
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	static void set(Supplier<RainbowGum> rainbowGumSupplier) {
		Objects.requireNonNull(rainbowGumSupplier);
		if (lock.writeLock().isHeldByCurrentThread()) {
			throw new IllegalStateException("RainbowGum component tried to log too early. "
					+ "This is usually caused by dependencies calling logging.");
		}
		lock.writeLock().lock();
		try {
			rainbowGum = null;
			supplier = rainbowGumSupplier;
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	private static void start(RainbowGum gum) {
		Objects.requireNonNull(gum);
		ShutdownManager.addShutdownHook(gum);
		gum.start();
		InternalRootRouter.setRouter(gum.router());
	}

}

final class SimpleRainbowGum implements RainbowGum, Shutdownable {

	private final LogConfig config;

	private final RootRouter router;

	private final AtomicInteger state = new AtomicInteger(0);

	private final UUID instanceId;

	private static final int INIT = 0;

	private static final int STARTED = 1;

	private static final int CLOSED = 2;

	public SimpleRainbowGum(LogConfig config, RootRouter router, UUID instanceId) {
		super();
		this.config = config;
		this.router = router;
		this.instanceId = instanceId;
	}

	@Override
	public LogConfig config() {
		return this.config;
	}

	@Override
	public RootRouter router() {
		return this.router;
	}

	@Override
	public RainbowGum start() {
		int current;
		if ((current = state.compareAndExchange(INIT, STARTED)) == INIT) {
			return RainbowGum.super.start();
		}
		throw new IllegalStateException("Cannot start. This rainbowgum is " + stateLabel(current));
	}

	@Override
	public UUID instanceId() {
		return this.instanceId;
	}

	@Override
	public void close() {
		if (state.compareAndSet(STARTED, CLOSED)) {
			RainbowGumHolder.remove(this);
			try {
				shutdown();
			}
			finally {
				ShutdownManager.removeShutdownHook(this);
			}
			return;
		}
	}

	@Override
	public void shutdown() {
		router().close();
	}

	@Override
	public String toString() {
		return "SimpleRainbowGum [instanceId=" + instanceId + ", config=" + config + ", router=" + router + ", state="
				+ stateLabel(state.get()) + "]";
	}

	private static String stateLabel(int state) {
		return switch (state) {
			case INIT -> "created";
			case STARTED -> "started";
			case CLOSED -> "closed";
			default -> {
				throw new IllegalArgumentException("" + state);
			}
		};
	}

}
