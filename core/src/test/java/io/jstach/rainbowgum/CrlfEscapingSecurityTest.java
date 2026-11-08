package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogFormatter.StandardPostProcessor;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.output.ListLogOutput;

class CrlfEscapingSecurityTest {

	static Stream<String> terminalPayloads() {
		return Stream.of("\u001b[8mhidden\u001b[28m", "\u001b[31mred\u001b[0m", "\u001b[mreset",
				"\u001b[38;2;0;0;0;48;2;0;0;0mhidden", "\u001b[2Jclear\u001b[1;1Hmove",
				"\u001b]52;c;Y2xpcGJvYXJk\u001b\\", "\u001b]0;title\u0007", "\u009b8mhidden",
				"\u001b[" + "1;".repeat(4096), "\u001b[" + "9".repeat(4096) + "m",
				"\\u001b[8m literal\\n\nFORGED\r\t\u0085\u2028\u2029\u007f\u0000");
	}

	@ParameterizedTest
	@MethodSource("terminalPayloads")
	void noTerminalEscapeSequenceSurvives(String payload) {
		var output = new StringBuilder(payload);
		StandardPostProcessor.CRLF.process(event(), output, 0);
		assertEquals(reference(payload), output.toString());
		assertEquals(-1, output.indexOf("\u001b"));
	}

	@Test
	void allUtf16CodeUnitsRespectTheEscapingContractAndRegionBoundary() {
		var input = new StringBuilder();
		for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
			input.append((char) c);
		}
		input.append("\r\n");
		String prefix = "previous event\n\u001b[31m";
		var output = new StringBuilder(prefix).append(input);
		StandardPostProcessor.CRLF.process(event(), output, prefix.length());
		assertEquals(prefix + reference(input.toString()), output.toString());
		assertTrue(output.length() <= prefix.length() + 6L * input.length());
	}

	static Stream<String> endings() {
		return Stream.of("", "\n", "\r\n", "\r", "\n\n", "\r\r\n", "\u0085", "\u2028", "\u2029");
	}

	@ParameterizedTest
	@MethodSource("endings")
	void keepsAtMostOneFinalLfOrCrLf(String ending) {
		String input = "\u001b[0m\\\u0001" + ending;
		var output = new StringBuilder(input);
		StandardPostProcessor.CRLF.process(event(), output, 0);
		assertEquals(reference(input), output.toString());
	}

	@ParameterizedTest
	@EnumSource(WriteMethod.class)
	void configuredEncoderEscapesAllEventFieldsAndThrowableText(WriteMethod writeMethod) {
		var output = new ListLogOutput() {
			@Override
			public LogEncoder.BufferHints bufferHints() {
				return writeMethod;
			}
		};
		var config = LogConfig.builder()
			.properties(
					LogProperties.builder().fromProperties("logging.appender.app.encoder.postProcessors=crlf").build())
			.build();
		var formatter = LogFormatter.builder()
			.level()
			.space()
			.loggerName()
			.space()
			.message()
			.newline()
			.throwable()
			.build();
		var failure = new IllegalStateException("bad\r\n\u001b[8mhidden\u001b[0m\u0085\u2028");
		failure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("class\u001b[2J", "method\r\n", "file\u001b]0;title\u0007", 42) });
		var event = LogEventFactory.of("untrusted\r\nlogger\u001b[8m")
			.eventNoArg(Level.ERROR, "typed \\n real\n\u009b2J\u2029", KeyValues.of(), failure);
		var raw = new StringBuilder();
		formatter.format(raw, event);
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output).formatter(formatter)))
			.build()
			.start()) {
			gum.log(event);
		}
		assertEquals(reference(raw.toString()), output.toString());
		assertEquals(1, output.events().size());
	}

	private static LogEvent event() {
		return LogEventFactory.of("test").eventNoArg(Level.INFO, "test", KeyValues.of(), null);
	}

	// Deliberately uses the JDK classification and conversion instead of the production
	// range predicate and fixed hexadecimal digit writes.
	private static String reference(String input) {
		int end = input.length();
		if (input.endsWith("\r\n")) {
			end -= 2;
		}
		else if (input.endsWith("\n")) {
			end--;
		}
		var expected = new StringBuilder();
		for (int i = 0; i < end; i++) {
			char c = input.charAt(i);
			if (c == '\\') {
				expected.append("\\\\");
			}
			else if (c == '\n') {
				expected.append("\\n");
			}
			else if (c == '\r') {
				expected.append("\\r");
			}
			else if ((Character.isISOControl(c) && c != '\t') || c == '\u2028' || c == '\u2029') {
				String hex = Integer.toHexString(c);
				expected.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
			}
			else {
				expected.append(c);
			}
		}
		return expected.append(input, end, input.length()).toString();
	}

}
