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
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.output.ListLogOutput;

class LtsvFormatterTest {

	static final Instant TIME = Instant.parse("2026-10-05T15:04:05.123456Z");

	private static LogEvent event(Level level, String thread, String message, KeyValues keyValues,
			@Nullable Throwable throwable) {
		return LogEvent.of(TIME, thread, 1, level, "com.example.App", message, keyValues, throwable);
	}

	private static String format(LogEvent event) {
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").build().format(sb, event);
		return sb.toString();
	}

	@Test
	void plainEvent() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "ada");
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmsg:hello world\trequestId:42\tuser:ada\n",
				format(event(Level.INFO, "main", "hello world", kvs, null)));
	}

	@Test
	void valuesAreEscapedNotQuoted() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("empty", "");
		kvs.putKeyValue("missing", null);
		kvs.putKeyValue("path", "C:\\temp");
		kvs.putKeyValue("colon", "a:b");
		kvs.putKeyValue("bell", "\u0007");
		String actual = format(event(Level.WARNING, "pool 1", "say \"hi\"\n\tthere\r", kvs, null));
		String expected = "time:2026-10-05T15:04:05.123Z\tlevel:WARN\tlogger:com.example.App\tthreadId:1\tthreadName:pool 1\t"
				+ "msg:say \"hi\"\\n\\tthere\\r\tempty:\tmissing:\tpath:C:\\\\temp\tcolon:a:b\tbell:\\u0007\n";
		assertEquals(expected, actual);
	}

	@Test
	void keyValuesNamedLikeFieldsArePrefixed() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("msg", "m2");
		kvs.putKeyValue("level", "L");
		kvs.putKeyValue("keyValues.ok", "k");
		kvs.putKeyValue("ok", "1");
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\t"
						+ "msg:real\tkeyValues.msg:m2\tkeyValues.level:L\tkeyValues.keyValues.ok:k\tok:1\n",
				format(event(Level.INFO, "main", "real", kvs, null)));
	}

	@Test
	void invalidLabelCharactersArePercentEncoded() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("user name", "ada");
		kvs.putKeyValue("a:b", "c");
		kvs.putKeyValue("tab\tkey", "t");
		kvs.putKeyValue("100%", "p");
		kvs.putKeyValue("tilde~", "s");
		kvs.putKeyValue("caf\u00e9", "u");
		kvs.putKeyValue("\uD83C\uDF33", "tree");
		kvs.putKeyValue("", "3");
		kvs.putKeyValue("a_b.c-D9", "ok");
		assertEquals("time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\t"
				+ "msg:x\tuser%20name:ada\ta%3Ab:c\ttab%09key:t\t100%25:p\ttilde%7E:s\tcaf%C3%A9:u\t%F0%9F%8C%B3:tree"
				+ "\t%:3\ta_b.c-D9:ok\n", format(event(Level.INFO, "main", "x", kvs, null)));
	}

	@ParameterizedTest
	@CsvSource({ "TRACE,TRACE", "DEBUG,DEBUG", "INFO,INFO", "WARNING,WARN", "ERROR,ERROR" })
	void levelNames(Level level, String expected) {
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:" + expected
						+ "\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmsg:x\n",
				format(event(level, "main", "x", KeyValues.of(), null)));
	}

	@Test
	void throwableStaysOnOneLine() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		String expected = "time:2026-10-05T15:04:05.123Z\tlevel:ERROR\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmsg:failed"
				+ "\terror:java.lang.IllegalStateException: boom"
				+ "\tstacktrace:java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\n";
		assertEquals(expected, format(event(Level.ERROR, "main", "failed", KeyValues.of(), ex)));
	}

	@Test
	void ltsvEncoderSchemeIsAvailableInCore() {
		var output = new ListLogOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output.uri=list
				logging.appender.list.encoder.uri=ltsv
				""";
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello world", KeyValues.of(), null));
		}
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmsg:hello world\n",
				output.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"name|thread:main", //
			"id|threadId:1", //
			"both|threadId:1\tthreadName:main", //
			"none|" })
	void threadProperty(String thread, @Nullable String fields) {
		String properties = "logging.appenders=list\nlogging.appender.list.output.uri=list\n"
				+ "logging.appender.list.encoder.uri=ltsv\nlogging.appender.list.encoder.ltsv.thread=" + thread + "\n";
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello", KeyValues.of(), null));
		}
		assertEquals("time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\t"
				+ (fields == null ? "" : fields + "\t") + "msg:hello\n", output.toString());
	}

	@Test
	void keyValuesNamedLikeTheChosenThreadFieldsArePrefixed() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("thread", "t");
		kvs.putKeyValue("threadId", "i");
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").thread(ThreadFields.ID)
			.build()
			.format(sb, event(Level.INFO, "main", "hello", kvs, null));
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tmsg:hello\tthread:t\tkeyValues.threadId:i\n",
				sb.toString());
	}

	@Test
	void invalidThreadPropertyFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ltsv.thread=group")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LtsvFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.LtsvFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ltsv.thread' from PROPERTIES_STRING[logging.appender.list.encoder.ltsv.thread], \
				'group' is not a valid value for io.jstach.rainbowgum.format.ThreadFields. \
				Valid values: 'name', 'id', 'both', 'none', 'true', 'false', 'default'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void keyValuesLabelsLeavesTheFixedFieldsUnlabeled() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("msg", "m2");
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").labels(LtsvLabels.KEY_VALUES)
			.build()
			.format(sb, event(Level.ERROR, "", "failed: a:b", kvs, ex));
		String expected = "2026-10-05T15:04:05.123Z\tERROR\tcom.example.App\t1\t\tfailed: a:b\trequestId:42\tkeyValues.msg:m2"
				+ "\terror:java.lang.IllegalStateException: boom"
				+ "\tstacktrace:java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\n";
		assertEquals(expected, sb.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"all|time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthread:main\tmsg:hello", //
			"true|time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthread:main\tmsg:hello", //
			"key_values|2026-10-05T15:04:05.123Z\tINFO\tcom.example.App\tmain\thello", //
			"false|2026-10-05T15:04:05.123Z\tINFO\tcom.example.App\tmain\thello", //
			"keyValues|2026-10-05T15:04:05.123Z\tINFO\tcom.example.App\tmain\thello", //
			"KEYVALUES|2026-10-05T15:04:05.123Z\tINFO\tcom.example.App\tmain\thello" })
	void labelsProperty(String labels, String line) {
		String properties = "logging.appenders=list\nlogging.appender.list.output.uri=list\n"
				+ "logging.appender.list.encoder.uri=ltsv\nlogging.appender.list.encoder.ltsv.thread=name\n"
				+ "logging.appender.list.encoder.ltsv.labels=" + labels + "\n";
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello", KeyValues.of(), null));
		}
		assertEquals(line + "\n", output.toString());
	}

	@Test
	void invalidLabelsPropertyFails() {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ltsv.labels=none")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LtsvFormatterBuilder("list").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.format.LtsvFormatterBuilder:
				Error for property. key: 'logging.appender.list.encoder.ltsv.labels' from PROPERTIES_STRING[logging.appender.list.encoder.ltsv.labels], \
				'none' is not a valid value for io.jstach.rainbowgum.format.LtsvLabels. \
				Valid values: 'all', 'key_values', 'true', 'false', 'keyvalues'""";
		assertEquals(expected, e.getMessage());
	}

	static final String E = "\033[";

	static final String R = E + "0;39m";

	@Test
	void colorsValuesWithTheTheme() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.build()
			.format(sb, event(Level.INFO, "pool 1", "hello world", kvs, null));
		String expected = "time:" + E + "36m2026-10-05T15:04:05.123Z" + R + "\tlevel:" + E + "1;34mINFO" + R
				+ "\tlogger:" + E + "35mcom.example.App" + R + "\tthreadId:" + E + "2;39m1" + R + "\tthreadName:" + E
				+ "2;39mpool 1" + R + "\tmsg:hello world\t" + E + "2;39mrequestId:42" + R + "\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void errorAndStacktraceTakeTheLevelColor() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.labels(LtsvLabels.KEY_VALUES)
			.thread(ThreadFields.NONE)
			.build()
			.format(sb, event(Level.ERROR, "main", "failed", KeyValues.of(), ex));
		String expected = E + "36m2026-10-05T15:04:05.123Z" + R + "\t" + E + "1;31mERROR" + R + "\t" + E
				+ "35mcom.example.App" + R + "\tfailed\terror:" + E + "1;31mjava.lang.IllegalStateException: boom" + R
				+ "\tstacktrace:" + E + "1;31mjava.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)"
				+ R + "\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void colorOffWritesNoEscapes() {
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").color(TTLL.ColorMode.OFF)
			.theme(TTLL.ColorTheme.ONE_DARK)
			.build()
			.format(sb, event(Level.INFO, "main", "x", KeyValues.of(), null));
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmsg:x\n",
				sb.toString());
	}

}
