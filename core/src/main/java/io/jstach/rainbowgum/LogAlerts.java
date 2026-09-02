package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Alerts (sometimes called errors or status events elsewhere) are for reporting problems
 * with the logging system itself rather than application logging - for example an
 * appender failing to write, or an async publisher's queue overflowing. Because logging
 * itself may be broken these are not routed through the normal {@link LogRouter} but are
 * instead kept in a small in memory ring buffer (see {@link #dump()} and
 * {@link #stats()}) as well as immediately reported (currently to stderr).
 * <p>
 * An instance is available from every {@link LogConfig#alerts()}. Components that are
 * {@linkplain LogProvider provided} config, or that are
 * {@linkplain LogLifecycle#start( LogConfig) started} with config, should prefer
 * capturing {@code config.alerts()} over reaching for global state.
 *
 * @see LogConfig#alerts()
 */
public sealed interface LogAlerts permits DefaultLogAlerts {

	/**
	 * Default capacity of the alert ring buffer.
	 */
	static final int DEFAULT_CAPACITY = 100;

	/**
	 * Records an alert.
	 * @param event event describing the alert. {@link Level#ERROR} or higher is expected
	 * but not enforced.
	 */
	public void error(LogEvent event);

	/**
	 * Records an alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param throwable cause of the alert.
	 */
	default void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Records an alert.
	 * @param loggerName usually the class where the alert originated.
	 * @param message alert message.
	 * @param throwable cause of the alert.
	 */
	default void error(Class<?> loggerName, String message, Throwable throwable) {
		error(LogEvent.of(Level.ERROR, loggerName.getName(), message, throwable));
	}

	/**
	 * A snapshot of the alerts currently held in the ring buffer, oldest first. Alerts
	 * are evicted oldest first once the buffer is at {@link Stats#capacity()}.
	 * @return immutable snapshot.
	 */
	public List<LogEvent> dump();

	/**
	 * Clears the ring buffer. Does not reset {@link Stats#total()}.
	 */
	public void clear();

	/**
	 * Stats about the alerts recorded so far.
	 * @return stats.
	 */
	public Stats stats();

	/**
	 * Creates alerts backed by a ring buffer of the {@link #DEFAULT_CAPACITY default
	 * capacity}.
	 * @return alerts.
	 */
	public static LogAlerts of() {
		return of(DEFAULT_CAPACITY);
	}

	/**
	 * Creates alerts backed by a ring buffer of the given capacity.
	 * @param capacity maximum number of alerts to retain. Older alerts are evicted first
	 * once capacity is reached.
	 * @return alerts.
	 */
	public static LogAlerts of(int capacity) {
		return new DefaultLogAlerts(capacity);
	}

	/**
	 * Stats about alerts recorded.
	 *
	 * @param total total number of alerts ever recorded, including ones since evicted
	 * from the ring buffer.
	 * @param size number of alerts currently held in the ring buffer.
	 * @param capacity maximum number of alerts the ring buffer holds.
	 */
	record Stats(long total, int size, int capacity) {
	}

}

final class DefaultLogAlerts implements LogAlerts {

	private final LogEvent[] ring;

	private int start = 0;

	private int size = 0;

	private final AtomicLong total = new AtomicLong();

	private final ReentrantLock lock = new ReentrantLock();

	DefaultLogAlerts(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity should be greater than 0");
		}
		this.ring = new LogEvent[capacity];
	}

	@Override
	public void error(LogEvent event) {
		var frozen = event.freeze();
		total.incrementAndGet();
		lock.lock();
		try {
			if (size < ring.length) {
				ring[(start + size) % ring.length] = frozen;
				size++;
			}
			else {
				ring[start] = frozen;
				start = (start + 1) % ring.length;
			}
		}
		finally {
			lock.unlock();
		}
		FailsafeAppender.INSTANCE.log(frozen);
	}

	@Override
	public List<LogEvent> dump() {
		lock.lock();
		try {
			List<LogEvent> list = new ArrayList<>(size);
			for (int i = 0; i < size; i++) {
				var e = ring[(start + i) % ring.length];
				if (e != null) {
					list.add(e);
				}
			}
			return List.copyOf(list);
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void clear() {
		lock.lock();
		try {
			Arrays.fill(ring, null);
			start = 0;
			size = 0;
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public Stats stats() {
		lock.lock();
		try {
			return new Stats(total.get(), size, ring.length);
		}
		finally {
			lock.unlock();
		}
	}

}
