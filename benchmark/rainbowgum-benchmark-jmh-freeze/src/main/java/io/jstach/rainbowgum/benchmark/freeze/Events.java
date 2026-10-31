package io.jstach.rainbowgum.benchmark.freeze;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.UUID;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

/**
 * Message arguments the benchmarks log with, as an application would pass them to SLF4J.
 */
public enum Events {

	/**
	 * Two String arguments.
	 */
	STRINGS,
	/**
	 * Two boxed numbers.
	 */
	NUMBERS,
	/**
	 * Two application objects whose toString builds a string.
	 */
	OBJECTS,
	/**
	 * Four arguments of mixed types, passed as an array.
	 */
	MIXED_FOUR;

	static final Instant TIMESTAMP = Instant.ofEpochSecond(1_700_000_000L);

	static final String LOGGER = "com.example.orders.OrderController";

	static final String NAME = "alice";

	static final String PATH = "/api/orders/123";

	static final Integer ROWS = 1234;

	static final Long MILLIS = 98765L;

	static final UUID ID = UUID.fromString("3f1c2b8e-9d4a-4e51-a7c3-0b6f5d2e8a91");

	static final User USER = new User("alice", 42);

	static final Order ORDER = new Order(123, "shipped");

	/**
	 * Creates the event a logging call with these arguments creates.
	 * @return event not yet frozen.
	 */
	LogEvent create() {
		var f = StandardMessageFormatter.SLF4J;
		return switch (this) {
			case STRINGS -> LogEvent.ofTwoArgs(TIMESTAMP, "main", 1, Level.INFO, LOGGER, "User {} requested {}",
					KeyValues.of(), f, NAME, PATH);
			case NUMBERS -> LogEvent.ofTwoArgs(TIMESTAMP, "main", 1, Level.INFO, LOGGER, "Loaded {} rows in {} ms",
					KeyValues.of(), f, ROWS, MILLIS);
			case OBJECTS -> LogEvent.ofTwoArgs(TIMESTAMP, "main", 1, Level.INFO, LOGGER, "User {} updated {}",
					KeyValues.of(), f, USER, ORDER);
			case MIXED_FOUR ->
				LogEvent.ofAll(TIMESTAMP, "main", 1, Level.INFO, LOGGER, "User {} order {} loaded {} rows for {}",
						KeyValues.of(), null, f, new Object[] { NAME, ID, ROWS, USER });
		};
	}

	static final class User {

		private final String name;

		private int visits;

		User(String name, int visits) {
			this.name = name;
			this.visits = visits;
		}

		@Override
		public String toString() {
			return "User[name=" + name + ", visits=" + visits + "]";
		}

	}

	static final class Order {

		private final long id;

		private String status;

		Order(long id, String status) {
			this.id = id;
			this.status = status;
		}

		@Override
		public String toString() {
			return "Order[id=" + id + ", status=" + status + "]";
		}

	}

}
