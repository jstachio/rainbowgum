package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;

/*
 * The generated PatternEncoderBuilder.charset(...) property/setter - verified with raw
 * captured bytes (like LogEncoder.of(LogFormatter, Charset)'s own test in core) rather
 * than ListLogOutput, which always decodes as UTF-8 for storage and would defeat a test
 * trying to confirm a *different* charset was actually used.
 */
class PatternEncoderCharsetTest {

	// 'é' is 2 bytes in UTF-8 (0xC3 0xA9) but 1 byte in ISO-8859-1 (0xE9).
	private static final String MESSAGE = "café";

	@Test
	void charsetPropertyAppliesToEncodedBytes() {
		var output = new CapturingOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=pattern
				logging.encoder.list.pattern=%msg
				logging.encoder.list.charset=ISO-8859-1
				""";
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.router().eventBuilder("test", System.Logger.Level.INFO).message(MESSAGE).log();
		}
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.ISO_8859_1), output.capturedBytes());
	}

	@Test
	void unsetCharsetDefaultsToUtf8() {
		var output = new CapturingOutput();
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list
				logging.appender.list.encoder=pattern
				logging.encoder.list.pattern=%msg
				""";
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(output));
		try (var g = RainbowGum.builder(config).build().start()) {
			g.router().eventBuilder("test", System.Logger.Level.INFO).message(MESSAGE).log();
		}
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.UTF_8), output.capturedBytes());
	}

	@Test
	void convertCharsetOfNullIsNull() {
		assertNull(PatternConfigurator.convertCharset(null));
	}

	static class CapturingOutput implements LogOutput {

		private final List<byte[]> writes = new ArrayList<>();

		byte[] capturedBytes() {
			return writes.get(writes.size() - 1);
		}

		@Override
		public BufferHints bufferHints() {
			return WriteMethod.BYTES;
		}

		@Override
		public URI uri() {
			throw new UnsupportedOperationException();
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			writes.add(Arrays.copyOfRange(bytes, off, off + len));
		}

		@Override
		public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
			byte[] arr = new byte[buf.remaining()];
			buf.get(arr);
			writes.add(arr);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}
