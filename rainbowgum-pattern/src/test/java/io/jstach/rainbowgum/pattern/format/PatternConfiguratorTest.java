package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Builder;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
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
			String actual = test.actual(config);
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
						patternRegistry.keyword(PatternKey.of("stuff"),
								(c, n) -> LogFormatter.builder().text("blah").build());
					}
				});
			}
		},
		/*
		 * Test the default behavior by replacing the console output.
		 */
		DEFAULT("""
				[36m00:00:00.001[0;39m [2;39m[main][0;39m [34mINFO [0;39m [35mcom.pattern.test.Test[0;39m - hello
				[36m00:00:00.001[0;39m [2;39m[main][0;39m [1;31mWARN [0;39m [35mcom.pattern.test.Test[0;39m - hello
				[36m00:00:00.001[0;39m [2;39m[main][0;39m [1;31mERROR[0;39m [35mcom.pattern.test.Test[0;39m - hello
								""") {

			@Override
			String properties() {
				return """
						logging.pattern.config.console.zoneId=UTC
						""";
			}

			@Override
			void config(Builder builder) {
				builder.configurator((c, p) -> {
					c.outputRegistry().register("stdout", ref -> LogProvider.of(new FakeConsoleOutput()));
					return true;
				});
			}

			String actual(LogConfig config) {
				ListLogOutput output = (ListLogOutput) config.outputRegistry().output("console").orElseThrow();
				System.out.print(output);
				return output.toString();
			}
		};

		static class FakeConsoleOutput extends ListLogOutput {

			@Override
			public OutputType type() {
				return OutputType.CONSOLE_OUT;
			}

		}

		private final String expected;

		private _Test(String expected) {
			this.expected = expected;
		}

		String actual(LogConfig config) {
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			return output.toString();
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
