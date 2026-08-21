package io.jstach.rainbowgum.nio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Real, full RainbowGum loads writing to a real ListLogOutput - no mocks. Compares
 * DirectByteBufferEncoder's output against the standard FormatterEncoder/
 * StringBuilderBuffer path (which does String.toString() + getBytes() per event) to
 * confirm the zero-copy path produces byte-for-byte identical results.
 */
class DirectByteBufferEncoderTest {

	private static final LogFormatter FORMATTER = LogFormatter.builder()
		.text("[")
		.level()
		.text("] ")
		.message()
		.newline()
		.build();

	@Test
	void asciiMessageMatchesStandardEncoder() {
		assertEncodesSameAsStandard("hello world");
	}

	@Test
	void multiByteUtf8MessageMatchesStandardEncoder() {
		// accented characters, CJK, and an emoji outside the BMP (surrogate pair).
		assertEncodesSameAsStandard("héllo wörld 你好 😀");
	}

	@Test
	void messageLargerThanInitialCapacityForcesGrowthAndStillMatches() {
		String longMessage = "x".repeat(DirectByteBufferEncoder.DEFAULT_INITIAL_BYTE_CAPACITY * 3);
		assertEncodesSameAsStandard(longMessage);
	}

	@Test
	void reuseBufferAcrossEventsDoesNotLeakPreviousLongerMessage() {
		var output = new ListLogOutput();
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.encoder(DirectByteBufferEncoder.of(FORMATTER));
				a.flag(AppenderFlag.REUSE_BUFFER);
			});
		}).build();
		try (var g = gum.start()) {
			g.log(event("this is a much longer first message that should not leak"));
			g.log(event("short"));
		}
		List<String> actual = output.events().stream().map(e -> e.getValue()).toList();
		assertEquals(List.of("[INFO] this is a much longer first message that should not leak\n", "[INFO] short\n"),
				actual);
	}

	private static void assertEncodesSameAsStandard(String message) {
		String direct = encodeWith(DirectByteBufferEncoder.of(FORMATTER), message);
		String standard = encodeWith(LogEncoder.of(FORMATTER), message);
		assertEquals(standard, direct);
	}

	private static String encodeWith(LogEncoder encoder, String message) {
		var output = new ListLogOutput();
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.encoder(encoder);
			});
		}).build();
		try (var g = gum.start()) {
			g.log(event(message));
		}
		return output.toString();
	}

	private static LogEvent event(String message) {
		return LogEvent.of(System.Logger.Level.INFO, "test", message, KeyValues.of(), null);
	}

}
