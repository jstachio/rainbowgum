package io.jstach.rainbowgum;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties.LoggerKey;

record DefaultLoggerKey(Namespace namespace, String prefix, String loggerName, String key) implements LoggerKey {

	private static final String ROUTE_START = LogProperties.ROOT_PREFIX + "route" + LogProperties.SEP;

	private static final String ROUTE_LEVEL = LogProperties.SEP + "level";

	/*
	 * The key for a logger name. The name is appended as is, so a name with a leading dot
	 * or any other character parses back to the same name.
	 */
	static String key(String prefix, String loggerName) {
		return loggerName.isEmpty() ? prefix : prefix + LogProperties.SEP + loggerName;
	}

	static @Nullable LoggerKey parseOrNull(String key) {
		var k = parse(Namespace.LEVEL, LogProperties.LEVEL_PREFIX, key);
		if (k == null) {
			k = parse(Namespace.CHANGE, LogProperties.CHANGE_LOGGER_PREFIX, key);
		}
		if (k == null) {
			k = parse(Namespace.CALLER, LogProperties.CALLER_LOGGER_PREFIX, key);
		}
		if (k == null && key.startsWith(ROUTE_START)) {
			int routeEnd = key.indexOf(LogProperties.SEP, ROUTE_START.length());
			if (routeEnd > ROUTE_START.length()) {
				k = parse(Namespace.ROUTE_LEVEL, key.substring(0, routeEnd) + ROUTE_LEVEL, key);
			}
		}
		return k;
	}

	private static @Nullable LoggerKey parse(Namespace namespace, String prefix, String key) {
		if (!key.startsWith(prefix)) {
			return null;
		}
		if (key.length() == prefix.length()) {
			return new DefaultLoggerKey(namespace, prefix, "", key);
		}
		if (!key.startsWith(LogProperties.SEP, prefix.length())) {
			return null;
		}
		return new DefaultLoggerKey(namespace, prefix, key.substring(prefix.length() + LogProperties.SEP.length()),
				key);
	}

}
