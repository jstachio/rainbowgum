package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * The main entrypoint and configuration of RainbowGum logging.
 * <p>
 * RainbowGum logging loads configuration through the service loader. While you can
 * manually set RainbowGum using {@link #set(Supplier)} it is better to register
 * implementations through the ServiceLoader so that RainbowGum will load prior to any
 * external logging.
 *
 */
public interface RainbowGum extends AutoCloseable {

	/**
	 * Gets the currently statically bound RainbowGum.
	 * @return current RainbowGum.
	 */
	public static RainbowGum of() {
		return RainbowGumHolder.get();
	}

	/**
	 * Provides the service loader default based RainbowGum.
	 * @return rainbowgum.
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
	 * The config assocated with this instance.
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
	 * @return the started rainbowgum.
	 */
	default RainbowGum start() {
		router().start(config());
		return this;
	}

	default void close() {
		router().close();
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
	 * Use to build a custom {@link RainbowGum}.
	 * @param config the config
	 * @return builder.
	 * @see #builder()
	 */
	public static Builder builder(LogConfig config) {
		return new Builder(config);
	}

	/**
	 * RainbowGum Builder.
	 */
	public class Builder {

		private final LogConfig config;

		private List<Router> routes = new ArrayList<>();

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
		 * @param consumer consumer is passed router builder. The consumer does not need
		 * to call {@link Router#builder(LogConfig)}
		 * @return builder.
		 * @see io.jstach.rainbowgum.LogRouter.Router.Builder
		 */
		public Builder route(Consumer<Router.Builder> consumer) {
			var builder = Router.builder(config);
			consumer.accept(builder);
			return route(builder.build());
		}

		/**
		 * Builds an unstarted {@link RainbowGum}.
		 * @return an unstarted {@link RainbowGum}.
		 */
		public RainbowGum build() {
			var routes = this.routes;
			var config = this.config;
			if (routes.isEmpty()) {
				routes = List.of(Router.builder(config).build());
			}
			var root = InternalRootRouter.of(routes, config.levelResolver());
			return new SimpleRainbowGum(config, root);
		}

	}

}

final class RainbowGumHolder {

	private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	private static Supplier<RainbowGum> supplier = RainbowGumServiceProvider::provide;

	private static volatile RainbowGum rainbowGum = null;

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

	static void set(Supplier<RainbowGum> rainbowGumSupplier) {
		Objects.requireNonNull(rainbowGumSupplier);
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
		Defaults.addShutdownHook(gum);
		InternalRootRouter.setRouter(gum.router());
		gum.start();
	}

}

final class SimpleRainbowGum implements RainbowGum {

	private final LogConfig config;

	private final RootRouter router;

	private final AtomicInteger state = new AtomicInteger(0);

	private static final int INIT = 0;

	private static final int STARTED = 1;

	private static final int CLOSED = 2;

	public SimpleRainbowGum(LogConfig config, RootRouter router) {
		super();
		this.config = config;
		this.router = router;
	}

	public LogConfig config() {
		return this.config;
	}

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
	public void close() {
		if (state.compareAndSet(STARTED, CLOSED)) {
			RainbowGum.super.close();
			return;
		}
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
