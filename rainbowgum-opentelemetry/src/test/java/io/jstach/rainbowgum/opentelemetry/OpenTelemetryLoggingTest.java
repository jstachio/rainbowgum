package io.jstach.rainbowgum.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

@Isolated
class OpenTelemetryLoggingTest {

	private static final String TRACE_ID = OpenTelemetryContributorTest.TRACE_ID;

	private static final String SPAN_ID = OpenTelemetryContributorTest.SPAN_ID;

	private static LogConfig config(boolean disabled) {
		return LogConfig.builder()
			.serviceLoader()
			.properties(LogProperties.builder()
				.fromProperties(disabled ? "logging.keyvalues.disabled=opentelemetry" : "")
				.build())
			.build();
	}

	private static SpanContext context() {
		return SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
	}

	@Test
	void asyncEventsCaptureSpanBeforeScopeCloses() throws InterruptedException {
		var output = new ListLogOutput();
		var workerBlocked = new CountDownLatch(1);
		var releaseWorker = new CountDownLatch(1);
		output.setConsumer((event, text) -> {
			if (event.message().equals("gate")) {
				workerBlocked.countDown();
				try {
					assertTrue(releaseWorker.await(10, TimeUnit.SECONDS), "Worker was not released");
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssertionError(e);
				}
			}
		});
		try (var gum = RainbowGum.builder(config(false))
			.route(r -> r.publisher(PublisherFactory.ofAsync(16)).appender("list", a -> a.output(output)))
			.set()) {
			var logger = LoggerFactory.getLogger(getClass().getName() + ".async");
			try {
				logger.info("gate");
				assertTrue(workerBlocked.await(10, TimeUnit.SECONDS), "Async worker did not reach the gate");
				try (var scope = Span.wrap(context()).makeCurrent()) {
					logger.info("plain");
					logger.atInfo().addKeyValue("extra", "value").log("fluent");
					var remote = SpanContext.createFromRemoteParent(TRACE_ID, "3333333333333333",
							TraceFlags.getDefault(), TraceState.getDefault());
					try (var remoteScope = Span.wrap(remote).makeCurrent()) {
						logger.info("remote unsampled");
					}
				}
				assertEquals(SpanContext.getInvalid(), Span.current().getSpanContext());
				logger.info("after scope");
			}
			finally {
				releaseWorker.countDown();
			}
		}
		assertEquals(5, output.events().size());
		for (int i : new int[] { 1, 2 }) {
			var values = output.events().get(i).getKey().keyValues();
			assertEquals(TRACE_ID, values.getValueOrNull("traceId"));
			assertEquals(SPAN_ID, values.getValueOrNull("spanId"));
		}
		assertEquals("value", output.events().get(2).getKey().keyValues().getValueOrNull("extra"));
		assertEquals(TRACE_ID, output.events().get(3).getKey().keyValues().getValueOrNull("traceId"));
		assertEquals("3333333333333333", output.events().get(3).getKey().keyValues().getValueOrNull("spanId"));
		assertNull(output.events().getFirst().getKey().keyValues().getValueOrNull("traceId"));
		assertNull(output.events().getLast().getKey().keyValues().getValueOrNull("traceId"));
	}

	@Test
	void mdcOverridesCurrentSpanAndFluentValuesOverrideMdc() {
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(config(false)).route(r -> r.appender("list", a -> a.output(output))).set();
				var scope = Span.wrap(context()).makeCurrent()) {
			var logger = LoggerFactory.getLogger(getClass().getName() + ".precedence");
			MDC.put("traceId", "11111111111111111111111111111111");
			MDC.put("spanId", "2222222222222222");
			logger.info("mdc");
			logger.atInfo().addKeyValue("spanId", "4444444444444444").log("fluent override");
			MDC.clear();
			logger.info("current span");
		}
		finally {
			MDC.clear();
		}
		assertEquals(3, output.events().size());
		assertEquals("11111111111111111111111111111111",
				output.events().get(0).getKey().keyValues().getValueOrNull("traceId"));
		assertEquals("2222222222222222", output.events().get(0).getKey().keyValues().getValueOrNull("spanId"));
		assertEquals("11111111111111111111111111111111",
				output.events().get(1).getKey().keyValues().getValueOrNull("traceId"));
		assertEquals("4444444444444444", output.events().get(1).getKey().keyValues().getValueOrNull("spanId"));
		assertEquals(TRACE_ID, output.events().get(2).getKey().keyValues().getValueOrNull("traceId"));
		assertEquals(SPAN_ID, output.events().get(2).getKey().keyValues().getValueOrNull("spanId"));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void cleanupPreservesApplicationSpan(boolean disabled) {
		var output = new ListLogOutput();
		var context = context();
		try (var gum = RainbowGum.builder(config(disabled)).route(r -> r.appender("list", a -> a.output(output))).set();
				var scope = Span.wrap(context).makeCurrent()) {
			KeyValuesContributor.global().clear();
			assertEquals(context, Span.current().getSpanContext());
			LoggerFactory.getLogger(getClass().getName() + ".cleanup." + disabled).info("after cleanup");
		}
		assertEquals(1, output.events().size());
		var values = output.events().getFirst().getKey().keyValues();
		assertEquals(disabled ? null : TRACE_ID, values.getValueOrNull("traceId"));
		assertEquals(disabled ? null : SPAN_ID, values.getValueOrNull("spanId"));
	}

}
