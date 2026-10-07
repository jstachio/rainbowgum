package io.jstach.rainbowgum.opentelemetry;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.opentelemetry.api.trace.Span;

/*
 * Reads the current span when the event is created, on the logging thread; an encoder
 * could not, since with an async publisher it runs on another thread. The span context
 * keeps its ids as hex strings, so nothing is formatted here.
 *
 * clear() is deliberately left as the default no-op: this keeps no state of its own, and
 * the OpenTelemetry context belongs to the application or agent, which ends it by closing
 * the scope, so Rainbow Gum must never clear it.
 */
enum OpenTelemetryContributor implements KeyValuesContributor {

	INSTANCE;

	@Override
	public KeyValues keyValues() {
		var context = Span.current().getSpanContext();
		if (!context.isValid()) {
			return KeyValues.of();
		}
		var kvs = MutableKeyValues.of(2);
		kvs.putKeyValue(OpenTelemetryConfigurator.TRACE_ID_KEY, context.getTraceId());
		kvs.putKeyValue(OpenTelemetryConfigurator.SPAN_ID_KEY, context.getSpanId());
		return kvs;
	}

}
