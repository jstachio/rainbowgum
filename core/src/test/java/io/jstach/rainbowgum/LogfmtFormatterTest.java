package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;
import io.jstach.rainbowgum.output.ListLogOutput;

class LogfmtFormatterTest {

	static final Instant TIME = Instant.parse("2026-10-05T15:04:05.123456Z");

	private static LogEvent event(Level level, String thread, String message, KeyValues keyValues,
			@Nullable Throwable throwable) {
		return LogEvent.of(TIME, thread, 1, level, "com.example.App", message, keyValues, throwable);
	}

	private static String format(LogEvent event) {
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").build().format(sb, event);
		return sb.toString();
	}

	@Test
	void plainEvent() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "ada");
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App thread=main msg=hello requestId=42 user=ada\n",
				format(event(Level.INFO, "main", "hello", kvs, null)));
	}

	@Test
	void valuesAreQuotedAndEscapedWhenNeeded() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("missing", null);
		kvs.putKeyValue("path", "C:\\temp");
		kvs.putKeyValue("eq", "a=b");
		kvs.putKeyValue("bell", "\u0007");
		String actual = format(event(Level.WARNING, "pool 1", "say \"hi\"\n\tthere", kvs, null));
		String expected = "time=2026-10-05T15:04:05.123Z level=WARN logger=com.example.App thread=\"pool 1\" "
				+ "msg=\"say \\\"hi\\\"\\n\\tthere\" empty=\"\" missing= path=\"C:\\\\temp\" eq=\"a=b\" "
				+ "bell=\"\\u0007\"\n";
		assertEquals(expected, actual);
	}

	@Test
	void invalidKeyCharactersAreReplaced() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("user name", "ada");
		kvs.putKeyValue("a=b", "c");
		kvs.putKeyValue("q\"k", "v");
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App thread=main msg=x user_name=ada a_b=c q_k=v\n",
				format(event(Level.INFO, "main", "x", kvs, null)));
	}

	@ParameterizedTest
	@CsvSource({ "TRACE,TRACE", "DEBUG,DEBUG", "INFO,INFO", "WARNING,WARN", "ERROR,ERROR" })
	void levelNames(Level level, String expected) {
		assertEquals("time=2026-10-05T15:04:05.123Z level=" + expected + " logger=com.example.App thread=main msg=x\n",
				format(event(level, "main", "x", KeyValues.of(), null)));
	}

	@Test
	void throwableStaysOnOneLine() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		String expected = "time=2026-10-05T15:04:05.123Z level=ERROR logger=com.example.App thread=main msg=failed "
				+ "error=\"java.lang.IllegalStateException: boom\" "
				+ "stacktrace=\"java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\"\n";
		assertEquals(expected, format(event(Level.ERROR, "main", "failed", KeyValues.of(), ex)));
	}

	@Test
	void logfmtEncoderSchemeIsAvailableInCore() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=logfmt
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello world", KeyValues.of(), null));
		}
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App thread=main msg=\"hello world\"\n",
				output.toString());
	}

	@Test
	void logfmtKeyValuesFormatter() {
		var formatter = LogFormatter.builder().text("[").logfmtKeyValues().text("]").build();
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "Ada Lovelace");
		kvs.putKeyValue("missing", null);
		kvs.putKeyValue("empty", "");
		var sb = new StringBuilder();
		formatter.format(sb, event(Level.INFO, "main", "x", kvs, null));
		assertEquals("[requestId=42 user=\"Ada Lovelace\" missing= empty=\"\"]", sb.toString());
	}

	@Test
	void logfmtKeyValuesFormatterWritesNothingWithoutKeyValues() {
		var formatter = LogFormatter.builder().text("[").logfmtKeyValues().text("]").build();
		var sb = new StringBuilder();
		formatter.format(sb, event(Level.INFO, "main", "x", KeyValues.of(), null));
		assertEquals("[]", sb.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "KEEP|[user=ada missing= empty=\"\"]",
			"EMPTY|[user=ada missing=\"\" empty=\"\"]", "SKIP|[user=ada empty=\"\"]" })
	void selectedKeysUseTheNullStrategy(KeyValueNullStrategy strategy, String expected) {
		var formatter = LogFormatter.builder()
			.text("[")
			.logfmtKeyValues(List.of("user", "missing", "empty"), strategy)
			.text("]")
			.build();
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("user", "ada");
		kvs.putKeyValue("ignored", "x");
		var sb = new StringBuilder();
		formatter.format(sb, event(Level.INFO, "main", "x", kvs, null));
		assertEquals(expected, sb.toString());
	}

	@Test
	void selectedKeysDefaultToKeep() {
		var formatter = LogFormatter.builder().logfmtKeyValues(List.of("missing", "user")).build();
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("user", "ada");
		var sb = new StringBuilder();
		formatter.format(sb, event(Level.INFO, "main", "x", kvs, null));
		assertEquals("missing= user=ada", sb.toString());
	}

	@Test
	void builderTakesALevelFormatter() {
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build()
			.format(sb, event(Level.INFO, "main", "x", KeyValues.of(), null));
		assertEquals("time=2026-10-05T15:04:05.123Z level=INFO  logger=com.example.App thread=main msg=x\n",
				sb.toString());
	}

	@Test
	void levelFormatterProperty() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=logfmt
				logging.encoder.list.levelFormatter=right_pad_level_formatter
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.WARNING, "main", "x", KeyValues.of(), null));
		}
		assertEquals("time=2026-10-05T15:04:05.123Z level=WARN  logger=com.example.App thread=main msg=x\n",
				output.toString());
	}

	@Test
	void invalidLevelFormatterPropertyFails() {
		var properties = LogProperties.builder().fromProperties("logging.encoder.list.levelFormatter=lower").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LogfmtFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogfmtFormatterBuilder:
				Error for property. key: 'logging.encoder.list.levelFormatter' from PROPERTIES_STRING[logging.encoder.list.levelFormatter], \
				'lower' is not a valid value for io.jstach.rainbowgum.DefaultLevelFormatter. \
				Valid values: 'level_formatter', 'right_pad_level_formatter'""";
		assertEquals(expected, e.getMessage());
	}

}
