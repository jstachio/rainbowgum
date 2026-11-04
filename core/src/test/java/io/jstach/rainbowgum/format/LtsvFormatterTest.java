package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.io.PrintWriter;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:hello world\trequestId:42\tuser:ada\n",
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
				+ "message:say \"hi\"\\n\\tthere\\r\tempty:\tmissing:\tpath:C:\\\\temp\tcolon:a:b\tbell:\\u0007\n";
		assertEquals(expected, actual);
	}

	@Test
	void keyValuesNamedLikeFieldsArePrefixed() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("message", "m2");
		kvs.putKeyValue("level", "L");
		kvs.putKeyValue("keyValues.ok", "k");
		kvs.putKeyValue("ok", "1");
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\t"
						+ "message:real\tkeyValues.message:m2\tkeyValues.level:L\tkeyValues.keyValues.ok:k\tok:1\n",
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
				+ "message:x\tuser%20name:ada\ta%3Ab:c\ttab%09key:t\t100%25:p\ttilde%7E:s\tcaf%C3%A9:u\t%F0%9F%8C%B3:tree"
				+ "\t%:3\ta_b.c-D9:ok\n", format(event(Level.INFO, "main", "x", kvs, null)));
	}

	@ParameterizedTest
	@CsvSource({ "TRACE,TRACE", "DEBUG,DEBUG", "INFO,INFO", "WARNING,WARN", "ERROR,ERROR" })
	void levelNames(Level level, String expected) {
		assertEquals(
				"time:2026-10-05T15:04:05.123Z\tlevel:" + expected
						+ "\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:x\n",
				format(event(level, "main", "x", KeyValues.of(), null)));
	}

	@Test
	void throwableStaysOnOneLine() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		String expected = "time:2026-10-05T15:04:05.123Z\tlevel:ERROR\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:failed"
				+ "\terror:java.lang.IllegalStateException: boom"
				+ "\tstacktrace:java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\n";
		assertEquals(expected, format(event(Level.ERROR, "main", "failed", KeyValues.of(), ex)));
	}

	/*
	 * Each line of the stack trace after a tab, keeping the trace's own tabs, while the
	 * message's line break, backslash and tab are escaped as in any value.
	 */
	@Test
	void stackTraceLines() {
		var cause = new RuntimeException("root");
		cause.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Repo", "load", "Repo.java", 7) });
		var ex = new IllegalStateException("bad\nvalue C:\\logs\tx", cause);
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var suppressed = new IllegalArgumentException("close");
		suppressed
			.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Close", "close", "Close.java", 3) });
		ex.addSuppressed(suppressed);
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").stacktrace(LtsvStackTrace.LINES)
			.build()
			.format(sb, event(Level.ERROR, "main", "failed", KeyValues.of(), ex));
		String expected = """
				time:2026-10-05T15:04:05.123Z\tlevel:ERROR\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:failed\
				\terror:java.lang.IllegalStateException: bad\\nvalue C:\\\\logs\\tx\tstacktrace:
				\tjava.lang.IllegalStateException: bad\\nvalue C:\\\\logs\\tx
				\t\tat app.Main.run(Main.java:42)
				\t\tSuppressed: java.lang.IllegalArgumentException: close
				\t\t\tat app.Close.close(Close.java:3)
				\tCaused by: java.lang.RuntimeException: root
				\t\tat app.Repo.load(Repo.java:7)
				""";
		assertEquals(expected, sb.toString());
	}

	@Test
	void stackTraceLinesTakeTheLevelColor() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.stacktrace(LtsvStackTrace.LINES)
			.columns(List.of(EventField.MESSAGE))
			.timestamp(LogFormatter.noop())
			.level(LogFormatter.noop())
			.logger(LogFormatter.noop())
			.thread(ThreadFields.NONE)
			.build()
			.format(sb, event(Level.ERROR, "main", "failed", KeyValues.of(), ex));
		String expected = "failed\terror:" + E + "1;31mjava.lang.IllegalStateException: boom" + R + "\tstacktrace:\n\t"
				+ E + "1;31mjava.lang.IllegalStateException: boom" + R + "\n\t\t" + E
				+ "1;31mat app.Main.run(Main.java:42)" + R + "\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void causeTextMatchingParentFramesDoesNotMergeTheirLines() {
		var frame = new StackTraceElement("app.Main", "run", "Main.java", 42);
		var ex = new RuntimeException("boom");
		ex.setStackTrace(new StackTraceElement[] { frame });
		var cause = new RuntimeException("boom\n\tat " + frame);
		cause.setStackTrace(new StackTraceElement[0]);
		ex.initCause(cause);
		assertEquals("""

				\tjava.lang.RuntimeException: boom
				\t\tat app.Main.run(Main.java:42)
				\tCaused by: java.lang.RuntimeException: boom\\n\\tat app.Main.run(Main.java:42)
				""", traceLines(ex));
	}

	@Test
	void customStackTraceKeepsWriterBoundariesAndUnterminatedText() {
		var ex = new RuntimeException("custom") {
			@Override
			public void printStackTrace(PrintWriter writer) {
				writer.print("custom");
				writer.append('\t').append("a\nb").append('\r');
				writer.println();
				writer.write(new char[] { '!', '\t', '4', '2', '!' }, 1, 3);
				writer.println();
				writer.println((Object) "object\r\nvalue");
				writer.write("skiptrailingtail", 4, 8);
			}
		};
		assertEquals("""

				\tcustom\\ta\\nb\\r
				\t\t42
				\tobject\\r\\nvalue
				\ttrailing
				""", traceLines(ex));
	}

	@Test
	void stackTraceLinesDoNotReadThrowableTextAgain() {
		var ex = new RuntimeException() {
			private int calls;

			// Exercise a custom toString that cannot safely be read a third time.
			@Override
			@SuppressWarnings("OverrideThrowableToString")
			public String toString() {
				if (++calls > 2) {
					throw new IllegalStateException("Unexpected third toString call");
				}
				return "render " + calls;
			}
		};
		ex.setStackTrace(new StackTraceElement[0]);
		assertEquals("\n\trender 2\n", traceLines(ex));
	}

	private static String traceLines(Throwable throwable) {
		var output = new StringBuilder();
		new LtsvFormatterBuilder("test").stacktrace(LtsvStackTrace.LINES)
			.build()
			.format(output, event(Level.ERROR, "main", "failed", KeyValues.of(), throwable));
		String marker = "\tstacktrace:";
		return output.substring(output.indexOf(marker) + marker.length());
	}

	@Test
	void emptyLeadingColumnDoesNotLookLikeAContinuation() {
		var ex = new RuntimeException("boom");
		ex.setStackTrace(new StackTraceElement[0]);
		var formatter = new LtsvFormatterBuilder("test").columns(List.of(EventField.MESSAGE))
			.stacktrace(LtsvStackTrace.LINES)
			.build();
		var output = new StringBuilder();
		formatter.format(output, event(Level.ERROR, "main", "failed", KeyValues.of(), ex));
		formatter.format(output, event(Level.INFO, "main", "", KeyValues.of(), null));
		assertEquals(List.of(
				"failed\ttime:2026-10-05T15:04:05.123Z\tlevel:ERROR\tlogger:com.example.App\tthreadId:1\tthreadName:main\terror:java.lang.RuntimeException: boom\tstacktrace:",
				"\\e\ttime:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main"),
				output.toString().lines().filter(line -> !line.startsWith("\t")).toList());
	}

	@Test
	void emptyLeadingColumnEscapeIsDistinctFromLiteralBackslashE() {
		var formatter = new LtsvFormatterBuilder("test").columns(List.of(EventField.MESSAGE))
			.timestamp(LogFormatter.noop())
			.thread(ThreadFields.NONE)
			.level(LogFormatter.noop())
			.logger(LogFormatter.noop())
			.stacktrace(LtsvStackTrace.LINES)
			.build();
		var output = new StringBuilder("previous event\n");
		formatter.format(output, event(Level.INFO, "main", "", KeyValues.of(), null));
		formatter.format(output, event(Level.INFO, "main", "\\e", KeyValues.of(), null));
		assertEquals("previous event\n\\e\n\\\\e\n", output.toString());
	}

	@Test
	void inlineModeKeepsEmptyLeadingColumn() {
		var formatter = new LtsvFormatterBuilder("test").columns(List.of(EventField.MESSAGE))
			.stacktrace(LtsvStackTrace.INLINE)
			.build();
		var output = new StringBuilder();
		formatter.format(output, event(Level.INFO, "main", "", KeyValues.of(), null));
		assertEquals(
				"\ttime:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\n",
				output.toString());
	}

	@Test
	void coloredEmptyLeadingFieldsHaveTheSameFramingAfterStrippingColor() {
		var emptyTimestamp = LogFormatter.of((output, event) -> {
		});
		for (EventField field : List.of(EventField.LOGGER, EventField.THREAD, EventField.TIME)) {
			var formatter = new LtsvFormatterBuilder("test").columns(List.of(field))
				.color(TTLL.ColorMode.FORCE)
				.thread(ThreadFields.NAME)
				.timestamp(emptyTimestamp)
				.build();
			var output = new StringBuilder();
			formatter.format(output, LogEvent.of(TIME, "", 1, Level.INFO, "", "hello", KeyValues.of(), null));
			String plain = output.toString().replaceAll("\u001b\\[[0-9;]*m", "");
			// INLINE is still allowed to have an empty leading column.
			assertEquals('\t', plain.charAt(0));
			var lines = new LtsvFormatterBuilder("test").columns(List.of(field))
				.color(TTLL.ColorMode.FORCE)
				.thread(ThreadFields.NAME)
				.timestamp(emptyTimestamp)
				.stacktrace(LtsvStackTrace.LINES)
				.build();
			output.setLength(0);
			lines.format(output, LogEvent.of(TIME, "", 1, Level.INFO, "", "hello", KeyValues.of(), null));
			assertEquals("\\e" + plain, output.toString().replaceAll("\u001b\\[[0-9;]*m", ""));
		}
	}

	@ParameterizedTest
	@CsvSource({ "lines, LINES", "true, LINES", "inline, INLINE", "default, INLINE", "false, INLINE" })
	void stacktraceProperty(String value, LtsvStackTrace expected) {
		assertEquals(expected, LtsvFormatter.convertStacktrace(value));
	}

	@Test
	void invalidStacktracePropertyFails() {
		var e = assertThrows(IllegalArgumentException.class, () -> LtsvFormatter.convertStacktrace("multiline"));
		assertEquals("'multiline' is not a valid value for io.jstach.rainbowgum.format.LtsvStackTrace. "
				+ "Valid values: 'inline', 'lines', 'default', 'true', 'false'", e.getMessage());
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
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:hello world\n",
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
				+ (fields == null ? "" : fields + "\t") + "message:hello\n", output.toString());
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
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tmessage:hello\tthread:t\tkeyValues.threadId:i\n",
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
	void everyFieldAsAColumn() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("message", "m2");
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").columns(List.of(EventField.values()))
			.build()
			.format(sb, event(Level.ERROR, "", "failed: a:b", kvs, ex));
		String expected = "2026-10-05T15:04:05.123Z\tERROR\tcom.example.App\t1\t\tfailed: a:b\trequestId:42\tkeyValues.message:m2"
				+ "\terror:java.lang.IllegalStateException: boom"
				+ "\tstacktrace:java.lang.IllegalStateException: boom\\n\\tat app.Main.run(Main.java:42)\n";
		assertEquals(expected, sb.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"|time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthread:main\tmessage:hello\trequestId:42", //
			"time,level,logger,thread,msg|2026-10-05T15:04:05.123Z\tINFO\tcom.example.App\tmain\thello\trequestId:42", //
			"thread,msg|main\thello\ttime:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\trequestId:42", //
			"MESSAGE,timestamp|hello\t2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthread:main\trequestId:42" })
	void columnsProperty(@Nullable String columns, String line) {
		String properties = "logging.appenders=list\nlogging.appender.list.output.uri=list\n"
				+ "logging.appender.list.encoder.uri=ltsv\nlogging.appender.list.encoder.ltsv.thread=name\n"
				+ "logging.appender.list.encoder.ltsv.columns=" + (columns == null ? "" : columns) + "\n";
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		try (var g = RainbowGum.builder(config).build().start()) {
			g.log(event(Level.INFO, "main", "hello", kvs, null));
		}
		assertEquals(line + "\n", output.toString());
	}

	@Test
	void emptyFirstColumnKeepsTheColumns() {
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").columns(List.of(EventField.MESSAGE, EventField.LOGGER))
			.thread(ThreadFields.NONE)
			.build()
			.format(sb, event(Level.INFO, "main", "", KeyValues.of(), null));
		assertEquals("\tcom.example.App\ttime:2026-10-05T15:04:05.123Z\tlevel:INFO\n", sb.toString());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = { //
			"msg,logger,msg|columns lists message more than once: [message, logger, message]", //
			"host|'host' is not a valid value for io.jstach.rainbowgum.format.EventField. "
					+ "Valid values: 'time', 'level', 'logger', 'thread', 'message', 'timestamp', 'msg'" })
	void invalidColumnsFail(String columns, String message) {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ltsv.columns=" + columns)
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LtsvFormatterBuilder("list").fromProperties(properties));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.format.LtsvFormatterBuilder:
						Error for property. key: 'logging.appender.list.encoder.ltsv.columns' from PROPERTIES_STRING[logging.appender.list.encoder.ltsv.columns], \
						"""
						+ message,
				e.getMessage());
	}

	private static String formatWith(String properties, LogEvent event) {
		var props = LogProperties.builder()
			.fromProperties(properties.replace("ltsv.", "logging.appender.list.encoder.ltsv."))
			.build();
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("list").fromProperties(props).build().format(sb, event);
		return sb.toString();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '"', value = { //
			"timestamp=ttll|time:15:04:05.123", //
			"timestamp=iso|time:2026-10-05T15:04:05.123Z", //
			"timestamp=default|time:2026-10-05T15:04:05.123Z", //
			"timestamp=yyyy-MM-dd HH|time:2026-10-05 15", //
			"level=padded|\"level:INFO \"", //
			"level=bracketed|level:[INFO]", //
			"level=true|level:INFO", //
			"logger=short|logger:App", //
			"logger=default|logger:com.example.App" })
	void fieldFormats(String property, String field) {
		String line = formatWith("ltsv." + property + "\nltsv.thread=none\n",
				event(Level.INFO, "main", "hello", KeyValues.of(), null));
		String label = field.substring(0, field.indexOf(':') + 1);
		int start = line.indexOf(label);
		int end = line.indexOf('\t', start);
		assertEquals(field, line.substring(start, end));
	}

	@Test
	void noneLeavesTheFieldOut() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		assertEquals("com.example.App\thello\tthreadId:1\tthreadName:main\trequestId:42\n",
				formatWith("ltsv.columns=logger,msg\nltsv.timestamp=none\nltsv.level=none\n",
						event(Level.INFO, "main", "hello", kvs, null)));
		assertEquals("time:2026-10-05T15:04:05.123Z\tmessage:hello\n",
				formatWith("ltsv.level=false\nltsv.logger=none\nltsv.thread=none\n",
						event(Level.INFO, "main", "hello", KeyValues.of(), null)));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"timestamp=none|time|columns lists time but timestamp is none", //
			"level=none|level,msg|columns lists level but level is none", //
			"logger=false|msg,logger|columns lists logger but logger is none", //
			"thread=none|thread|columns lists thread but thread is none" })
	void aColumnCannotBeNone(String property, String columns, String message) {
		var properties = LogProperties.builder()
			.fromProperties("logging.appender.list.encoder.ltsv." + property
					+ "\nlogging.appender.list.encoder.ltsv.columns=" + columns)
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new LtsvFormatterBuilder("list").fromProperties(properties).build());
		assertEquals("Validation failed for io.jstach.rainbowgum.format.LtsvFormatterBuilder: " + message,
				e.getMessage());
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
				+ "2;39mpool 1" + R + "\tmessage:hello world\t" + E + "2;39mrequestId:42" + R + "\n";
		assertEquals(expected, sb.toString());
	}

	@Test
	void errorAndStacktraceTakeTheLevelColor() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		var sb = new StringBuilder();
		new LtsvFormatterBuilder("test").color(TTLL.ColorMode.FORCE)
			.columns(List.of(EventField.TIME, EventField.LEVEL, EventField.LOGGER, EventField.MESSAGE))
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
				"time:2026-10-05T15:04:05.123Z\tlevel:INFO\tlogger:com.example.App\tthreadId:1\tthreadName:main\tmessage:x\n",
				sb.toString());
	}

	@Test
	void escapingIsLinearInTheNumberOfEscapes() {
		int count = 1 << 20;
		var sb = new StringBuilder("message:a").append("\n".repeat(count));
		assertTimeout(Duration.ofSeconds(2), () -> LtsvFormatter.escapeInPlace(sb, 8));
		assertEquals("message:a" + "\\n".repeat(count), sb.toString());
	}

}
