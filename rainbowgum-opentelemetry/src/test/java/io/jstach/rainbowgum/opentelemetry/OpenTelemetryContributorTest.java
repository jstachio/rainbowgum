package io.jstach.rainbowgum.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;

class OpenTelemetryContributorTest {

	static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

	static final String SPAN_ID = "b7ad6b7169203331";

	private static Span span() {
		return Span.wrap(SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault()));
	}

	private static LogConfig config(String properties) {
		return LogConfig.builder()
			.serviceLoader()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.build();
	}

	private static String render(KeyValues kvs) {
		var sb = new StringBuilder();
		kvs.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
		return sb.toString().strip();
	}

	/*
	 * What a logging facade's events get, through the globally bound gum.
	 */
	private static String eventKeyValues(LogConfig config, boolean inSpan) {
		try (var gum = RainbowGum.builder(config).set()) {
			var factory = LogEventFactory.of("test");
			if (!inSpan) {
				return render(factory.defaultKeyValues());
			}
			try (var scope = span().makeCurrent()) {
				return render(factory.defaultKeyValues());
			}
		}
	}

	@Test
	void noSpanAddsNothing() {
		assertEquals("", eventKeyValues(config(""), false));
	}

	@Test
	void currentSpanAddsTraceAndSpanId() {
		assertEquals("traceId=" + TRACE_ID + " spanId=" + SPAN_ID, eventKeyValues(config(""), true));
	}

	@Test
	void disabledLeavesThemOut() {
		assertEquals("", eventKeyValues(config("logging.keyvalues.disabled=opentelemetry"), true));
	}

	@Test
	void spanEndedScopeAddsNothing() {
		try (var scope = span().makeCurrent()) {
			assertEquals("traceId=" + TRACE_ID + " spanId=" + SPAN_ID,
					render(OpenTelemetryContributor.INSTANCE.keyValues()));
		}
		assertEquals("", render(OpenTelemetryContributor.INSTANCE.keyValues()));
	}

}
