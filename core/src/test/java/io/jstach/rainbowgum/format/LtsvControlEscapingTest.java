package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
		return IntStream
			.concat(IntStream.rangeClosed(0x80, 0x9f),
					IntStream
						.concat(IntStream.of(0x2028, 0x2029, 0x890, 0x891),
								IntStream.concat(IntStream.rangeClosed(0x13439, 0x1343f),
										IntStream.rangeClosed(0, Character.MAX_CODE_POINT)
											.filter(c -> Character.getType(c) == Character.FORMAT))))
			.distinct()
			.boxed();
	}

	@ParameterizedTest
	@MethodSource("controls")
	void escapesEachC1ControlSeparatorAndFormatCharacter(int code) {
		String input = "before" + new String(Character.toChars(code)) + "after";
		var output = new StringBuilder(input);
		LtsvFormatter.escapeInPlace(output, 0);
		String expected = IntStream.range(0, Character.charCount(code))
			.mapToObj(i -> String.format("\\u%04x", (int) Character.toChars(code)[i]))
			.collect(Collectors.joining());
		assertEquals("before" + expected + "after", output.toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "plain😀é:quote\"%", "中文/λ", "ordinary text" })
	void preservesOrdinaryUnicodeAndValuePunctuation(String input) {
		var output = new StringBuilder(input);
		LtsvFormatter.escapeInPlace(output, 0);
		assertEquals(input, output.toString());
	}

	@Test
	void preservesMalformedValuesAndLiteralEscapesWithoutCollisions() {
		var input = new StringBuilder("\ud800?\udc00\\ud800\ud83d\ude00\ud834\udd73");
		LtsvFormatter.escapeInPlace(input, 0);
		assertEquals("\\ud800?\\udc00\\\\ud800😀\\ud834\\udd73", input.toString());
	}

	@Test
	void labelsStayDistinctForEveryUtf16UnitAndSentinel() {
		var labels = new HashSet<String>();
		var output = new StringBuilder();
		LtsvFormatter.appendLabel(output, "");
		assertEquals("%", output.toString());
		assertTrue(labels.add(output.toString()));
		for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
			output.setLength(0);
			LtsvFormatter.appendLabel(output, String.valueOf((char) c));
			assertTrue(labels.add(output.toString()), () -> "duplicate label: " + output);
			assertTrue(output.toString().matches("[A-Za-z0-9_.%\\-]+"));
		}
	}

	@Test
	void labelsDocumentUtf8AndMalformedSurrogateExtensions() {
		var output = new StringBuilder();
		LtsvFormatter.appendLabel(output, "a b:%é😀\ud800\udc00\ud800%uD800\udc00");
		assertEquals("a%20b%3A%25%C3%A9%F0%9F%98%80%F0%90%80%80%uD800%25uD800%uDC00", output.toString());
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
		String payload = "a\u0085\u009b8m\u009d52;c;x\u009c\u2028\u2029\u001b[8m\\n\t\n\r\u202e\ud834\udd73\ud800z";
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
				logging.appender.app.encoder.ltsv.time=none
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
		String expected = "logger:" + escaped + "\tthread:" + escaped + "\tmessage:" + escaped + "\tkey%C2%85:"
				+ escaped + "\terror:" + reference(failure.toString()) + "\tstacktrace:"
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
				logging.appender.test.encoder.ltsv.time=none
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
		assertEquals("message:" + escaped + "\t\u001b[2;39mvalue:" + escaped + "\u001b[0;39m\n", output.toString());
	}

	// The reference uses the JDK's control classification and number conversion, rather
	// than the production predicate and hexadecimal digit table.
	private static String reference(String value) {
		var output = new StringBuilder();
		for (int i = 0; i < value.length();) {
			int c = value.codePointAt(i);
			i += Character.charCount(c);
			switch (c) {
				case '\\' -> output.append("\\\\");
				case '\t' -> output.append("\\t");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				default -> {
					if (Character.isISOControl(c) || c == 0x2028 || c == 0x2029
							|| Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE
							|| c == 0x890 || c == 0x891 || (c >= 0x13439 && c <= 0x1343f)) {
						for (char unit : Character.toChars(c)) {
							String hex = Integer.toHexString(unit);
							output.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
						}
					}
					else {
						output.appendCodePoint(c);
					}
				}
			}
		}
		return output.toString();
	}

}
