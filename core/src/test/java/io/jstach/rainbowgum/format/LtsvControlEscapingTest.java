package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LtsvControlEscapingTest {

	static Stream<Integer> controls() {
		return IntStream.concat(IntStream.rangeClosed(0x80, 0x9f), IntStream.of(0x2028, 0x2029)).boxed();
	}

	@ParameterizedTest
	@MethodSource("controls")
	void escapesEachC1ControlAndUnicodeLineSeparator(int code) {
		String input = "before" + (char) code + "after";
		var output = new StringBuilder(input);
		LtsvFormatter.escapeInPlace(output, 0);
		assertEquals(reference(input), output.toString());
	}

	@Test
	void allUtf16CodeUnitsKeepValuesAndPrefixUnambiguous() {
		var input = new StringBuilder();
		for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
			input.append((char) c);
		}
		input.append("literal \\u0085 versus \u0085");
		String prefix = "previous:record\t\u001b[32m";
		var output = new StringBuilder(prefix).append(input);
		LtsvFormatter.escapeInPlace(output, prefix.length());
		assertEquals(prefix + reference(input.toString()), output.toString());
		assertTrue(output.length() <= prefix.length() + 6L * input.length());
	}

	@ParameterizedTest
	@EnumSource(WriteMethod.class)
	void escapesUntrustedFieldsAndThrowableThroughTheEncoder(WriteMethod writeMethod) {
		String payload = "a\u0085\u009b8m\u009d52;c;x\u009c\u2028\u2029\u001b[8m\\n\t\n\rz";
		var values = KeyValues.MutableKeyValues.of();
		values.putKeyValue("key\u0085", payload);
		var failure = new IllegalStateException(payload);
		failure.setStackTrace(
				new StackTraceElement[] { new StackTraceElement("class\u009b", "method\u0085", "file\u2028", 42) });
		var event = LogEvent.of(Instant.EPOCH, payload, 1, Level.ERROR, payload, payload, values, failure);
		var output = new ListLogOutput() {
			@Override
			public LogEncoder.BufferHints bufferHints() {
				return writeMethod;
			}
		};
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.appender.app.encoder.uri=ltsv
				logging.appender.app.encoder.ltsv.timestamp=none
				logging.appender.app.encoder.ltsv.level=none
				logging.appender.app.encoder.ltsv.thread=name
				logging.appender.app.encoder.ltsv.color=off
				""").build()).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output)))
			.build()
			.start()) {
			gum.log(event);
		}
		String escaped = reference(payload);
		String expected = "logger:" + escaped + "\tthread:" + escaped + "\tmsg:" + escaped + "\tkey%C2%85:" + escaped
				+ "\terror:" + reference(failure.toString()) + "\tstacktrace:"
				+ reference(failure + "\n\tat class\u009b.method\u0085(file\u2028:42)") + "\n";
		assertEquals(expected, output.toString());
		assertEquals(1, output.events().size());
	}

	@Test
	void trustedFormatterColoursSurviveWhileInjectedSgrDoesNot() {
		String payload = "\u001b[8mhidden\u001b[0m\u009b8m";
		var values = KeyValues.MutableKeyValues.of();
		values.putKeyValue("value", payload);
		var event = LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", payload, values, null);
		var properties = LogProperties.builder().fromProperties("""
				logging.appender.test.encoder.ltsv.timestamp=none
				logging.appender.test.encoder.ltsv.level=none
				logging.appender.test.encoder.ltsv.logger=none
				logging.appender.test.encoder.ltsv.thread=none
				""").build();
		var output = new StringBuilder();
		new LtsvFormatterBuilder("test").fromProperties(properties)
			.color(TTLL.ColorMode.FORCE)
			.build()
			.format(output, event);
		String escaped = "\\u001b[8mhidden\\u001b[0m\\u009b8m";
		assertEquals("msg:" + escaped + "\t\u001b[2;39mvalue:" + escaped + "\u001b[0;39m\n", output.toString());
	}

	// The reference uses the JDK's control classification and number conversion, rather
	// than the production predicate and hexadecimal digit table.
	private static String reference(String value) {
		var output = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> output.append("\\\\");
				case '\t' -> output.append("\\t");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				default -> {
					if (Character.isISOControl(c) || c == '\u2028' || c == '\u2029') {
						String hex = Integer.toHexString(c);
						output.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
					}
					else {
						output.append(c);
					}
				}
			}
		}
		return output.toString();
	}

}
