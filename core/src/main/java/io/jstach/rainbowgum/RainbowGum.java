package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.SyncLogRouter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public interface RainbowGum extends AutoCloseable {

	public static RainbowGum of() {
		return SimpleRainbowGum.of();
	}

	public static void start(RainbowGum gum) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			gum.config().shutdownHook().run();
			gum.close();
		}));
		LogRouter.setRouter(gum.router());
		gum.start();
	}

	public LogConfig config();

	public LogRouter router();

	default void start() {
		router().start(config());
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

		private List<LogRouter> routers = new ArrayList<>();

		private Builder(LogConfig config) {
			this.config = config;
		}

		public Builder router(LogRouter router) {
			this.routers.add(router);
			return this;
		}

		public Builder synchronous(Consumer<LogRouter.SyncLogRouter.Builder> consumer) {
			var builder = LogRouter.SyncLogRouter.builder();
			consumer.accept(builder);
			return router(builder.build(this.config));
		}

		public Builder asynchronous(Consumer<LogRouter.AsyncLogRouter.Builder> consumer) {
			var builder = LogRouter.AsyncLogRouter.builder();
			consumer.accept(builder);
			return router(builder.build());
		}

		public RainbowGum build() {
			var routers = this.routers;
			var config = this.config;
			if (routers.isEmpty()) {
				routers = List.of(SyncLogRouter.builder().appender(LogAppender.builder().build()).build(this.config));
			}
			var root = RootRouter.of(routers, config.levelResolver());
			return new SimpleRainbowGum(config, root);
		}

	}

}

record SimpleRainbowGum(LogConfig config, LogRouter router) implements RainbowGum {

	static RainbowGum of() {
		return Holder.rainbowGum;
	}

	private enum Holder {

		;
		private static final RainbowGum rainbowGum = RainbowGumServiceProvider.provide();

	}

}
