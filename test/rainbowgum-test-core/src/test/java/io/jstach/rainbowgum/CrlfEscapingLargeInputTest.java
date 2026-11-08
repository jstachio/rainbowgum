package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.lang.System.Logger.Level;
import java.time.Duration;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CrlfEscapingLargeInputTest {

	enum Input {

		CONTROLS("\u0001", "\\u0001"), ESCAPES("\u001b", "\\u001b"), BACKSLASHES("\\", "\\\\"),
		COLOUR_SEQUENCES("\u001b[31m", "\\u001b[31m"),
		INCOMPLETE_SEQUENCES("\u001b[1111111111111111;", "\\u001b[1111111111111111;"),
		LINE_SEPARATORS("\u0085\u2028\u2029", "\\u0085\\u2028\\u2029");

		final String raw;

		final String escaped;

		Input(String raw, String escaped) {
			this.raw = raw;
			this.escaped = escaped;
		}

	}

	@ParameterizedTest
	@EnumSource(Input.class)
	void denseAdversarialInputsDoNotCauseRepeatedSuffixShifts(Input input) {
		int count = (1 << 20) / input.raw.length();
		String prefix = "previous event\n";
		var output = new StringBuilder(prefix).append(input.raw.repeat(count)).append("\r\n");
		var event = LogEventFactory.of("test").eventNoArg(Level.INFO, "test", KeyValues.of(), null);
		assertTimeout(Duration.ofSeconds(2),
				() -> LogFormatter.StandardPostProcessor.CRLF.process(event, output, prefix.length()));
		assertEquals(prefix + input.escaped.repeat(count) + "\r\n", output.toString());
	}

}
