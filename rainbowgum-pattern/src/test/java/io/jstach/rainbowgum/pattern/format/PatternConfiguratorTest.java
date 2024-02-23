package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class PatternConfiguratorTest {

	@ParameterizedTest
	@EnumSource(value = _Test.class)
	void test(_Test test) throws Exception {
		String properties = test.properties();
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
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

		FULL("""
				[main] INFO  c.p.test.Test - hello
				[main] WARN  c.p.test.Test - hello
				[main] ERROR c.p.test.Test - hello
				""") {

		};

		private final String expected;

		private _Test(String expected) {
			this.expected = expected;
		}

		String properties() {
			return """
					logging.appenders=console,list
					logging.appender.list.output=list
					logging.appender.list.encoder=pattern
					logging.appender.console.output=stdout
					logging.appender.console.encoder=pattern
					logging.encoder.console.pattern=[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n
					logging.encoder.list.pattern=[%thread] %-5level %logger{15} - %msg%n
					""";
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

	}

}
