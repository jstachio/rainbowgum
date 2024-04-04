package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Builder;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.PatternKey;
import io.jstach.rainbowgum.pattern.format.spi.PatternKeywordProvider;

class PatternConfiguratorTest {

	@ParameterizedTest
	@EnumSource(value = _Test.class)
	void test(_Test test) throws Exception {
		String properties = test.properties();
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.with(test::config)
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			test.events(r.router());
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

		},
		ANSI_DISABLE("""
				[main] INFO  c.p.test.Test - hello
				[main] WARN  c.p.test.Test - hello
				[main] ERROR c.p.test.Test - hello
				""") {
			@Override
			String properties() {
				return """
						logging.appenders=console,list
						logging.appender.list.output=list
						logging.appender.list.encoder=pattern
						logging.appender.console.output=stdout
						logging.appender.console.encoder=pattern
						logging.encoder.console.pattern=[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n
						logging.encoder.list.pattern=[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n
						logging.global.ansi.disable=true
						""";
			}
		},
		CUSTOM_KEYWORD("""
				INFO  blah
				WARN  blah
				ERROR blah
				""") {
			String properties() {
				return """
						logging.appenders=list
						logging.appender.list.output=list
						logging.appender.list.encoder=pattern
						logging.encoder.list.pattern=%-5level %stuff{}%n
						""";
			}

			@Override
			void config(Builder builder) {
				builder.configurator(new PatternKeywordProvider() {
					@Override
					protected void register(PatternRegistry patternRegistry) {
						patternRegistry.keyword(PatternKey.of("stuff"), (c, n) -> LogFormatter.builder().text("blah").build());
					}
				});
			}
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

		void events(LogRouter router) {
			Instant instant = Instant.ofEpochMilli(1);
			for (var level : System.Logger.Level.values()) {
				router.eventBuilder("com.pattern.test.Test", level).message("hello").timestamp(instant).log();
			}
		}

		void config(LogConfig.Builder builder) {

		}

	}

}
