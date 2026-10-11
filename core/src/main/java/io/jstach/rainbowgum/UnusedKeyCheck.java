package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.properties.ForwardingLogProperties;

/*
 * Used by logging.debug=help and all. Records every property key read through the
 * layers it wraps, then finds keys that were set in a layer that can list its keys but
 * never read, suggesting the read key each was probably meant to be. Package private so
 * nothing outside core can depend on it, even from the classpath.
 */
final class UnusedKeyCheck {

	static final int MAX_DISTANCE = 2;

	/*
	 * Read before or outside of LogConfig, so the recorder never sees them: core's own
	 * system property only keys, RainbowGumSystemLoggerFinder's, and the keys
	 * rainbowgum-simple-props reads while loading its files.
	 */
	static final Set<String> READ_BEFORE_CONFIG_KEYS = Set.of("logging.debug", "logging.global.queue.level",
			"logging.global.queue.error", "logging.systemlogger.initialize", "logging.profiles",
			"logging.simpleprops.strict");

	/*
	 * System properties other libraries read that happen to start with logging.: Spring
	 * Boot's logging.config and JBoss LogManager's logging.configuration.
	 */
	static final Set<String> OTHER_LIBRARY_KEYS = Set.of("logging.config", "logging.configuration");

	private final Set<String> reads = ConcurrentHashMap.newKeySet();

	private final List<KeySource> sources;

	/**
	 * A layer that can list its keys.
	 *
	 * @param properties the layer, used for descriptions.
	 * @param keys its keys.
	 */
	record KeySource(LogProperties properties, Supplier<? extends Collection<String>> keys) {
	}

	/**
	 * An unused key.
	 *
	 * @param key the key as set.
	 * @param description where it was set.
	 * @param suggestion the read key it was probably meant to be.
	 */
	record Unused(String key, String description, @Nullable String suggestion) {

		/**
		 * Message for alerts and errors.
		 * @return message.
		 */
		String message() {
			String message = "Property key '" + key + "' from " + description + " was set but not read during startup.";
			if (suggestion != null) {
				message += " Did you mean '" + suggestion + "'?";
			}
			return message;
		}

	}

	/**
	 * Creates a check over sources.
	 * @param sources layers that can list their keys, highest priority first.
	 */
	UnusedKeyCheck(List<KeySource> sources) {
		this.sources = List.copyOf(sources);
	}

	/**
	 * Wraps a layer so its reads are recorded.
	 * @param properties layer.
	 * @return recording layer.
	 */
	LogProperties wrap(LogProperties properties) {
		if (properties instanceof LogProperties.MutableLogProperties mutable) {
			return new MutableRecordingProperties(mutable, reads);
		}
		return new RecordingProperties(properties, reads);
	}

	/**
	 * Keys set in the sources that were not read, each reported once for the highest
	 * priority source that has it.
	 * @return unused keys in source order.
	 */
	List<Unused> unused() {
		var candidates = new TreeSet<>(reads);
		var seen = new LinkedHashSet<String>();
		var unused = new ArrayList<Unused>();
		for (var source : sources) {
			for (String key : source.keys().get()) {
				if (isIgnored(key) || reads.contains(key) || !seen.add(key)) {
					continue;
				}
				unused.add(new Unused(key, source.properties().description(key), suggestion(key, candidates)));
			}
		}
		return unused;
	}

	/*
	 * Level keys are read per logger name as loggers are created, so not reading one at
	 * startup means nothing.
	 */
	static boolean isIgnored(String key) {
		if (!key.startsWith(LogProperties.ROOT_PREFIX) || READ_BEFORE_CONFIG_KEYS.contains(key)
				|| OTHER_LIBRARY_KEYS.contains(key)) {
			return true;
		}
		if (key.equals(LogProperties.LEVEL_PREFIX) || key.startsWith(LogProperties.LEVEL_PREFIX + LogProperties.SEP)) {
			return true;
		}
		String routePrefix = LogProperties.ROOT_PREFIX + "route.";
		if (!key.startsWith(routePrefix)) {
			return false;
		}
		int routeEnd = key.indexOf(LogProperties.SEP, routePrefix.length());
		if (routeEnd < 0) {
			return false;
		}
		String routeKey = key.substring(routeEnd + LogProperties.SEP.length());
		return routeKey.equals("level") || routeKey.startsWith("level.");
	}

	/*
	 * Suggestions come from every key read, not only those read but not set: a system
	 * property meant to override a key set in a file is close to a key that was found.
	 */
	static @Nullable String suggestion(String key, Set<String> candidates) {
		String normalized = normalize(key);
		for (String candidate : candidates) {
			if (normalize(candidate).equals(normalized)) {
				return candidate;
			}
		}
		var moved = movedKeys(key);
		for (String candidate : candidates) {
			for (String pattern : moved) {
				if (matches(pattern, candidate)) {
					return candidate;
				}
			}
		}
		String best = null;
		int bestDistance = MAX_DISTANCE + 1;
		for (String candidate : candidates) {
			if (isIgnored(candidate)) {
				continue;
			}
			int distance = distance(key, candidate, bestDistance);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	/*
	 * Where a key from an older layout is now, with ANY_SEGMENT standing for the type's
	 * URI scheme when the old key does not say it:
	 *
	 * logging.encoder.console.level -> logging.appender.console.encoder.*.level
	 * logging.encoder.ttll.console.level -> logging.appender.console.encoder.ttll.level
	 * logging.appender.console.encoder.level -> logging.appender.console.encoder.*.level
	 * logging.appender.console.encoder -> logging.appender.console.encoder.uri
	 *
	 * The third only applies to a single property name segment, since a longer key under
	 * the encoder is a typed key, possibly left over from a type switched away from.
	 */
	static List<String> movedKeys(String key) {
		var segments = List.of(key.split("\\.", -1));
		if (segments.size() < 3 || !segments.get(0).equals("logging")) {
			return List.of();
		}
		String component = segments.get(1);
		if (component.equals("encoder") || component.equals("output")) {
			var moved = new ArrayList<String>();
			if (segments.size() > 3) {
				moved.add(movedKey(segments.get(2), component, ANY_SEGMENT, segments.subList(3, segments.size())));
			}
			if (segments.size() > 4) {
				moved.add(movedKey(segments.get(3), component, segments.get(2), segments.subList(4, segments.size())));
			}
			return moved;
		}
		if (component.equals("appender") && segments.size() >= 4
				&& (segments.get(3).equals("encoder") || segments.get(3).equals("output"))) {
			if (segments.size() == 4) {
				return List.of(key + ".uri");
			}
			if (segments.size() == 5 && !segments.get(4).equals("uri")) {
				return List.of(movedKey(segments.get(2), segments.get(3), ANY_SEGMENT, segments.subList(4, 5)));
			}
		}
		return List.of();
	}

	private static final String ANY_SEGMENT = "*";

	private static String movedKey(String name, String component, String scheme, List<String> rest) {
		return "logging.appender." + name + "." + component + "." + scheme + "." + String.join(LogProperties.SEP, rest);
	}

	/*
	 * Whether a candidate is a moved key pattern, compared like normalize with
	 * ANY_SEGMENT matching any one segment.
	 */
	static boolean matches(String pattern, String candidate) {
		String[] p = normalize(pattern).split("\\.", -1);
		String[] c = normalize(candidate).split("\\.", -1);
		if (p.length != c.length) {
			return false;
		}
		for (int i = 0; i < p.length; i++) {
			if (!p[i].equals(ANY_SEGMENT) && !p[i].equals(c[i])) {
				return false;
			}
		}
		return true;
	}

	/*
	 * The same key as far as a user typing it is concerned: keyValues, keyvalues,
	 * KEYVALUES, key-values, and key_values.
	 */
	static String normalize(String key) {
		return key.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
	}

	/*
	 * Levenshtein distance, giving up once it cannot be under the limit.
	 */
	static int distance(String a, String b, int limit) {
		if (Math.abs(a.length() - b.length()) >= limit) {
			return limit;
		}
		int[] previous = new int[b.length() + 1];
		int[] current = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			previous[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			current[0] = i;
			int rowMin = current[0];
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
				rowMin = Math.min(rowMin, current[j]);
			}
			if (rowMin >= limit) {
				return limit;
			}
			int[] swap = previous;
			previous = current;
			current = swap;
		}
		return previous[b.length()];
	}

	/*
	 * Records the key of every lookup. visit records the key asked for, since a layer
	 * that renames keys may answer through another properties.
	 */
	private static sealed class RecordingProperties extends ForwardingLogProperties permits MutableRecordingProperties {

		private final LogProperties delegate;

		private final Set<String> reads;

		RecordingProperties(LogProperties delegate, Set<String> reads) {
			this.delegate = delegate;
			this.reads = reads;
		}

		@Override
		protected LogProperties delegate() {
			return delegate;
		}

		@Override
		public @Nullable String valueOrNull(String key) {
			reads.add(key);
			return super.valueOrNull(key);
		}

		@Override
		public @Nullable List<String> listOrNull(String key) {
			reads.add(key);
			return super.listOrNull(key);
		}

		@Override
		public @Nullable Map<String, String> mapOrNull(String key) {
			reads.add(key);
			var map = super.mapOrNull(key);
			/*
			 * Keep the delegate's map parsing, including native map support. When no
			 * single value supplies the map, its .keys and member properties supply it.
			 * Those nested reads happen inside the delegate rather than this decorator.
			 */
			if (map != null && delegate.valueOrNull(key) == null) {
				reads.add(key + LogProperties.SEP + LogProperties.MAP_KEYS_SUFFIX);
				for (var member : map.keySet()) {
					reads.add(key + LogProperties.SEP + member);
				}
			}
			return map;
		}

		@Override
		public <R extends @Nullable Object> @Nullable R visit(String key,
				BiFunction<LogProperties, String, @Nullable R> visitor) {
			reads.add(key);
			return super.visit(key, visitor);
		}

	}

	private static final class MutableRecordingProperties extends RecordingProperties
			implements LogProperties.MutableLogProperties {

		private final LogProperties.MutableLogProperties mutable;

		MutableRecordingProperties(LogProperties.MutableLogProperties mutable, Set<String> reads) {
			super(mutable, reads);
			this.mutable = mutable;
		}

		@Override
		public LogProperties.MutableLogProperties put(String key, @Nullable String value) {
			mutable.put(key, value);
			return this;
		}

	}

}
