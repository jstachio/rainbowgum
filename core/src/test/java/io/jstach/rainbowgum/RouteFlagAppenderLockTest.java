package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogRouter.Router;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * This used to also cover LogRouter.RouteFlag.SHARED_APPENDER_LOCK, which chose between
 * an independent-lock composite and a shared-lock one. That flag - and the shared-lock
 * composite it selected - were removed: appenders always keep their own independent lock
 * now (see AppenderAsModeFlagPermutationTest/AppenderAsModeReentryTest for the "asXXX"
 * level coverage). This one remaining test confirms that still holds end-to-end through
 * a real RainbowGum route with the default publisher, not just when driving
 * LogAppender.Appenders directly.
 */
class RouteFlagAppenderLockTest {

	@Test
	void multipleAppendersOnARouteProduceACompositeAppender() {
		LogConfig config = LogConfig.builder().build();
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
