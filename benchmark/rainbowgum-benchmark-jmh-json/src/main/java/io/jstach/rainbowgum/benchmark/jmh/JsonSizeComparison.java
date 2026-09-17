package io.jstach.rainbowgum.benchmark.jmh;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.json.encoder.LogbackJsonEncoder;

/**
 * Not a JMH benchmark - a plain, one-shot printout of the actual encoded byte length each
 * encoder produces for the identical event, to check whether Logback's own built-in
 * {@code JsonEncoder} simply emits less data (a real, separate possible explanation for
 * {@link JsonEncoderBenchmark}'s throughput gap, alongside the field-name-escaping theory
 * described there) rather than being a genuinely cheaper encoder for the same amount of
 * output.
 */
public final class JsonSizeComparison {

	private JsonSizeComparison() {
	}

	/**
	 * Entry point.
	 * @param args ignored.
	 */
	public static void main(String[] args) {
		byte[] rg = rainbowGumBytes();
		byte[] lb = logbackBytes();
		System.out.println("rainbowgum bytes: " + rg.length);
		System.out.println(new String(rg, java.nio.charset.StandardCharsets.UTF_8));
		System.out.println("logback bytes:    " + lb.length);
		System.out.println(new String(lb, java.nio.charset.StandardCharsets.UTF_8));
		double pct = 100.0 * (rg.length - lb.length) / lb.length;
		System.out.printf(Locale.ROOT, "rainbowgum is %.1f%% %s than logback for this event%n", Math.abs(pct),
				pct >= 0 ? "bigger" : "smaller");
	}

	private static byte[] rainbowGumBytes() {
		var encoder = LogbackJsonEncoder.of(b -> {
		}).provide("bench", LogConfig.builder().build());
		var buffer = encoder.buffer(WriteMethod.BYTES);
		var event = LogEvent.ofAll(Instant.now(), Thread.currentThread().getName(), Thread.currentThread().threadId(),
				System.Logger.Level.INFO, "io.jstach.rainbowgum.benchmark.jmh.JsonSizeComparison",
				"processing business logic step={} value={}", KeyValues.of(Map.of("requestId", "42")), null,
				StandardMessageFormatter.SLF4J, new Object[] { 1, 3512882862L });
		buffer.clear();
		encoder.encode(event, buffer);
		var capture = new CapturingOutput();
		buffer.drain(capture, event);
		return capture.bytesOrEmpty();
	}

	private static byte[] logbackBytes() {
		// See JsonEncoderBenchmark's own comment on why this goes through
		// org.slf4j.LoggerFactory rather than constructing a LoggerContext directly.
		Logger logger = (Logger) org.slf4j.LoggerFactory
			.getLogger("io.jstach.rainbowgum.benchmark.jmh.JsonSizeComparison");
		var appender = new AppenderBase<ILoggingEvent>() {
			private ILoggingEvent last;

			@Override
			protected void append(ILoggingEvent eventObject) {
				this.last = eventObject;
			}
		};
		appender.setContext(logger.getLoggerContext());
		appender.start();
		logger.setLevel(Level.INFO);
		logger.setAdditive(false);
		logger.addAppender(appender);
		var encoder = new JsonEncoder();
		encoder.setContext(logger.getLoggerContext());
		// See JsonEncoderBenchmark's newLogbackEncoder for why these are set to match
		// LogbackJsonEncoder's own field set.
		encoder.setWithSequenceNumber(false);
		encoder.setWithContext(false);
		encoder.setWithMarkers(false);
		encoder.setWithKVPList(false);
		encoder.setWithMessage(false);
		encoder.setWithArguments(false);
		encoder.setWithFormattedMessage(true);
		encoder.start();
		MDC.put("requestId", "42");
		logger.info("processing business logic step={} value={}", 1, 3512882862L);
		// encode() (reads getMDCPropertyMap() internally) must run before
		// MDC.remove(...) - see JsonEncoderBenchmark's own comment on this.
		byte[] bytes = encoder.encode(appender.last);
		MDC.remove("requestId");
		return bytes;
	}

	private static final class CapturingOutput implements LogOutput {

		private byte @org.jspecify.annotations.Nullable [] bytes;

		byte[] bytesOrEmpty() {
			var b = bytes;
			return b == null ? new byte[0] : b;
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
			this.bytes = java.util.Arrays.copyOfRange(bytes, off, off + len);
		}

		@Override
		public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
			byte[] b = new byte[buf.remaining()];
			buf.get(b);
			this.bytes = b;
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}
