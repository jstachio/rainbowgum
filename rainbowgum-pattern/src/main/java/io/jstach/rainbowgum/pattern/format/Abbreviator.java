package io.jstach.rainbowgum.pattern.format;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

interface Abbreviator {

	static final char DOT = '.';

	static final String DISABLE_CACHE_SYSTEM_PROPERTY = "logback.namedConverter.disableCache";

	public String abbreviate(String in);

	public static Abbreviator of(int length) {
		if (length <= 0) {
			return cache(StandardAbbreviator.CLASS_NAME_ONLY);
		}
		return cache(new TargetLengthBasedClassNameAbbreviator(length));
	}

	public static Abbreviator cache(Abbreviator a) {
		if (Boolean.getBoolean(DISABLE_CACHE_SYSTEM_PROPERTY)) {
			return a;
		}
		Cache<String, String> cache = Cache.of(a::abbreviate);
		return new CacheAbbreviator(cache);
	}

	record CacheAbbreviator(Cache<String, String> cache) implements Abbreviator {

		@Override
		public String abbreviate(String in) {
			return cache.value(in);
		}

	}

	enum StandardAbbreviator implements Abbreviator {

		CLASS_NAME_ONLY {

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

		public String abbreviate(String fqClassName) {
			if (fqClassName == null) {
				throw new IllegalArgumentException("Class name may not be null");
			}

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

interface Cache<K, V> {

	public V value(K key);

	public static <K, V> Cache<K, V> of(Function<K, V> function) {
		return new LogbackCache<>(function);
	}

}

class LogbackCache<K, V> extends LinkedHashMap<K, V> implements Cache<K, V> {

	private static final long serialVersionUID = 1050866539278406045L;

	private static final int INITIAL_CACHE_SIZE = 512;

	private static final double LOAD_FACTOR = 0.75; // this is the JDK
													// implementation default

	/**
	 * We don't let the cache map size to go over MAX_ALLOWED_REMOVAL_THRESHOLD elements
	 */
	private static final int MAX_ALLOWED_REMOVAL_THRESHOLD = (int) (2048 * LOAD_FACTOR);

	/**
	 * When the cache miss rate is above 30%, the cache is deemed inefficient.
	 */
	private static final double CACHE_MISSRATE_TRIGGER = 0.3d;

	/**
	 * We should have a sample size of minimal length before computing the cache miss
	 * rate.
	 */
	private static final int MIN_SAMPLE_SIZE = 1024;

	private static final double NEGATIVE = -1;

	private volatile boolean cacheEnabled = true;

	private final Function<K, V> function;

	private volatile int cacheMisses = 0;

	private volatile int totalCalls = 0;

	int removalThreshold;

	CacheMissCalculator cacheMissCalculator = new CacheMissCalculator();

	private final ReentrantLock lock = new ReentrantLock();

	LogbackCache(Function<K, V> function) {
		this(INITIAL_CACHE_SIZE, function);
	}

	LogbackCache(int initialCapacity, Function<K, V> function) {
		super(initialCapacity);
		this.removalThreshold = (int) (initialCapacity * LOAD_FACTOR);
		this.function = function;
	}

	/**
	 * In the JDK tested, this method is called for every map insertion.
	 *
	 */
	@Override
	protected boolean removeEldestEntry(Map.Entry<K, V> entry) {
		if (shouldDoubleRemovalThreshold()) {
			removalThreshold *= 2;

			// int missRate = (int) (cacheMissCalculator.getCacheMissRate() *
			// 100);
			//
			// NamedConverter.this.addInfo("Doubling nameCache removalThreshold
			// to " + removalThreshold
			// + " previous cacheMissRate=" + missRate + "%");
			cacheMissCalculator.updateMilestones();
		}

		if (size() >= removalThreshold) {
			return true;
		}
		else
			return false;
	}

	@Override
	public V value(K key) {
		if (!cacheEnabled) {
			return function.apply(key);
		}
		return _value(key);
	}

	V _value(K fqn) {
		lock.lock();
		try {
			totalCalls++;
			V abbreviated = get(fqn);
			if (abbreviated == null) {
				cacheMisses++;
				abbreviated = function.apply(fqn);
				put(fqn, abbreviated);
			}
			return abbreviated;
		}
		finally {
			lock.unlock();
		}
	}

	void disableCache() {
		if (!cacheEnabled)
			return;
		this.cacheEnabled = false;
		clear();
		// addInfo("Disabling cache at totalCalls=" + totalCalls);
	}

	public double getCacheMissRate() {
		return cacheMissCalculator.getCacheMissRate();
	}

	public int getCacheMisses() {
		return cacheMisses;
	}

	private boolean shouldDoubleRemovalThreshold() {

		double rate = cacheMissCalculator.getCacheMissRate();

		// negative rate indicates insufficient sample size
		if (rate < 0)
			return false;

		if (rate < CACHE_MISSRATE_TRIGGER)
			return false;

		// cannot double removalThreshold is already at max allowed size
		if (this.removalThreshold >= MAX_ALLOWED_REMOVAL_THRESHOLD) {
			this.disableCache();
			return false;
		}

		return true;
	}

	class CacheMissCalculator {

		int totalsMilestone = 0;

		int cacheMissesMilestone = 0;

		void updateMilestones() {
			this.totalsMilestone = LogbackCache.this.totalCalls;
			this.cacheMissesMilestone = LogbackCache.this.cacheMisses;
		}

		double getCacheMissRate() {

			int effectiveTotal = LogbackCache.this.totalCalls - totalsMilestone;

			if (effectiveTotal < MIN_SAMPLE_SIZE) {
				// cache miss rate cannot be negative. With a negative value, we
				// signal the
				// caller of insufficient sample size.
				return NEGATIVE;
			}

			int effectiveCacheMisses = LogbackCache.this.cacheMisses - cacheMissesMilestone;
			return (1.0d * effectiveCacheMisses / effectiveTotal);
		}

	}

}
