package io.jstach.rainbowgum;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter.SyncLogRouter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public interface RainbowGum extends AutoCloseable {

	public static RainbowGum of() {
		return SimpleRainbowGum.of();
	}

	public static void start(RainbowGum gum) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
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
		return new Builder();
	}

	public class Builder {

		private LogConfig config = LogConfig.of(System::getProperty);

		private @Nullable LogRouter router;

		private Builder() {
		}

		public Builder router(LogRouter router) {
			this.router = router;
			return this;
		}

		public Builder config(LogConfig config) {
			this.config = config;
			return this;
		}

		public RainbowGum build() {
			var router = this.router;
			var config = this.config;
			if (router == null) {
				// router =
				// AsyncLogRouter.builder().appender(LogAppender.builder().build()).build();
				router = SyncLogRouter.builder().appender(LogAppender.builder().build()).build();
			}

			return new SimpleRainbowGum(config, router);
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
