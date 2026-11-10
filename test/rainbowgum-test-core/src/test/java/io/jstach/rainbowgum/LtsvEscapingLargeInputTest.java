package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.format.LtsvFormatterBuilder;
import io.jstach.rainbowgum.format.TTLL;

class LtsvEscapingLargeInputTest {

	enum Input {

		C1("\u0080\u0085\u009b\u009f", "\\u0080\\u0085\\u009b\\u009f"), SEPARATORS("\u2028\u2029", "\\u2028\\u2029"),
		ESCAPES("\u001b[8m", "\\u001b[8m"), C0("\u0001", "\\u0001"), BACKSLASHES("\\", "\\\\"),
		FORMAT("\u202e\ud834\udd73\ud80d\udc39", "\\u202e\\ud834\\udd73\\ud80d\\udc39"),
		MALFORMED("\ud800?\udc00", "\\ud800?\\udc00"), PLAIN("text", "text");

		final String raw;

		final String escaped;

		Input(String raw, String escaped) {
			this.raw = raw;
			this.escaped = escaped;
		}

	}

	@ParameterizedTest
	@EnumSource(Input.class)
	void denseMegabyteValuesDoNotCauseRepeatedSuffixShifts(Input input) {
		var properties = LogProperties.builder().fromProperties("""
				logging.appender.test.encoder.ltsv.timestamp=none
				logging.appender.test.encoder.ltsv.level=none
				logging.appender.test.encoder.ltsv.logger=none
				logging.appender.test.encoder.ltsv.thread=none
				""").build();
		var formatter = new LtsvFormatterBuilder("test").fromProperties(properties).color(TTLL.ColorMode.OFF).build();
		int count = (1 << 20) / input.raw.length();
		var event = LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", input.raw.repeat(count), KeyValues.of(),
				null);
		String prefix = "previous:record\n";
		var output = new StringBuilder(prefix);
		assertTimeout(Duration.ofSeconds(2), () -> formatter.format(output, event));
		assertEquals(prefix + "msg:" + input.escaped.repeat(count) + "\n", output.toString());
	}

}
