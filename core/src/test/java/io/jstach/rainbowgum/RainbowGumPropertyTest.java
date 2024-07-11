package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogRouter.Router.RouterFactory;
import io.jstach.rainbowgum.output.ListLogOutput;

class RainbowGumPropertyTest {

	@ParameterizedTest
	@EnumSource(value = _Test.class)
	void test(_Test test) throws Exception {
		String properties = test.properties();
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.with(test::config)
			.build();
		try (var r = test.config(RainbowGum.builder(config)).build().start()) {
			var es = test.events();
			for (var e : es) {
				r.log(e);
			}
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			String actual = output.toString();
			assertEquals(test.expected, actual);
			test.assertOther(r);
		}
	}

	enum _Test {

		ROUTES_PROPERTY("""
				logging.routes=error,debug
				logging.route.error.appenders=list
				logging.route.error.level=ERROR
				logging.route.debug.level=DEBUG
				logging.route.debug.appenders=console
				logging.appender.list.output=list
				""", """
				00:00:00.001 [main] ERROR com.pattern.test.Test - hello
				""") {

		},
		ROUTER_OF_DEBUG_ONLY("""
				logging.appenders=list
				logging.appender.list.output=list
				logging.level=TRACE
				""", """
				00:00:00.001 [main] DEBUG com.pattern.test.Test - hello
				""") {

			@Override
			RainbowGum.Builder config(RainbowGum.Builder builder) {
				return builder.route(rb -> {
					rb.factory(RouterFactory.of(e -> {
						return switch (e.level()) {
							case DEBUG -> e;
							default -> null;
						};
					}));
				});
			}

		},
		REUSE_BUFFER_APPENDER("""
				logging.appenders=list
				logging.appender.list.output=list
				logging.level=ERROR
				logging.appender.list.flags=reuse_buffer
				""", """
				00:00:00.001 [main] ERROR com.pattern.test.Test - hello
				""") {

			@Override
			void assertOther(RainbowGum gum) {
				SingleSyncRootRouter rootRouter = (SingleSyncRootRouter) gum.router();
				var router = rootRouter.router();
				DefaultSyncLogPublisher publisher = (DefaultSyncLogPublisher) router.publisher();
				assertInstanceOf(ReuseBufferLogAppender.class, publisher.appender());
			}

		};

		private final String properties;

		private final String expected;

		private _Test(String properties, String expected) {
			this.properties = properties;
			this.expected = expected;
		}

		String properties() {
			return this.properties;
		}

		List<LogEvent> events() {
			Instant instant = Instant.ofEpochMilli(1);
			List<LogEvent> events = new ArrayList<>();
			for (var level : System.Logger.Level.values()) {
				if (level == System.Logger.Level.OFF) {
					continue;
				}
				var e = LogEvent.of(level, "com.pattern.test.Test", "hello", null).freeze(instant);
				events.add(e);
			}
			return events;
		}

		void assertOther(RainbowGum gum) {

		}

		RainbowGum.Builder config(RainbowGum.Builder builder) {
			return builder;
		}

		void config(LogConfig.Builder builder) {

		}

	}

}
