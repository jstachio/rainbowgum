package io.jstach.rainbowgum.benchmark.jmh;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.encoder.JsonEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.json.encoder.LogbackJsonEncoder;

/**
 * Compares Rainbow Gum's {@link LogbackJsonEncoder} (a same-schema reimplementation of
 * Logback's own {@code ch.qos.logback.classic.encoder.JsonEncoder}, see its own javadoc
 * for the exact field differences) against the real thing directly - triggered by a
 * real-workload finding (see {@code benchmark/native}'s {@code 0-11-2-RESULTS.md}) that
 * Logback's own built-in JSON encoder substantially outperforms both the third-party
 * {@code logstash-logback-encoder} <em>and</em>, per this microbenchmark, Rainbow Gum's
 * own JSON path for a same-shape payload.
 * <p>
 * Leading theory under test: {@code JsonBuffer._writeStartField} escapes every field name
 * through the same path used for values (`RawJsonWriter#writeString`, added in commit
 * 1f2bb502 to close a real security hole - an MDC-derived key containing a quote could
 * otherwise forge sibling JSON fields, see
 * {@code io.jstach.rainbowgum.json.JsonBuffer#EXTENDED_F}'s own javadoc), even for
 * Rainbow Gum's own hardcoded, compile-time-known-safe field names ("timestamp", "level",
 * ...). Logback's own encoder does not: decompiling
 * {@code JsonEncoder.appenderMember(StringBuilder, String, String)} shows the field-name
 * argument written via a plain, unconditional {@code StringBuilder.append(String)} - only
 * values go through its own {@code jsonEscape}. This benchmark does not isolate that one
 * difference on its own (that would mean reaching
 * {@code RawJsonWriter}/{@code writeAsciiString} directly, both package-private inside
 * {@code rainbowgum-json} - out of reach from this separate module) - it measures the two
 * real, complete encoders end to end, so whatever the actual cause turns out to be
 * (field-name escaping, payload size, something else) shows up in the numbers, not
 * asserted in advance.
 * <p>
 * Deliberately constructs a fresh event inside each {@code @Benchmark} method rather than
 * reusing one built once in {@code @Setup} - Logback's own {@code LoggingEvent} memoizes
 * {@code getFormattedMessage()} after its first call, so reusing the same captured
 * instance across every JMH iteration would silently make only the <em>first</em> call
 * pay for message formatting, understating Logback's real per-event cost and making the
 * comparison meaningless. Rainbow Gum's own path has no such caching (message formatting
 * always writes fresh into the buffer's {@code getFormattedMessageBuilder()}, cleared
 * every call), so it would not have shown the problem on its own - constructing fresh
 * events on both sides keeps the comparison fair regardless of which side happened to
 * have the caching.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class JsonEncoderBenchmark {

	private static final String LOGGER_NAME = "io.jstach.rainbowgum.benchmark.jmh.JsonEncoderBenchmark";

	private static final String MESSAGE_PATTERN = "processing business logic step={} value={}";

	private LogEncoder rgEncoder = LogbackJsonEncoder.of(b -> {
	}).provide("bench", LogConfig.builder().build());

	private Buffer rgBuffer = rgEncoder.buffer(WriteMethod.BYTES);

	/*
	 * Going through the real org.slf4j.LoggerFactory entry point (not `new
	 * LoggerContext()` directly) matters here, not just for realism: it is what actually
	 * triggers SLF4J's own provider-binding lifecycle, which is what initializes
	 * org.slf4j.MDC's backing adapter in the first place. Constructing a LoggerContext by
	 * hand skips that lifecycle entirely, so MDC.put(...) would silently have nowhere to
	 * go (confirmed the hard way: a NullPointerException deep in
	 * LoggingEvent.getMDCPropertyMap() the first time this benchmark actually ran).
	 */
	private Logger lbLogger = (Logger) org.slf4j.LoggerFactory.getLogger(LOGGER_NAME);

	private JsonEncoder lbEncoder = newLogbackEncoder(lbLogger);

	private CapturingAppender lbAppender = new CapturingAppender();

	@Setup
	public void setup() {
		lbAppender.setContext(lbLogger.getLoggerContext());
		lbAppender.start();
		lbLogger.setLevel(Level.INFO);
		lbLogger.setAdditive(false);
		lbLogger.addAppender(lbAppender);
	}

	/*
	 * Matches LogbackJsonEncoder's own field set exactly (see its class javadoc) rather
	 * than JsonEncoder's own defaults (sequenceNumber/context on, formattedMessage off,
	 * message/arguments on instead) - a same-shape comparison is the whole point, and the
	 * two are otherwise measuring different amounts of work, not just different encoders
	 * doing the same work.
	 */
	private static JsonEncoder newLogbackEncoder(Logger logger) {
		var encoder = new JsonEncoder();
		encoder.setContext(logger.getLoggerContext());
		encoder.setWithSequenceNumber(false);
		encoder.setWithContext(false);
		encoder.setWithMarkers(false);
		encoder.setWithKVPList(false);
		encoder.setWithMessage(false);
		encoder.setWithArguments(false);
		encoder.setWithFormattedMessage(true);
		encoder.start();
		return encoder;
	}

	/**
	 * Rainbow Gum's own {@link LogbackJsonEncoder}: build a fresh {@link LogEvent}, then
	 * encode it into the reused {@link Buffer}.
	 * @param bh consumes the buffer so the JIT cannot eliminate the encode call as dead
	 * code.
	 */
	@Benchmark
	public void rainbowGum(Blackhole bh) {
		var event = LogEvent.ofAll(Instant.now(), Thread.currentThread().getName(), Thread.currentThread().threadId(),
				System.Logger.Level.INFO, LOGGER_NAME, MESSAGE_PATTERN, KeyValues.of(Map.of("requestId", "42")), null,
				StandardMessageFormatter.SLF4J, new Object[] { 1, 3512882862L });
		rgBuffer.clear();
		rgEncoder.encode(event, rgBuffer);
		bh.consume(rgBuffer);
	}

	/**
	 * Logback's own real {@code ch.qos.logback.classic.encoder.JsonEncoder}: log a real
	 * message through a real {@link Logger} (capturing the resulting
	 * {@link ILoggingEvent} rather than hand-building a fake one), then encode it.
	 * @param bh consumes the returned bytes so the JIT cannot eliminate the encode call
	 * as dead code.
	 */
	@Benchmark
	public void logback(Blackhole bh) {
		MDC.put("requestId", "42");
		lbLogger.info(MESSAGE_PATTERN, 1, 3512882862L);
		/*
		 * encode() (which reads ILoggingEvent#getMDCPropertyMap() internally) must run
		 * before MDC.remove(...), not after - confirmed by hand that Logback's own MDC
		 * property map is not always eagerly copied at event-construction time (it varies
		 * with how many appenders end up attached), so removing a key from the live MDC
		 * before anything has actually read the captured event's copy can wipe it out
		 * from under you. Encoding first sidesteps needing to know which case applies.
		 */
		byte[] bytes = lbEncoder.encode(lbAppender.last());
		MDC.remove("requestId");
		bh.consume(bytes);
	}

	static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

		private ILoggingEvent last = null;

		@Override
		protected void append(ILoggingEvent eventObject) {
			this.last = eventObject;
		}

		ILoggingEvent last() {
			var e = last;
			if (e == null) {
				throw new IllegalStateException("no event captured yet");
			}
			return e;
		}

	}

}
