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
		@Nullable String eventName, @Nullable String traceId, @Nullable String spanId, List<Attribute> attributes) {

	/*
	 * The key value OpenTelemetry's Logback and Log4j appenders read the event name from.
	 */
	static final String EVENT_NAME_KEY = "otel.event.name";

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

	/*
	 * What the encoder and output were configured with: which key values carry the trace
	 * context and which other key values become attributes.
	 */
	record Mapping(String traceIdKey, String spanIdKey, KeyFilter keyFilter) {

		static Mapping of(@Nullable String traceIdKey, @Nullable String spanIdKey, @Nullable List<String> included,
				@Nullable List<String> excluded) {
			return new Mapping(traceIdKey == null ? OtlpJsonEncoder.DEFAULT_TRACE_ID_KEY : traceIdKey,
					spanIdKey == null ? OtlpJsonEncoder.DEFAULT_SPAN_ID_KEY : spanIdKey,
					KeyFilter.of(included, excluded));
		}

	}

	/*
	 * Case sensitive globs where '*' matches any run of characters and '?' one character,
	 * the same syntax as OpenTelemetry's appender attribute filters. An empty include
	 * list includes every key, and an exclude match always wins.
	 */
	record KeyFilter(List<String> included, List<String> excluded) {

		static final KeyFilter ALL = new KeyFilter(List.of(), List.of());

		static KeyFilter of(@Nullable List<String> included, @Nullable List<String> excluded) {
			if ((included == null || included.isEmpty()) && (excluded == null || excluded.isEmpty())) {
				return ALL;
			}
			return new KeyFilter(included == null ? List.of() : List.copyOf(included),
					excluded == null ? List.of() : List.copyOf(excluded));
		}

		boolean test(String key) {
			for (var glob : excluded) {
				if (matches(glob, key)) {
					return false;
				}
			}
			if (included.isEmpty()) {
				return true;
			}
			for (var glob : included) {
				if (matches(glob, key)) {
					return true;
				}
			}
			return false;
		}

		static boolean matches(String glob, String key) {
			int g = 0;
			int k = 0;
			int star = -1;
			int starKey = 0;
			while (k < key.length()) {
				if (g < glob.length() && (glob.charAt(g) == '?' || glob.charAt(g) == key.charAt(k))) {
					g++;
					k++;
				}
				else if (g < glob.length() && glob.charAt(g) == '*') {
					star = g++;
					starKey = k;
				}
				else if (star >= 0) {
					g = star + 1;
					k = ++starKey;
				}
				else {
					return false;
				}
			}
			while (g < glob.length() && glob.charAt(g) == '*') {
				g++;
			}
			return g == glob.length();
		}

	}

	static OtlpRecord of(LogEvent event, Mapping mapping) {
		var kvs = event.keyValues();
		String traceIdKey = mapping.traceIdKey();
		String spanIdKey = mapping.spanIdKey();
		var keyFilter = mapping.keyFilter();
		String traceId = validId(kvs.getValueOrNull(traceIdKey), 32);
		String spanId = traceId == null ? null : validId(kvs.getValueOrNull(spanIdKey), 16);
		String eventName = kvs.getValueOrNull(EVENT_NAME_KEY);
		Map<String, Attribute> attributes = new LinkedHashMap<>();
		attributes.put("thread.name", Attribute.of("thread.name", event.threadName()));
		attributes.put("thread.id", Attribute.of("thread.id", event.threadId()));
		var caller = event.callerOrNull();
		if (caller != null) {
			String function = caller.className() + "." + caller.methodName();
			attributes.put("code.function.name", Attribute.of("code.function.name", function));
			var file = caller.fileNameOrNull();
			if (file != null) {
				attributes.put("code.file.path", Attribute.of("code.file.path", file));
			}
			if (caller.lineNumber() > 0) {
				attributes.put("code.line.number", Attribute.of("code.line.number", caller.lineNumber()));
			}
		}
		kvs.forEach((k, v) -> {
			if (v == null || (traceId != null && k.equals(traceIdKey)) || (spanId != null && k.equals(spanIdKey))
					|| k.equals(EVENT_NAME_KEY) || !keyFilter.test(k)) {
				return;
			}
			attributes.putIfAbsent(k, Attribute.of(k, v));
		});
		var t = event.throwableOrNull();
		if (t != null) {
			attributes.put("exception.type", Attribute.of("exception.type", t.getClass().getName()));
			var message = t.getMessage();
			if (message != null) {
				attributes.put("exception.message", Attribute.of("exception.message", message));
			}
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			attributes.put("exception.stacktrace", Attribute.of("exception.stacktrace", stackTrace.toString()));
		}
		var body = new StringBuilder();
		event.formattedMessage(body);
		var level = event.level();
		return new OtlpRecord(event.loggerName(), unixNanos(event.timestamp()), severityNumber(level),
				severityText(level), body.toString(), eventName, traceId, spanId, List.copyOf(attributes.values()));
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
