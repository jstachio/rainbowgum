package io.jstach.rainbowgum;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogRouter.AsyncLogRouter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

public interface RainbowGum {

	public static RainbowGum of() {
		return SimpleRainbowGum.of();
	}

	public static void start(RainbowGum gum) {
		gum.start();
		LogRouter.setRouter(gum.router());
	}

	public LogConfig config();

	public LogRouter router();

	default void start() {
		router().start(config());
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
				router = AsyncLogRouter.builder().appender(LogAppender.builder().build()).build();
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
