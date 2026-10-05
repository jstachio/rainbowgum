package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LevelPrefixTest {

	private static String log(String extraProperties, Level level, String message, @Nullable Throwable throwable) {
		var output = new ListLogOutput();
		String properties = """
				logging.level=TRACE
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=pattern
				logging.encoder.list.pattern=%level %msg%n%ex
				""" + extraProperties;
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(LogEventFactory.of("test").eventNoArg(level, message, KeyValues.of(), throwable));
		}
		return output.toString();
	}

	@ParameterizedTest
	@CsvSource({ "ERROR,<3>", "WARNING,<4>", "INFO,<6>", "DEBUG,<7>", "TRACE,<7>" })
	void journaldPrefixIsTheSyslogSeverity(Level level, String prefix) {
		String actual = log("logging.encoder.list.levelPrefix=journald\n", level, "hello", null);
		String expected = prefix + level.getName().replace("WARNING", "WARN") + " hello\n";
		assertEquals(expected, actual);
	}

	@Test
	void everyLineOfAMultiLineEventIsPrefixed() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		String actual = log("logging.encoder.list.levelPrefix=journald\n", Level.ERROR, "first\nsecond", ex);
		String expected = """
				<3>ERROR first
				<3>second
				<3>java.lang.IllegalStateException: boom
				<3>\tat app.Main.run(Main.java:42)
				""";
		assertEquals(expected, actual);
	}

	@Test
	void noPrefixByDefault() {
		assertEquals("ERROR hello\n", log("", Level.ERROR, "hello", null));
		assertEquals("ERROR hello\n", log("logging.encoder.list.levelPrefix=none\n", Level.ERROR, "hello", null));
	}

	@Test
	void builderSetsThePrefix() {
		var output = new ListLogOutput();
		var config = LogConfig.builder().configurator(new PatternConfigurator()).build();
		try (var g = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(output)
				.encoder(
						new PatternEncoderBuilder("list").pattern("%msg%n").levelPrefix(LevelPrefix.JOURNALD).build())))
			.build()
			.start()) {
			g.log(LogEventFactory.of("test").eventNoArg(Level.WARNING, "careful", KeyValues.of(), (Throwable) null));
		}
		assertEquals("<4>careful\n", output.toString());
	}

	@Test
	void invalidLevelPrefixFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.levelPrefix=syslog").build();
		var e = assertThrows(ValidationException.class,
				() -> new PatternEncoderBuilder("list").pattern("%msg").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder:
				Error for property. key: 'logging.encoder.list.levelPrefix' from PROPERTIES_STRING[logging.encoder.list.levelPrefix], \
				'syslog' is not a valid value for io.jstach.rainbowgum.pattern.format.LevelPrefix. Valid values: 'none', 'journald'""";
		assertEquals(expected, e.getMessage());
	}

}
