package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.Route;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public interface RainbowGum extends AutoCloseable {

	public static RainbowGum of() {
		return SimpleRainbowGum.of();
	}

	public static void start(RainbowGum gum) {
		Defaults.addShutdownHook(gum);
		LogRouter.setRouter(gum.router());
		gum.start();
	}

	public LogConfig config();

	public RootRouter router();

	default RainbowGum start() {
		router().start(config());
		return this;
	}

	default void close() {
		router().close();
	}

	public static Builder builder() {
		return builder(Defaults.config.get());
	}

	public static Builder builder(LogConfig config) {
		return new Builder(config);
	}

	public class Builder {

		private final LogConfig config;

		private List<Route> routes = new ArrayList<>();

		private Builder(LogConfig config) {
			this.config = config;
		}

		public Builder route(Route route) {
			this.routes.add(route);
			return this;
		}

		public Builder route(Consumer<Route.Builder> consumer) {
			var builder = Route.builder(config);
			consumer.accept(builder);
			return route(builder.build());
		}

		public RainbowGum build() {
			var routes = this.routes;
			var config = this.config;
			if (routes.isEmpty()) {
				routes = List.of(Route.builder(config).build());
			}
			var root = RootRouter.of(routes, config.levelResolver());
			return new SimpleRainbowGum(config, root);
		}

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

	static RainbowGum of() {
		return Holder.rainbowGum;
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

	private enum Holder {

		;
		private static final RainbowGum rainbowGum = RainbowGumServiceProvider.provide();

	}

}
