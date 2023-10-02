package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import io.jstach.rainbowgum.LogRouter.ChildLogRouter;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.LogRouter.SyncLogRouter;
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

		private List<ChildLogRouter> routers = new ArrayList<>();

		private Builder(LogConfig config) {
			this.config = config;
		}

		public Builder router(ChildLogRouter router) {
			this.routers.add(router);
			return this;
		}

		public Builder sync(Consumer<LogRouter.SyncLogRouter.Builder> consumer) {
			var builder = LogRouter.SyncLogRouter.builder();
			consumer.accept(builder);
			return router(builder.build());
		}

		public Builder async(Consumer<LogRouter.AsyncLogRouter.Builder> consumer) {
			var builder = LogRouter.AsyncLogRouter.builder();
			consumer.accept(builder);
			return router(builder.build());
		}

		public RainbowGum build() {
			var routers = this.routers;
			var config = this.config;
			if (routers.isEmpty()) {
				routers = List.of(SyncLogRouter.builder().appender(LogAppender.builder().build()).build());
			}
			var root = RootRouter.of(routers, config.levelResolver());
			return new SimpleRainbowGum(config, root);
		}

	}

}

record SimpleRainbowGum(LogConfig config, RootRouter router) implements RainbowGum {

	static RainbowGum of() {
		return Holder.rainbowGum;
	}

	private enum Holder {

		;
		private static final RainbowGum rainbowGum = RainbowGumServiceProvider.provide();

	}

}
