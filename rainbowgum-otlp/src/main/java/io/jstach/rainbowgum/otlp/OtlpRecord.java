package io.jstach.rainbowgum.otlp;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

/*
 * One LogEvent mapped to OTLP LogRecord values. The JSON and protobuf writers both read
 * this, so the two wire formats cannot disagree about what a record contains.
 */
record OtlpRecord(String scopeName, long timeUnixNano, int severityNumber, String severityText, String body,
		@Nullable String traceId, @Nullable String spanId, List<Attribute> attributes) {

	/*
	 * A string or int64 OTLP attribute value; intValue is used when stringValue is null.
	 */
	record Attribute(String key, @Nullable String stringValue, long intValue) {

		static Attribute of(String key, String value) {
			return new Attribute(key, value, 0);
		}

		static Attribute of(String key, long value) {
			return new Attribute(key, null, value);
		}

	}

	static OtlpRecord of(LogEvent event, String traceIdKey, String spanIdKey) {
		var kvs = event.keyValues();
		String traceId = validId(kvs.getValueOrNull(traceIdKey), 32);
		String spanId = traceId == null ? null : validId(kvs.getValueOrNull(spanIdKey), 16);
		List<Attribute> attributes = new ArrayList<>();
		attributes.add(Attribute.of("thread.name", event.threadName()));
		attributes.add(Attribute.of("thread.id", event.threadId()));
		kvs.forEach((k, v) -> {
			if (v == null || (traceId != null && k.equals(traceIdKey)) || (spanId != null && k.equals(spanIdKey))) {
				return;
			}
			attributes.add(Attribute.of(k, v));
		});
		var t = event.throwableOrNull();
		if (t != null) {
			attributes.add(Attribute.of("exception.type", t.getClass().getName()));
			var message = t.getMessage();
			if (message != null) {
				attributes.add(Attribute.of("exception.message", message));
			}
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			attributes.add(Attribute.of("exception.stacktrace", stackTrace.toString()));
		}
		var body = new StringBuilder();
		event.formattedMessage(body);
		var level = event.level();
		return new OtlpRecord(event.loggerName(), unixNanos(event.timestamp()), severityNumber(level),
				severityText(level), body.toString(), traceId, spanId, List.copyOf(attributes));
	}

	/*
	 * Groups records by scope (logger name), keeping first-seen order, because OTLP nests
	 * LogRecords under one ScopeLogs per instrumentation scope.
	 */
	static Map<String, List<OtlpRecord>> byScope(List<OtlpRecord> records) {
		Map<String, List<OtlpRecord>> scopes = new LinkedHashMap<>();
		for (var r : records) {
			scopes.computeIfAbsent(r.scopeName(), k -> new ArrayList<>()).add(r);
		}
		return scopes;
	}

	static long unixNanos(Instant instant) {
		return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
	}

	static int severityNumber(Level level) {
		return switch (level) {
			case ALL, TRACE -> 1;
			case DEBUG -> 5;
			case INFO -> 9;
			case WARNING -> 13;
			case ERROR -> 17;
			case OFF -> 0;
		};
	}

	static String severityText(Level level) {
		return switch (level) {
			case ALL, TRACE -> "TRACE";
			case DEBUG -> "DEBUG";
			case INFO -> "INFO";
			case WARNING -> "WARN";
			case ERROR -> "ERROR";
			case OFF -> "";
		};
	}

	/*
	 * W3C trace context ids: lowercase or uppercase hex of the exact length and not all
	 * zeros. Anything else stays an ordinary attribute.
	 */
	static @Nullable String validId(@Nullable String id, int length) {
		if (id == null || id.length() != length) {
			return null;
		}
		boolean nonZero = false;
		for (int i = 0; i < length; i++) {
			char c = id.charAt(i);
			boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!hex) {
				return null;
			}
			nonZero |= c != '0';
		}
		return nonZero ? id.toLowerCase(java.util.Locale.ROOT) : null;
	}

}
