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
	 * An OTLP attribute value: any when not null, else stringValue when not null, else
	 * intValue.
	 */
	record Attribute(String key, @Nullable String stringValue, long intValue, @Nullable Any any) {

		static Attribute of(String key, String value) {
			return new Attribute(key, value, 0, null);
		}

		static Attribute of(String key, long value) {
			return new Attribute(key, null, value, null);
		}

		/*
		 * A key value with a JSON form becomes a typed AnyValue: a number, boolean, array
		 * or kvlist. A JSON form that does not parse keeps the plain value.
		 */
		static Attribute of(String key, String value, @Nullable String json) {
			if (json == null) {
				return of(key, value);
			}
			try {
				return new Attribute(key, null, 0, new Any(JsonTree.parse(json)));
			}
			catch (IllegalArgumentException e) {
				return of(key, value);
			}
		}

	}

	/*
	 * A parsed JSON form, see JsonTree. value null is an empty AnyValue.
	 */
	record Any(@Nullable Object value) {
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
		var t = event.throwableOrNull();
		var caller = event.callerOrNull();
		/*
		 * A list, not a map: the only possible duplicates are key values named like the
		 * attributes the event itself sets, and reserved() skips those, so the event's
		 * attributes win.
		 */
		List<Attribute> attributes = new ArrayList<>(4 + kvs.size() + (t == null ? 0 : 3));
		attributes.add(Attribute.of("thread.name", event.threadName()));
		attributes.add(Attribute.of("thread.id", event.threadId()));
		boolean file = false;
		boolean line = false;
		if (caller != null) {
			attributes.add(Attribute.of("code.function.name", caller.className() + "." + caller.methodName()));
			var fileName = caller.fileNameOrNull();
			if (fileName != null) {
				attributes.add(Attribute.of("code.file.path", fileName));
				file = true;
			}
			if (caller.lineNumber() > 0) {
				attributes.add(Attribute.of("code.line.number", caller.lineNumber()));
				line = true;
			}
		}
		String exceptionMessage = t == null ? null : t.getMessage();
		var reserved = new Reserved(caller != null, file, line, t != null, exceptionMessage != null);
		kvs.forEach((k, v) -> {
			if (v == null || (traceId != null && k.equals(traceIdKey)) || (spanId != null && k.equals(spanIdKey))
					|| k.equals(EVENT_NAME_KEY) || reserved.test(k) || !keyFilter.test(k)) {
				return;
			}
			attributes.add(Attribute.of(k, v, kvs.getJsonOrNull(k)));
		});
		if (t != null) {
			attributes.add(Attribute.of("exception.type", t.getClass().getName()));
			if (exceptionMessage != null) {
				attributes.add(Attribute.of("exception.message", exceptionMessage));
			}
			var stackTrace = new StringBuilder();
			ThrowableFormatter.appendThrowable(stackTrace, t);
			attributes.add(Attribute.of("exception.stacktrace", stackTrace.toString()));
		}
		var body = new StringBuilder();
		event.formattedMessage(body);
		var level = event.level();
		return new OtlpRecord(event.loggerName(), unixNanos(event.timestamp()), severityNumber(level),
				severityText(level), body.toString(), eventName, traceId, spanId, attributes);
	}

	/*
	 * Names of the attributes this event sets itself, which a key value of the same name
	 * must not duplicate.
	 */
	private record Reserved(boolean function, boolean file, boolean line, boolean exception, boolean exceptionMessage) {

		boolean test(String key) {
			return switch (key) {
				case "thread.name", "thread.id" -> true;
				case "code.function.name" -> function;
				case "code.file.path" -> file;
				case "code.line.number" -> line;
				case "exception.type", "exception.stacktrace" -> exception;
				case "exception.message" -> exceptionMessage;
				default -> false;
			};
		}

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
