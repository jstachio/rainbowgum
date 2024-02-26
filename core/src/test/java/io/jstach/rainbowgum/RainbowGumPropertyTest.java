package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
		try (var r = RainbowGum.builder(config).build().start()) {
			var es = test.events();
			for (var e : es) {
				r.log(e);
			}
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			String actual = output.toString();
			assertEquals(test.expected, actual);
		}
	}

	enum _Test {

		ROUTE("""
				logging.routes=error,debug
				logging.route.error.appenders=list
				logging.route.error.level=ERROR
				logging.route.debug.level=DEBUG
				logging.appender.list.output=list
				""", """
				00:00:00.001 [main] ERROR com.pattern.test.Test - hello
				""") {

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

		void config(LogConfig.Builder builder) {

		}

	}

}
