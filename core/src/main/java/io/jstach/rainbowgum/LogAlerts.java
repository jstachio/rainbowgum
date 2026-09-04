package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
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
	 * Counter name (see {@link #errorCounter(String, long)}) for the global count of log
	 * events dropped without ever being written anywhere - for example an appender
	 * dropping events on reentry. Incremented whenever a drop happens regardless of
	 * whether that particular drop is also logged/alerted, since counting and
	 * alerting/logging are separate concerns.
	 */
	static final String EVENTS_DROPPED_METRIC = "events.dropped";

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
		var currentThread = Thread.currentThread();
		error(LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), Level.ERROR,
				loggerName.getName(), message, KeyValues.of(), throwable));
	}

	/**
	 * Increments a counter for something worth tracking as "this happens and it matters"
	 * but too frequent to record as a discrete {@link #error(LogEvent) alert event} -
	 * incrementing a counter is far cheaper than recording an event (no ring buffer
	 * entry, no listener dispatch per call). Every {@link #error(LogEvent)} call (and
	 * therefore the {@code error(Class, ...)} overloads too) also increments the counter
	 * named after the event's {@link LogEvent#loggerName() logger name}, so counts stay
	 * available even once older alerts have been evicted from the ring buffer. Read
	 * current values back with {@link #counters()}.
	 * <p>
	 * This is intentionally minimal - a future dedicated metrics API is expected to take
	 * over counters like this. Using this method now instead of hand rolling an ad hoc
	 * counter keeps the eventual migration to one call site per counter.
	 * @param name counter name, e.g. {@code "queue.dropped"} or a logger name.
	 * @param increment amount to add, usually {@code 1}.
	 */
	public void errorCounter(String name, long increment);

	/**
	 * A snapshot of every counter recorded via methods like
	 * {@link #errorCounter(String, long)}.
	 * @return immutable snapshot.
	 */
	public List<Counter> counters();

	/**
	 * A single named counter's current value, as returned by {@link #counters()}.
	 *
	 * @param name counter name, as passed to a counter method like
	 * {@link #errorCounter(String, long)}.
	 * @param level how much this counter matters - not a log level routing decision, just
	 * a signal of significance, mirroring the counter method it came from (e.g.
	 * {@link Level#ERROR} for {@link #errorCounter(String, long)}).
	 * @param count current value.
	 */
	record Counter(String name, Level level, long count) {
	}

	/**
	 * A snapshot of the alerts currently held in the ring buffer, oldest first. Alerts
	 * are evicted oldest first once the buffer is at {@link Stats#capacity()}.
	 * @return immutable snapshot.
	 */
	public List<LogEvent> dump();

	/**
	 * Clears the ring buffer. Does not reset {@link Stats#total()} or any counter
	 * recorded via {@link #errorCounter(String, long)} - like a Prometheus/Micrometer
	 * counter, these are meant to be monotonically increasing for the life of the
	 * process; a downstream metrics system computes rate of change rather than relying on
	 * the counter itself being reset.
	 */
	public void clear();

	/**
	 * Stats about the alerts recorded so far.
	 * @return stats.
	 */
	public Stats stats();

	/**
	 * Registers a listener that is notified synchronously, in addition to the alert being
	 * recorded in the ring buffer, every time {@link #error(LogEvent)} is called.
	 * <p>
	 * <strong>Listeners are held with a normal (strong) reference and are not
	 * automatically removed.</strong> This is deliberate: alert listeners are expected to
	 * be few and long lived - typically registered once (e.g. a metrics bridge or an
	 * ops/paging integration) for as long as the owning {@link LogConfig} is - rather
	 * than one per short lived object. A weak reference would risk silently dropping a
	 * listener (a lambda with no other strong reference could be collected almost
	 * immediately) which is the wrong failure mode for something whose entire job is not
	 * losing alerts. Call {@link AutoCloseable#close()} on the returned registration to
	 * unregister deterministically once the caller's own lifecycle ends.
	 * @param listener listener to register.
	 * @return registration; {@link AutoCloseable#close()} unregisters the listener.
	 */
	public AutoCloseable addListener(Listener listener);

	/**
	 * Notified of alerts as they happen. See {@link #addListener(Listener)}.
	 */
	@FunctionalInterface
	interface Listener {

		/**
		 * Called synchronously every time an alert is recorded. Should not throw -
		 * exceptions are caught and reported separately so a broken listener cannot
		 * disrupt alert recording or other listeners.
		 * @param event the alert.
		 */
		void onAlert(LogEvent event);

	}

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

	private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

	private final ConcurrentHashMap<String, LongAdder> errorCounters = new ConcurrentHashMap<>();

	private final LogEventFactory eventFactory = LogEventFactory.of(DefaultLogAlerts.class.getName());

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
		errorCounter(frozen.loggerName(), 1);
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
		for (var listener : listeners) {
			try {
				listener.onAlert(frozen);
			}
			catch (Exception e) {
				FailsafeAppender.INSTANCE.log(eventFactory.event(Level.ERROR, "LogAlerts.Listener threw", e));
			}
		}
		FailsafeAppender.INSTANCE.log(frozen);
	}

	@Override
	public AutoCloseable addListener(Listener listener) {
		listeners.add(listener);
		return () -> listeners.remove(listener);
	}

	@Override
	public void errorCounter(String name, long increment) {
		errorCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public List<Counter> counters() {
		List<Counter> list = new ArrayList<>(errorCounters.size());
		for (var e : errorCounters.entrySet()) {
			list.add(new Counter(e.getKey(), Level.ERROR, e.getValue().sum()));
		}
		return List.copyOf(list);
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
