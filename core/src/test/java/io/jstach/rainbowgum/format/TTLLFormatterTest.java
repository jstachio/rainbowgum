package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class TTLLFormatterTest {

	static final Instant TIME = Instant.parse("2026-10-05T12:00:00.123Z");

	private static LogEvent event(KeyValues keyValues) {
		return LogEvent.of(TIME, "main", 7, Level.INFO, "com.example.App", "hello world", keyValues, null);
	}

	private static KeyValues requestKeyValues() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "Ada Lovelace");
		return kvs;
	}

	private static String format(io.jstach.rainbowgum.LogFormatter formatter, LogEvent event) {
		var sb = new StringBuilder();
		formatter.format(sb, event);
		return sb.toString();
	}

	/*
	 * Logs one event through a gum whose appender uses the given encoder properties.
	 */
	private static String log(String encoder, String properties, LogEvent event) {
		var output = new ListLogOutput();
		String all = "logging.appenders=list\nlogging.appender.list.output=list\n"
				+ (encoder.isEmpty() ? "" : "logging.appender.list.encoder=" + encoder + "\n") + properties;
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event);
		}
		return output.toString();
	}

	@Test
	void defaultsAreTheClassicLayout() {
		assertEquals("12:00:00.123 [main] INFO  com.example.App - hello world\n",
				format(new TTLLFormatterBuilder("test").build(), event(requestKeyValues())));
	}

	@Test
	void defaultsMatchTheStandardEventFormatter() {
		var event = event(requestKeyValues());
		assertEquals(format(StandardEventFormatter.builder().build(), event),
				format(new TTLLFormatterBuilder("test").build(), event));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"timestamp=iso|2026-10-05T12:00:00.123Z [main] INFO  com.example.App - hello world", //
			"timestamp=none|[main] INFO  com.example.App - hello world", //
			"timestamp=HH:mm|12:00 [main] INFO  com.example.App - hello world", //
			"thread=id|12:00:00.123 [7] INFO  com.example.App - hello world", //
			"thread=none|12:00:00.123 INFO  com.example.App - hello world", //
			"level=plain|12:00:00.123 [main] INFO com.example.App - hello world", //
			"level=bracketed|12:00:00.123 [main] [INFO] com.example.App - hello world", //
			"level=none|12:00:00.123 [main] com.example.App - hello world", //
			"logger=short|12:00:00.123 [main] INFO  App - hello world", //
			"logger=none|12:00:00.123 [main] INFO  - hello world", //
			"keyValues=logfmt|12:00:00.123 [main] INFO  com.example.App {requestId=42 user=\"Ada Lovelace\"} - hello world", //
			"keyValues=percent|12:00:00.123 [main] INFO  com.example.App {requestId=42&user=Ada%20Lovelace} - hello world", //
			"keyValues=LOGBACK|12:00:00.123 [main] INFO  com.example.App {requestId=42, user=Ada Lovelace} - hello world" })
	void eachPartIsConfigurableByProperty(String property, String expected) {
		assertEquals(expected + "\n",
				log("ttll", "logging.encoder.list." + property + "\n", event(requestKeyValues())));
	}

	@Test
	void everyPartNoneLeavesTheMessage() {
		String properties = """
				logging.encoder.list.timestamp=none
				logging.encoder.list.thread=none
				logging.encoder.list.level=none
				logging.encoder.list.logger=none
				""";
		assertEquals("hello world\n", log("ttll", properties, event(requestKeyValues())));
	}

	@Test
	void keyValueBracesAreLeftOutWithoutKeyValues() {
		assertEquals("12:00:00.123 [main] INFO  com.example.App - hello world\n",
				log("ttll", "logging.encoder.list.keyValues=logfmt\n", event(KeyValues.of())));
	}

	@Test
	void uriQueryConfiguresTheEncoder() {
		assertEquals("12:00:00.123 [main] INFO  com.example.App {requestId=42 user=\"Ada Lovelace\"} - hello world\n",
				log("ttll:///?keyValues=logfmt", "", event(requestKeyValues())));
	}

	@Test
	void defaultEncoderHonorsTheProperties() {
		assertEquals("12:00:00.123 [main] INFO  App {requestId=42 user=\"Ada Lovelace\"} - hello world\n",
				log("", "logging.encoder.list.keyValues=logfmt\nlogging.encoder.list.logger=short\n",
						event(requestKeyValues())));
	}

	@Test
	void invalidPartNameFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.thread=both").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.encoder.list.thread' from PROPERTIES_STRING[logging.encoder.list.thread], \
				'both' is not a valid value for io.jstach.rainbowgum.format.TTLL.ThreadFormat. Valid values: 'name', 'id', 'none'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void invalidTimestampPatternFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.timestamp=bogus{").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new TTLLFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.TTLLFormatterBuilder:
				Error for property. key: 'logging.encoder.list.timestamp' from PROPERTIES_STRING[logging.encoder.list.timestamp], \
				'bogus{' is neither a timestamp format (ttll, iso, none) nor a valid DateTimeFormatter pattern: \
				Unknown pattern letter: b""";
		assertEquals(expected, e.getMessage());
	}

}
