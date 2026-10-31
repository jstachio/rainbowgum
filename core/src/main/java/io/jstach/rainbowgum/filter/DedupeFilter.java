package io.jstach.rainbowgum.filter;

import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

final class DedupeFilter implements Filter {

	static final int DEFAULT_REPEATS = 5;

	static final int DEFAULT_CACHE_SIZE = 100;

	/**
	 * {@link java.lang.System.Logger.Level#WARNING warning}, so errors are always kept
	 */
	static final Level DEFAULT_LEVEL = Level.WARNING;

	private final int repeats;

	private final int severity;

	private final ReentrantLock lock = new ReentrantLock();

	private final Map<String, Count> counts;

	private DedupeFilter(int repeats, int cacheSize, Level level) {
		this.repeats = repeats;
		this.severity = level.getSeverity();
		this.counts = new LinkedHashMap<>(16, 0.75f, true) {

			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Count> eldest) {
				return size() > cacheSize;
			}

		};
	}

	/**
	 * Drops an event once its unformatted message has been seen more than
	 * <code>repeats</code> times after the first. Messages with arguments are compared
	 * before the arguments are filled in. Only the <code>cacheSize</code> most recently
	 * seen messages are remembered, and a forgotten message counts from the start again.
	 * Events more severe than <code>level</code> are always kept.
	 * @param name route name.
	 * @param filter name the filter is selected with.
	 * @param repeats times a message is kept after its first.
	 * @param cacheSize messages remembered.
	 * @param level the most severe level that is deduplicated.
	 * @return filter.
	 */
	@LogConfigurable(name = "DedupeFilterBuilder", prefix = LogProperties.FILTER_PREFIX)
	static Filter of(@LogConfigurable.KeyParameter String name, @LogConfigurable.KeyParameter String filter,
			@LogConfigurable.DefaultParameter("DEFAULT_REPEATS") Integer repeats,
			@LogConfigurable.DefaultParameter("DEFAULT_CACHE_SIZE") Integer cacheSize,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("parseLevel") Level level) {
		if (repeats < 0) {
			throw new IllegalArgumentException("repeats must not be negative.");
		}
		if (cacheSize < 1) {
			throw new IllegalArgumentException("cacheSize must be positive.");
		}
		return new DedupeFilter(repeats, cacheSize, level);
	}

	static Level parseLevel(String value) {
		return LevelResolver.parseLevel(value);
	}

	@Override
	public @Nullable LogEvent filterOrNull(LogEvent event) {
		if (event.level().getSeverity() > severity) {
			return event;
		}
		String message = event.message();
		lock.lock();
		try {
			var count = counts.computeIfAbsent(message, m -> new Count());
			if (count.value > repeats) {
				return null;
			}
			count.value++;
			return event;
		}
		finally {
			lock.unlock();
		}
	}

	private static final class Count {

		int value;

	}

}
