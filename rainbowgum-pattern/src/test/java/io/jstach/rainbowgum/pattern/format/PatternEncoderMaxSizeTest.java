package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;

/*
 * The generated PatternEncoderBuilder.maxSize(...) property/setter - proves
 * logging.encoder.{name}.maxSize actually threads through to the LogEncoder.Buffer
 * handed out and its isOversized() answer, the same way charset threads through to
 * encoding (see PatternEncoderCharsetTest). This is the encoder-side half of
 * AppenderBufferSizeLimitTest (core) - the appender never sees maxSize itself, only the
 * boolean isOversized() the buffer computes from it.
 * <p>
 * Uses {@link WriteMethod#STRING} (a plain {@link LogEncoder.Buffer.StringBuilderBuffer}
 * starting from a tiny default-capacity {@link StringBuilder}) rather than
 * {@link WriteMethod#BYTES}/{@link WriteMethod#BYTE_BUFFER}
 * ({@link LogEncoder.Buffer.DirectByteBufferBuffer}, which pre-allocates an 8192-byte
 * buffer up front) so small, easy-to-read {@code maxSize} values in these tests aren't
 * swamped by that pre-allocation.
 */
class PatternEncoderMaxSizeTest {

	private static LogEncoder.Buffer bufferAfterEncoding(String properties, String message) {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		var b = new PatternEncoderBuilder("list");
		b.pattern("%msg");
		b.fromProperties(config.properties());
		LogEncoder encoder = b.build().provide("list", config);
		var buffer = encoder.buffer(WriteMethod.STRING);
		var event = LogEvent.of(System.Logger.Level.INFO, "test", message, KeyValues.of(), null);
		encoder.encode(event, buffer);
		return buffer;
	}

	@Test
	void messagePastMaxSizeReportsOversizedBuffer() {
		var buffer = bufferAfterEncoding("logging.encoder.list.maxSize=5\n", "a message well past five characters");
		assertTrue(buffer.isOversized());
	}

	@Test
	void messageUnderMaxSizeDoesNotReportOversized() {
		var buffer = bufferAfterEncoding("logging.encoder.list.maxSize=1000\n", "short");
		assertFalse(buffer.isOversized());
	}

	@Test
	void unsetMaxSizeNeverReportsOversized() {
		var buffer = bufferAfterEncoding("",
				"a message well past five characters, over and over again to grow it a lot more just in case");
		assertFalse(buffer.isOversized());
	}

	/*
	 * Confirms one of the previously-flagged gaps in the appender-side exploration
	 * (core's AppenderBufferSizeLimitTest) no longer applies now that maxSize moved to
	 * the encoder: the built-in "console" appender's default encoder - resolved via
	 * PatternConfigurator's own CONSOLE_OUT registration below, not through an explicit
	 * logging.appender.console.encoder property - is built with
	 * PatternEncoderBuilder(name).fromProperties(...) using the SAME name ("console") a
	 * real appender would pass, so logging.encoder.console.maxSize is picked up exactly
	 * the same way logging.encoder.console.charset already was, with no separate wiring
	 * needed.
	 */
	@Test
	void consoleDefaultEncoderPicksUpMaxSizeTheSameWayAsAnyOtherEncoder() {
		String properties = "logging.encoder.console.maxSize=5\n";
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		// Mirrors exactly what DefaultAppenderRegistry.defaultConsoleAppender() (core)
		// does to resolve the console appender's encoder when none is explicitly
		// configured.
		LogEncoder encoder = config.encoderRegistry()
			.encoderForOutputType(OutputType.CONSOLE_OUT)
			.provide("console", config);
		var buffer = encoder.buffer(WriteMethod.STRING);
		var event = LogEvent.of(System.Logger.Level.INFO, "test", "a message well past five characters", KeyValues.of(),
				null);
		encoder.encode(event, buffer);
		assertTrue(buffer.isOversized());
	}

}
