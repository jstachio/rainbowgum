package io.jstach.rainbowgum.pattern.format;

import java.util.concurrent.ConcurrentHashMap;

interface Abbreviator {

	static final char DOT = '.';

	public String abbreviate(String in);

	/**
	 * Creates the abbreviator for the given target length - uncached. Callers that want
	 * caching wrap the result with {@link #cache(Abbreviator)} themselves, driven by
	 * {@link PatternConfig#abbreviatorCache()}.
	 * @param length target length, non-positive means class-name-only.
	 * @return abbreviator.
	 */
	public static Abbreviator of(int length) {
		if (length <= 0) {
			return StandardAbbreviator.CLASS_NAME_ONLY;
		}
		return new TargetLengthBasedClassNameAbbreviator(length);
	}

	/**
	 * Wraps an abbreviator with a cache.
	 * @param a abbreviator to wrap.
	 * @return cached abbreviator.
	 */
	public static Abbreviator cache(Abbreviator a) {
		return new CacheAbbreviator(a);
	}

	/*
	 * Why a plain ConcurrentHashMap and not an LRU or Logback's cache:
	 *
	 * The key is the logger name, and logger names are as bounded as the loggers
	 * themselves, which the logging facades keep for the life of the process anyway. So
	 * this map never holds more than one short string per logger that already exists. An
	 * LRU only protects against an unbounded key set; here eviction would just recompute
	 * abbreviations for loggers that are still in use.
	 *
	 * Logger names that are not bounded, for example a request id used as a logger name,
	 * are a bug in the application that would leak loggers first. Rainbow Gum reports
	 * them through LogMetrics.LOGGER_NAMES_METRIC instead of guarding every cache.
	 *
	 * Logback's NamedConverter cache, which this replaced, took a lock on every call (hit
	 * or miss) to count calls, and grew or disabled itself based on its miss rate; all of
	 * that exists for the unbounded case. Here a hit is a lock free read and a miss locks
	 * only its own bin in computeIfAbsent.
	 */
	final class CacheAbbreviator implements Abbreviator {

		private final Abbreviator abbreviator;

		private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

		CacheAbbreviator(Abbreviator abbreviator) {
			this.abbreviator = abbreviator;
		}

		@Override
		public String abbreviate(String in) {
			String abbreviated = cache.get(in);
			if (abbreviated != null) {
				return abbreviated;
			}
			return cache.computeIfAbsent(in, abbreviator::abbreviate);
		}

	}

	enum StandardAbbreviator implements Abbreviator {

		CLASS_NAME_ONLY {

			@Override
			public String abbreviate(String fqClassName) {
				// we ignore the fact that the separator character can also be a
				// dollar
				// If the inner class is org.good.AClass#Inner, returning
				// AClass#Inner seems most appropriate
				int lastIndex = fqClassName.lastIndexOf(DOT);
				if (lastIndex != -1) {
					return fqClassName.substring(lastIndex + 1, fqClassName.length());
				}
				else {
					return fqClassName;
				}
			}
		};

	}

	class TargetLengthBasedClassNameAbbreviator implements Abbreviator {

		final int targetLength;

		public TargetLengthBasedClassNameAbbreviator(int targetLength) {
			this.targetLength = targetLength;
		}

		@Override
		public String abbreviate(String fqClassName) {
			int inLen = fqClassName.length();
			if (inLen < targetLength) {
				return fqClassName;
			}

			StringBuilder buf = new StringBuilder(inLen);

			int rightMostDotIndex = fqClassName.lastIndexOf(DOT);

			if (rightMostDotIndex == -1)
				return fqClassName;

			// length of last segment including the dot
			int lastSegmentLength = inLen - rightMostDotIndex;

			int leftSegments_TargetLen = targetLength - lastSegmentLength;
			if (leftSegments_TargetLen < 0)
				leftSegments_TargetLen = 0;

			int leftSegmentsLen = inLen - lastSegmentLength;

			// maxPossibleTrim denotes the maximum number of characters we aim
			// to trim
			// the actual number of character trimmed may be higher since
			// segments, when
			// reduced, are reduced to just one character
			int maxPossibleTrim = leftSegmentsLen - leftSegments_TargetLen;

			int trimmed = 0;
			boolean inDotState = true;

			int i = 0;
			for (; i < rightMostDotIndex; i++) {
				char c = fqClassName.charAt(i);
				if (c == DOT) {
					// if trimmed too many characters, let us stop
					if (trimmed >= maxPossibleTrim)
						break;
					buf.append(c);
					inDotState = true;
				}
				else {
					if (inDotState) {
						buf.append(c);
						inDotState = false;
					}
					else {
						trimmed++;
					}
				}
			}
			// append from the position of i which may include the last seen DOT
			buf.append(fqClassName.substring(i));
			return buf.toString();
		}

	}

}
