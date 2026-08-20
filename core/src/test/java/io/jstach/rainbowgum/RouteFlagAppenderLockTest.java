package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.LogRouter.RouteFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

class RouteFlagAppenderLockTest {

	@Test
	void independentLockIsDefault() {
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).route(r -> {
			r.appender("a", a -> a.output(new ListLogOutput()));
			r.appender("b", a -> a.output(new ListLogOutput()));
		}).build().start()) {
			var appender = config.serviceRegistry().findOrNull(LogAppender.class, Router.DEFAULT_ROUTER_NAME);
			if (appender == null) {
				throw new AssertionError("appender should not be null");
			}
			assertInstanceOf(IndependentLockCompositeLogAppender.class, appender);
		}
	}

	@Test
	void sharedAppenderLockFlagUsesSharedLock() {
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).route(r -> {
			r.flag(RouteFlag.SHARED_APPENDER_LOCK);
			r.appender("a", a -> a.output(new ListLogOutput()));
			r.appender("b", a -> a.output(new ListLogOutput()));
		}).build().start()) {
			var appender = config.serviceRegistry().findOrNull(LogAppender.class, Router.DEFAULT_ROUTER_NAME);
			if (appender == null) {
				throw new AssertionError("appender should not be null");
			}
			assertInstanceOf(CompositeLogAppender.class, appender);
		}
	}

	@Test
	void sharedAppenderLockPropertyUsesSharedLock() {
		var props = LogProperties.MutableLogProperties.builder()
			.description("test_props")
			.build()
			.put("logging.route.default.flags", "SHARED_APPENDER_LOCK");
		LogConfig config = LogConfig.builder().properties(props).build();
		try (var gum = RainbowGum.builder(config).route(r -> {
			r.appender("a", a -> a.output(new ListLogOutput()));
			r.appender("b", a -> a.output(new ListLogOutput()));
		}).build().start()) {
			var appender = config.serviceRegistry().findOrNull(LogAppender.class, Router.DEFAULT_ROUTER_NAME);
			if (appender == null) {
				throw new AssertionError("appender should not be null");
			}
			assertInstanceOf(CompositeLogAppender.class, appender);
		}
	}

}
