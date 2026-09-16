package io.jstach.rainbowgum.benchmark.encoding;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.core.layout.ByteBufferDestination;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.logging.log4j.util.SortedArrayStringMap;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;

/**
 * Synthetic encoder benchmark for comparing Log4j2's PatternLayout direct encoder with
 * Rainbow Gum's TTLL encoder variants, without filesystem or console I/O.
 */
public final class Main {

	private static final String PATTERN = "%d{HH:mm:ss.SSS} [%t] %-5level %logger - %msg%n";

	private static final String LOGGER_NAME = "io.jstach.rainbowgum.benchmark.nativeimage.BenchHandler";

	private static final String THREAD_NAME = "bench-vthread-1";

	private static final long THREAD_ID = 42L;

	private static final long TIME_MILLIS = 1_798_923_456_789L;

	private static final Instant TIMESTAMP = Instant.ofEpochMilli(TIME_MILLIS);

	private static final KeyValues KEY_VALUES = KeyValues.of(Map.of("requestId", "123456789"));

	private static volatile long blackhole;

	private Main() {
	}

	public static void main(String[] args) {
		Options options = Options.parse(args);
		List<Case> cases = cases(options);
		if (cases.isEmpty()) {
			throw new IllegalArgumentException("Unknown mode: " + options.mode);
		}
		System.out.println("scenario=" + options.scenario.name);
		System.out.println("iterations=" + options.iterations + " warmup=" + options.warmup + " repetitions="
				+ options.repetitions);
		System.out.println();
		System.out.printf("%-24s %14s %14s %14s%n", "case", "ns/event", "events/s", "bytes/event");
		for (Case c : cases) {
			c.run(options.warmup);
			Result best = null;
			for (int i = 0; i < options.repetitions; i++) {
				Result result = c.run(options.iterations);
				if (best == null || result.nanos < best.nanos) {
					best = result;
				}
			}
			if (best == null) {
				throw new IllegalStateException("No result for " + c.name());
			}
			double nsPerEvent = best.nanos / (double) options.iterations;
			double eventsPerSecond = 1_000_000_000.0 / nsPerEvent;
			double bytesPerEvent = best.bytes / (double) options.iterations;
			System.out.printf(Locale.ROOT, "%-24s %,14.1f %,14.0f %,14.1f%n", c.name(), nsPerEvent, eventsPerSecond,
					bytesPerEvent);
			blackhole ^= best.checksum;
		}
		System.out.println();
		System.out.println("blackhole=" + blackhole);
	}

	private static List<Case> cases(Options options) {
		List<Case> cases = new ArrayList<>();
		if (options.includes("log4j2-direct")) {
			cases.add(new Log4j2DirectCase(options.scenario.log4jEvents()));
		}
		if (options.includes("log4j2-byte-array")) {
			cases.add(new Log4j2ByteArrayCase(options.scenario.log4jEvents()));
		}
		if (options.includes("rainbowgum-bytes")) {
			cases.add(new RainbowGumCase("rainbowgum-bytes", WriteMethod.BYTES, options.scenario.rainbowGumEvents()));
		}
		if (options.includes("rainbowgum-byte-buffer")) {
			cases.add(new RainbowGumCase("rainbowgum-byte-buffer", WriteMethod.BYTE_BUFFER,
					options.scenario.rainbowGumEvents()));
		}
		if (options.includes("rainbowgum-string")) {
			cases.add(new RainbowGumCase("rainbowgum-string", WriteMethod.STRING, options.scenario.rainbowGumEvents()));
		}
		if (options.includes("rainbowgum-threadlocal-bytes")) {
			cases.add(new RainbowGumThreadLocalCase("rainbowgum-threadlocal-bytes", WriteMethod.BYTES,
					options.scenario.rainbowGumEvents()));
		}
		if (options.includes("rainbowgum-threadlocal-byte-buffer")) {
			cases.add(new RainbowGumThreadLocalCase("rainbowgum-threadlocal-byte-buffer", WriteMethod.BYTE_BUFFER,
					options.scenario.rainbowGumEvents()));
		}
		if (options.includes("rainbowgum-threadlocal-string")) {
			cases.add(new RainbowGumThreadLocalCase("rainbowgum-threadlocal-string", WriteMethod.STRING,
					options.scenario.rainbowGumEvents()));
		}
		return cases;
	}

	private sealed interface Case
			permits Log4j2DirectCase, Log4j2ByteArrayCase, RainbowGumCase, RainbowGumThreadLocalCase {

		String name();

		Result run(int iterations);

	}

	private record Result(long nanos, long bytes, long checksum) {
	}

	private static final class Log4j2DirectCase implements Case {

		private final PatternLayout layout = PatternLayout.newBuilder()
			.withPattern(PATTERN)
			.withCharset(StandardCharsets.UTF_8)
			.build();

		private final org.apache.logging.log4j.core.LogEvent[] events;

		private final CountingByteBufferDestination destination = new CountingByteBufferDestination();

		private Log4j2DirectCase(org.apache.logging.log4j.core.LogEvent[] events) {
			this.events = events;
		}

		@Override
		public String name() {
			return "log4j2-direct";
		}

		@Override
		public Result run(int iterations) {
			destination.resetCounters();
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++) {
				layout.encode(events[i & 3], destination);
				destination.flush();
			}
			long nanos = System.nanoTime() - start;
			return new Result(nanos, destination.bytes, destination.checksum);
		}

	}

	private static final class Log4j2ByteArrayCase implements Case {

		private final PatternLayout layout = PatternLayout.newBuilder()
			.withPattern(PATTERN)
			.withCharset(StandardCharsets.UTF_8)
			.build();

		private final org.apache.logging.log4j.core.LogEvent[] events;

		private long checksum;

		private long bytes;

		private Log4j2ByteArrayCase(org.apache.logging.log4j.core.LogEvent[] events) {
			this.events = events;
		}

		@Override
		public String name() {
			return "log4j2-byte-array";
		}

		@Override
		public Result run(int iterations) {
			checksum = 0L;
			bytes = 0L;
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++) {
				byte[] encoded = layout.toByteArray(events[i & 3]);
				record(encoded, 0, encoded.length);
			}
			long nanos = System.nanoTime() - start;
			return new Result(nanos, bytes, checksum);
		}

		private void record(byte[] encoded, int off, int len) {
			bytes += len;
			if (len > 0) {
				checksum += encoded[off] + encoded[off + len - 1];
			}
		}

	}

	private static final class RainbowGumCase implements Case {

		private final String name;

		private final LogEvent[] events;

		private final CountingLogOutput output;

		private final LogEncoder encoder;

		private final LogEncoder.Buffer buffer;

		private RainbowGumCase(String name, WriteMethod writeMethod, LogEvent[] events) {
			this.name = name;
			this.events = events;
			this.output = new CountingLogOutput(writeMethod);
			LogConfig config = LogConfig.builder().build();
			this.encoder = LogEncoder.ofTTLL().provide("encoding", config);
			this.buffer = encoder.buffer(writeMethod);
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Result run(int iterations) {
			output.resetCounters();
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++) {
				LogEvent event = events[i & 3];
				encoder.encode(event, buffer);
				buffer.drain(output, event);
			}
			long nanos = System.nanoTime() - start;
			return new Result(nanos, output.bytes, output.checksum);
		}

	}

	private static final class RainbowGumThreadLocalCase implements Case {

		private final String name;

		private final LogEvent[] events;

		private final CountingLogOutput output;

		private final LogEncoder encoder;

		private final ThreadLocal<LogEncoder.Buffer> bufferThreadLocal;

		private RainbowGumThreadLocalCase(String name, WriteMethod writeMethod, LogEvent[] events) {
			this.name = name;
			this.events = events;
			this.output = new CountingLogOutput(writeMethod);
			LogConfig config = LogConfig.builder().build();
			this.encoder = LogEncoder.ofTTLL().provide("encoding", config);
			this.bufferThreadLocal = ThreadLocal.withInitial(() -> encoder.buffer(writeMethod));
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Result run(int iterations) {
			output.resetCounters();
			long start = System.nanoTime();
			for (int i = 0; i < iterations; i++) {
				LogEvent event = events[i & 3];
				LogEncoder.Buffer buffer = bufferThreadLocal.get();
				encoder.encode(event, buffer);
				buffer.drain(output, event);
			}
			long nanos = System.nanoTime() - start;
			return new Result(nanos, output.bytes, output.checksum);
		}

	}

	private static final class CountingLogOutput implements LogOutput {

		private final WriteMethod writeMethod;

		private long bytes;

		private long checksum;

		private CountingLogOutput(WriteMethod writeMethod) {
			this.writeMethod = writeMethod;
		}

		private void resetCounters() {
			bytes = 0L;
			checksum = 0L;
		}

		@Override
		public URI uri() {
			return URI.create("memory:///encoding");
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			record(bytes, off, len);
		}

		@Override
		public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
			int len = buf.remaining();
			if (len > 0) {
				checksum += buf.get(buf.position()) + buf.get(buf.limit() - 1);
			}
			bytes += len;
			buf.position(buf.limit());
		}

		@Override
		public void flush() {
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public WriteMethod bufferHints() {
			return writeMethod;
		}

		@Override
		public void close() {
		}

		private void record(byte[] source, int off, int len) {
			bytes += len;
			if (len > 0) {
				checksum += source[off] + source[off + len - 1];
			}
		}

	}

	private static final class CountingByteBufferDestination implements ByteBufferDestination {

		private final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[8192]);

		private long bytes;

		private long checksum;

		private void resetCounters() {
			bytes = 0L;
			checksum = 0L;
			byteBuffer.clear();
		}

		@Override
		public ByteBuffer getByteBuffer() {
			return byteBuffer;
		}

		@Override
		public ByteBuffer drain(ByteBuffer buffer) {
			flush(buffer);
			return buffer;
		}

		@Override
		public void writeBytes(ByteBuffer data) {
			int len = data.remaining();
			if (len > 0) {
				checksum += data.get(data.position()) + data.get(data.limit() - 1);
			}
			bytes += len;
			data.position(data.limit());
		}

		@Override
		public void writeBytes(byte[] data, int offset, int length) {
			record(data, offset, length);
		}

		private void flush() {
			flush(byteBuffer);
		}

		private void flush(ByteBuffer buffer) {
			buffer.flip();
			int len = buffer.remaining();
			if (len > 0) {
				checksum += buffer.get(buffer.position()) + buffer.get(buffer.limit() - 1);
			}
			bytes += len;
			buffer.clear();
		}

		private void record(byte[] source, int off, int len) {
			bytes += len;
			if (len > 0) {
				checksum += source[off] + source[off + len - 1];
			}
		}

	}

	private enum Scenario {

		ASCII("ascii", "world"),

		EMOJI("emoji", "\uD83C\uDF0D"),

		LONG("long", "world-" + "abcdefghijklmnopqrstuvwxyz0123456789".repeat(8));

		private final String name;

		private final String requestName;

		Scenario(String name, String requestName) {
			this.name = name;
			this.requestName = requestName;
		}

		private LogEvent[] rainbowGumEvents() {
			long step1 = requestName.hashCode() * 31L;
			return new LogEvent[] {
					LogEvent.ofOneArg(TIMESTAMP, THREAD_NAME, THREAD_ID, Level.INFO, LOGGER_NAME,
							"received request name={}", KEY_VALUES, StandardMessageFormatter.SLF4J, requestName),
					LogEvent.ofOneArg(TIMESTAMP, THREAD_NAME, THREAD_ID, Level.INFO, LOGGER_NAME,
							"validating input length={}", KEY_VALUES, StandardMessageFormatter.SLF4J,
							requestName.length()),
					LogEvent.ofOneArg(TIMESTAMP, THREAD_NAME, THREAD_ID, Level.INFO, LOGGER_NAME,
							"processing business logic step=1 value={}", KEY_VALUES, StandardMessageFormatter.SLF4J,
							step1),
					LogEvent.ofOneArg(TIMESTAMP, THREAD_NAME, THREAD_ID, Level.INFO, LOGGER_NAME,
							"returning response status={}", KEY_VALUES, StandardMessageFormatter.SLF4J, 200) };
		}

		private org.apache.logging.log4j.core.LogEvent[] log4jEvents() {
			long step1 = requestName.hashCode() * 31L;
			return new org.apache.logging.log4j.core.LogEvent[] {
					log4jEvent(new ParameterizedMessage("received request name={}", requestName)),
					log4jEvent(new ParameterizedMessage("validating input length={}", requestName.length())),
					log4jEvent(new ParameterizedMessage("processing business logic step=1 value={}", step1)),
					log4jEvent(new ParameterizedMessage("returning response status={}", 200)) };
		}

		private static org.apache.logging.log4j.core.LogEvent log4jEvent(
				org.apache.logging.log4j.message.Message message) {
			SortedArrayStringMap contextData = new SortedArrayStringMap();
			contextData.putValue("requestId", "123456789");
			contextData.freeze();
			return Log4jLogEvent.newBuilder()
				.setLoggerName(LOGGER_NAME)
				.setLoggerFqcn(LOGGER_NAME)
				.setLevel(org.apache.logging.log4j.Level.INFO)
				.setMessage(message)
				.setContextData(contextData)
				.setThreadId(THREAD_ID)
				.setThreadName(THREAD_NAME)
				.setThreadPriority(Thread.NORM_PRIORITY)
				.setTimeMillis(TIME_MILLIS)
				.build();
		}

	}

	private record Options(int iterations, int warmup, int repetitions, Scenario scenario, String mode) {

		private static Options parse(String[] args) {
			int iterations = 5_000_000;
			int warmup = 1_000_000;
			int repetitions = 5;
			Scenario scenario = Scenario.ASCII;
			String mode = "all";
			for (int i = 0; i < args.length; i++) {
				String arg = args[i];
				String value = switch (arg) {
					case "--iterations", "--warmup", "--repetitions", "--scenario", "--mode" -> {
						if (++i >= args.length) {
							throw new IllegalArgumentException("Missing value for " + arg);
						}
						yield args[i];
					}
					default -> throw new IllegalArgumentException("Unknown argument: " + arg);
				};
				switch (arg) {
					case "--iterations" -> iterations = parsePositiveInt(arg, value);
					case "--warmup" -> warmup = parsePositiveInt(arg, value);
					case "--repetitions" -> repetitions = parsePositiveInt(arg, value);
					case "--scenario" -> scenario = parseScenario(value);
					case "--mode" -> mode = value;
					default -> throw new IllegalStateException(arg);
				}
			}
			return new Options(iterations, warmup, repetitions, scenario, mode);
		}

		private boolean includes(String caseName) {
			return mode.equals("all") || mode.equals(caseName);
		}

		private static int parsePositiveInt(String option, String value) {
			int parsed = Integer.parseInt(value.replace("_", ""));
			if (parsed <= 0) {
				throw new IllegalArgumentException(option + " must be positive: " + value);
			}
			return parsed;
		}

		private static Scenario parseScenario(String value) {
			for (Scenario scenario : Scenario.values()) {
				if (scenario.name.equals(value)) {
					return scenario;
				}
			}
			throw new IllegalArgumentException("Unknown scenario: " + value);
		}

	}

}
