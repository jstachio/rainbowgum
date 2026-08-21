package io.jstach.rainbowgum;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * Records a bounded history of {@link LogResponse.Status} events so that pull based
 * health checks can see not just whether the system is currently okay but how it has been
 * degrading over time (for example how many events have been dropped recently) rather
 * than a single transient {@link LogResponse.Status}.
 * <p>
 * The history is a fixed capacity, oldest-evicted-first buffer: once {@link #capacity()}
 * is reached the oldest {@link StatusEvent} is dropped to make room for the newest one.
 *
 * @apiNote This is currently an internal detail and not extension API.
 */
public sealed interface LogStatusReporter permits DefaultLogStatusReporter {

	/**
	 * Records a status event, possibly evicting the oldest recorded event if
	 * {@link #capacity()} has been reached.
	 * @param event event to record.
	 */
	public void report(StatusEvent event);

	/**
	 * A snapshot of the currently retained events ordered oldest to newest.
	 * @return immutable snapshot.
	 */
	public List<StatusEvent> recent();

	/**
	 * The maximum number of events retained. Once reached the oldest event is dropped for
	 * every new one recorded.
	 * @return capacity.
	 */
	public int capacity();

	/**
	 * Creates a status reporter with the given capacity.
	 * @param capacity maximum number of events to retain. Should be greater than zero.
	 * @return status reporter.
	 */
	static LogStatusReporter of(int capacity) {
		return new DefaultLogStatusReporter(capacity);
	}

	/**
	 * A single recorded {@link LogResponse.Status} along with when and which component
	 * reported it.
	 *
	 * @param timestamp when the status was recorded.
	 * @param type the component type that reported the status.
	 * @param name a name identifying the specific component or origin.
	 * @param status the status reported.
	 */
	public record StatusEvent(Instant timestamp, Class<?> type, String name, LogResponse.Status status) {

		/**
		 * A single recorded {@link LogResponse.Status}.
		 * @param timestamp when the status was recorded.
		 * @param type the component type that reported the status.
		 * @param name a name identifying the specific component or origin.
		 * @param status the status reported.
		 */
		public StatusEvent {
			Objects.requireNonNull(timestamp);
			Objects.requireNonNull(type);
			Objects.requireNonNull(name);
			Objects.requireNonNull(status);
		}

	}

}

final class DefaultLogStatusReporter implements LogStatusReporter {

	private final int capacity;

	private final ArrayDeque<LogStatusReporter.StatusEvent> events;

	DefaultLogStatusReporter(int capacity) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity should be greater than 0");
		}
		this.capacity = capacity;
		this.events = new ArrayDeque<>(capacity);
	}

	@Override
	public synchronized void report(LogStatusReporter.StatusEvent event) {
		if (events.size() >= capacity) {
			events.pollFirst();
		}
		events.addLast(event);
	}

	@Override
	public synchronized List<LogStatusReporter.StatusEvent> recent() {
		return List.copyOf(events);
	}

	@Override
	public int capacity() {
		return capacity;
	}

}
