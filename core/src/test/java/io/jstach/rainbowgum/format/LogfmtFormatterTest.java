package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
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
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=hello requestId=42 user=ada\n",
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
		String expected = "time=2026-10-05T15:04:05.123Z level=WARN logger=com.example.App threadId=1 threadName=\"pool 1\" "
				+ "msg=\"say \\\"hi\\\"\\n\\tthere\" empty=\"\" missing= path=\"C:\\\\temp\" eq=\"a=b\" "
				+ "bell=\"\\u0007\"\n";
		assertEquals(expected, actual);
	}

	@Test
	void keyValuesNamedLikeFieldsArePrefixed() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("msg", "m2");
		kvs.putKeyValue("level", "L");
		kvs.putKeyValue("keyValues.ok", "k");
		kvs.putKeyValue("ok", "1");
		assertEquals("""
				time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=real \
				keyValues.msg=m2 keyValues.level=L keyValues.keyValues.ok=k ok=1
				""", format(event(Level.INFO, "main", "real", kvs, null)));
	}

	@Test
	void escapedKeysDoNotCollide() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("a b", "1");
		kvs.putKeyValue("a_b", "2");
		kvs.putKeyValue("", "3");
		kvs.putKeyValue("_", "4");
		assertEquals(
				"""
						time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=x a%20b=1 a_b=2 %=3 _=4
						""",
				format(event(Level.INFO, "main", "x", kvs, null)));
	}

	@Test
	void invalidKeyCharactersArePercentEncoded() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("user name", "ada");
		kvs.putKeyValue("a=b", "c");
		kvs.putKeyValue("q\"k", "v");
		kvs.putKeyValue("back\\slash", "w");
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=x user%20name=ada a%3Db=c q%22k=v back%5Cslash=w\n",
				format(event(Level.INFO, "main", "x", kvs, null)));
	}

	@ParameterizedTest
	@CsvSource({ "TRACE,TRACE", "DEBUG,DEBUG", "INFO,INFO", "WARNING,WARN", "ERROR,ERROR" })
	void levelNames(Level level, String expected) {
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=" + expected
						+ " logger=com.example.App threadId=1 threadName=main msg=x\n",
				format(event(level, "main", "x", KeyValues.of(), null)));
	}

	@Test
	void throwableStaysOnOneLine() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		String expected = "time=2026-10-05T15:04:05.123Z level=ERROR logger=com.example.App threadId=1 threadName=main msg=failed "
				+ "error=\"java.lang.IllegalStateException: boom\" "
				+ "stacktrace=\"java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\"\n";
		assertEquals(expected, format(event(Level.ERROR, "main", "failed", KeyValues.of(), ex)));
	}

	@Test
	void logfmtEncoderSchemeIsAvailableInCore() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=logfmt
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello world", KeyValues.of(), null));
		}
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=\"hello world\"\n",
				output.toString());
	}

	@Test
	void builderTakesALevelFormatter() {
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build()
			.format(sb, event(Level.INFO, "main", "x", KeyValues.of(), null));
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO  logger=com.example.App threadId=1 threadName=main msg=x\n",
				sb.toString());
	}

	@Test
	void levelFormatterProperty() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=logfmt
				logging.appender.list.encoder.logfmt.levelFormatter=right_pad_level_formatter
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.WARNING, "main", "x", KeyValues.of(), null));
		}
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=WARN  logger=com.example.App threadId=1 threadName=main msg=x\n",
				output.toString());
	}

	@Test
	void invalidLevelFormatterPropertyFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.logfmt.levelFormatter=lower")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LogfmtFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.LogfmtFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.logfmt.levelFormatter' from PROPERTIES_STRING[logging.appender.list.encoder.logfmt.levelFormatter], \
				'lower' is not a valid value for io.jstach.rainbowgum.format.LogfmtFormatter.LevelFormatterChoice. \
				Valid values: 'level_formatter', 'right_pad_level_formatter'""";
		assertEquals(expected, e.getMessage());
	}

	static final String E = "\033[";

	static final String R = E + "0;39m";

	@Test
	void colorsValuesWithTheTheme() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.build()
			.format(sb, event(Level.INFO, "pool 1", "hello world", kvs, null));
		String expected = "time=" + E + "36m2026-10-05T15:04:05.123Z" + R + " level=" + E + "1;34mINFO" + R + " logger="
				+ E + "35mcom.example.App" + R + " threadId=" + E + "2;39m1" + R + " threadName=" + E
				+ "2;39m\"pool 1\"" + R + " msg=\"hello world\" " + E + "2;39mrequestId=42" + R + "\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void coloredPaddingIsWrittenAfterTheLevelColor() {
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.levelFormatter(LogFormatter.LevelFormatter.ofRightPadded())
			.build()
			.format(sb, event(Level.WARNING, "main", "x", KeyValues.of(), null));
		String expected = "time=" + E + "36m2026-10-05T15:04:05.123Z" + R + " level=" + E + "1;31mWARN" + R
				+ "  logger=" + E + "35mcom.example.App" + R + " threadId=" + E + "2;39m1" + R + " threadName=" + E
				+ "2;39mmain" + R + " msg=x\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void colorOffWritesNoEscapes() {
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").color(TTLL.ColorMode.OFF)
			.theme(TTLL.ColorTheme.ONE_DARK)
			.build()
			.format(sb, event(Level.INFO, "main", "x", KeyValues.of(), null));
		assertEquals(
				"time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 threadName=main msg=x\n",
				sb.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"name|thread=main", //
			"id|threadId=1", //
			"both|threadId=1 threadName=\"pool 1\"", //
			"none|" })
	void threadProperty(String thread, @Nullable String fields) {
		String properties = "logging.appenders=list\nlogging.appender.list.output.uri=list\n"
				+ "logging.appender.list.encoder.uri=logfmt\nlogging.appender.list.encoder.logfmt.thread=" + thread
				+ "\n";
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		String threadName = thread.equals("both") ? "pool 1" : "main";
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, threadName, "hello", KeyValues.of(), null));
		}
		assertEquals("time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App "
				+ (fields == null ? "" : fields + " ") + "msg=hello\n", output.toString());
	}

	@Test
	void keyValuesNamedLikeTheChosenThreadFieldsArePrefixed() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("thread", "t");
		kvs.putKeyValue("threadId", "i");
		var sb = new StringBuilder();
		new LogfmtFormatterBuilder("test").thread(ThreadFields.ID)
			.build()
			.format(sb, event(Level.INFO, "main", "hello", kvs, null));
		assertEquals("""
				time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App threadId=1 msg=hello \
				thread=t keyValues.threadId=i
				""", sb.toString());
	}

}
