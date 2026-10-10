package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class Json5FormatterTest {

	static final Instant TIME = Instant.parse("2026-10-05T15:04:05.123Z");

	static final String PREFIX = "{\"time\":\"2026-10-05T15:04:05.123Z\",\"level\":\"INFO\",\"logger\":\"com.example.App\","
			+ "\"thread\":\"main\",\"msg\":\"hello world\"";

	private static LogEvent event(String message, KeyValues keyValues, @Nullable Throwable throwable) {
		return LogEvent.of(TIME, "main", 1, Level.INFO, "com.example.App", message, keyValues, throwable);
	}

	private static KeyValues requestKeyValues() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "Ada Lovelace");
		return kvs;
	}

	private static String format(LogFormatter formatter, LogEvent event) {
		var sb = new StringBuilder();
		formatter.format(sb, event);
		return sb.toString();
	}

	private static String log(String encoder, String properties, LogEvent event) {
		var output = new ListLogOutput();
		String all = "logging.appenders=list\nlogging.appender.list.output=list\nlogging.appender.list.encoder="
				+ encoder + "\n" + properties;
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(all).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event);
		}
		return output.toString();
	}

	@Test
	void keyValuesAreMergedByDefault() {
		assertEquals(PREFIX + ",\"requestId\":\"42\",\"user\":\"Ada Lovelace\"}\n",
				format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON).build(),
						event("hello world", requestKeyValues(), null)));
	}

	@Test
	void json5IsTheDefaultAndLeavesIdentifierKeysUnquoted() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user name", "Ada");
		assertEquals(
				"{time:\"2026-10-05T15:04:05.123Z\",level:\"INFO\",logger:\"com.example.App\",thread:\"main\","
						+ "msg:\"hello world\",requestId:\"42\",\"user name\":\"Ada\"}\n",
				format(new Json5FormatterBuilder("test").build(), event("hello world", kvs, null)));
	}

	@Test
	void nestedKeyValuesAreAnObjectEvenWhenEmpty() {
		var formatter = new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON)
			.keyValues(KeyValuesPlacement.NESTED)
			.build();
		assertEquals(PREFIX + ",\"keyValues\":{\"requestId\":\"42\",\"user\":\"Ada Lovelace\"}}\n",
				format(formatter, event("hello world", requestKeyValues(), null)));
		assertEquals(PREFIX + ",\"keyValues\":{}}\n", format(formatter, event("hello world", KeyValues.of(), null)));
	}

	@Test
	void noKeyValues() {
		assertEquals(PREFIX + "}\n",
				format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON)
					.keyValues(KeyValuesPlacement.NONE)
					.build(), event("hello world", requestKeyValues(), null)));
	}

	@Test
	void mergedKeyValuesNamedLikeFieldsAreNestedInstead() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("msg", "shadow");
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("level", null);
		assertEquals(PREFIX + ",\"requestId\":\"42\",\"keyValues\":{\"msg\":\"shadow\",\"level\":null}}\n",
				format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON).build(),
						event("hello world", kvs, null)));
	}

	@Test
	void nullValuesAreJsonNull() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("missing", null);
		assertEquals(PREFIX + ",\"missing\":null}\n",
				format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON).build(),
						event("hello world", kvs, null)));
	}

	@Test
	void messageIsEscaped() {
		String actual = format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON)
			.keyValues(KeyValuesPlacement.NONE)
			.build(), event("say \"hi\"\n\tback\\slash \u7530 \uD800", KeyValues.of(), null));
		assertEquals(PREFIX.replace("\"hello world\"", "\"say \\\"hi\\\"\\n\\tback\\\\slash \u7530 \\ud800\"") + "}\n",
				actual);
	}

	@Test
	void throwableAddsErrorAndStacktraceOnOneLine() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		assertEquals(PREFIX + ",\"error\":\"java.lang.IllegalStateException: boom\","
				+ "\"stacktrace\":\"java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\"}\n",
				format(new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON)
					.keyValues(KeyValuesPlacement.NONE)
					.build(), event("hello world", KeyValues.of(), ex)));
	}

	@Test
	void jsonEncoderSchemeAndProperties() {
		assertEquals(PREFIX + ",\"requestId\":\"42\",\"user\":\"Ada Lovelace\"}\n",
				log("json5", "logging.encoder.list.format=json\n", event("hello world", requestKeyValues(), null)));
		assertEquals(
				"{time:\"2026-10-05T15:04:05.123Z\",level:\"INFO\",logger:\"com.example.App\",thread:\"main\","
						+ "msg:\"hello world\",keyValues:{requestId:\"42\",user:\"Ada Lovelace\"}}\n",
				log("json5", "logging.encoder.list.keyValues=nested\n",
						event("hello world", requestKeyValues(), null)));
		assertEquals(PREFIX + "}\n",
				log("json5:///?format=json&keyValues=false", "", event("hello world", requestKeyValues(), null)));
	}

	@Test
	void nonJsonFormatFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.format=percent").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new Json5FormatterBuilder("list").fromProperties(properties).build());
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.format.Json5FormatterBuilder: format=percent is not JSON. Use json or json5.",
				e.getMessage());
	}

	@Test
	void invalidKeyValuesFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.keyValues=flat").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new Json5FormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.Json5FormatterBuilder:
				Error for property. key: 'logging.encoder.list.keyValues' from PROPERTIES_STRING[logging.encoder.list.keyValues], \
				'flat' is not a valid value for io.jstach.rainbowgum.format.KeyValuesPlacement. \
				Valid values: 'merged', 'nested', 'none', 'true', 'false', 'default'""";
		assertEquals(expected, e.getMessage());
	}

	static final String E = "\033[";

	static final String R = E + "0;39m";

	@Test
	void json5ColorsValuesWithTheTheme() {
		var formatter = new Json5FormatterBuilder("test").color(TTLL.ColorMode.FORCE).build();
		String expected = "{time:" + E + "36m\"2026-10-05T15:04:05.123Z\"" + R + ",level:" + E + "1;34m\"INFO\"" + R
				+ ",logger:" + E + "35m\"com.example.App\"" + R + ",thread:" + E + "2;39m\"main\"" + R
				+ ",msg:\"hello\"," + E + "2;39mrequestId:\"42\",user:\"Ada Lovelace\"" + R + "}\n";
		assertEquals(expected, format(formatter, event("hello", requestKeyValues(), null)));
	}

	@Test
	void json5RightPadsTheLevelAfterTheQuote() {
		var formatter = new Json5FormatterBuilder("test").levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build();
		assertEquals(
				"{time:\"2026-10-05T15:04:05.123Z\",level:\"INFO\" ,logger:\"com.example.App\",thread:\"main\","
						+ "msg:\"hello\",requestId:\"42\",user:\"Ada Lovelace\"}\n",
				format(formatter, event("hello", requestKeyValues(), null)));
	}

	@Test
	void json5ColoredAndPaddedKeepsThePaddingOutsideTheColor() {
		var formatter = new Json5FormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build();
		String expected = "{time:" + E + "36m\"2026-10-05T15:04:05.123Z\"" + R + ",level:" + E + "1;34m\"INFO\"" + R
				+ " ,logger:" + E + "35m\"com.example.App\"" + R + ",thread:" + E + "2;39m\"main\"" + R
				+ ",msg:\"hello\"}\n";
		assertEquals(expected, format(formatter, event("hello", KeyValues.of(), null)));
	}

	@Test
	void jsonIgnoresColorThemeAndLevelFormatter() {
		var plain = new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON).build();
		var configured = new Json5FormatterBuilder("test").format(KeyValuesFormatterBuilder.Format.JSON)
			.color(TTLL.ColorMode.FORCE)
			.theme(TTLL.ColorTheme.DARCULA)
			.levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build();
		var event = event("hello", requestKeyValues(), null);
		assertEquals(format(plain, event), format(configured, event));
	}

	@Test
	void levelFormatterAndColorProperties() {
		String expected = "{time:" + E + "2;39m\"2026-10-05T15:04:05.123Z\"" + R + ",level:" + E + "32m\"INFO\"" + R
				+ " ,logger:" + E + "36m\"com.example.App\"" + R + ",thread:" + E + "2;39m\"main\"" + R
				+ ",msg:\"hello\"}\n";
		assertEquals(expected, log("json5", """
				logging.encoder.list.levelFormatter=right_pad_level_formatter
				logging.encoder.list.color=force
				logging.encoder.list.theme=spring
				""", event("hello", KeyValues.of(), null)));
	}

}
