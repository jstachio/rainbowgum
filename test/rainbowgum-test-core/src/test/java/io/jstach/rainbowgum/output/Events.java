package io.jstach.rainbowgum.output;

import java.util.ArrayList;
import java.util.List;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.TestLogEventFactory;

/**
 * Shared golden-string event fixtures used by {@link FileOutputTest} and
 * {@code FileOutputPropertiesTest} (in rainbowgum-core, duplicated there since it cannot
 * depend on this module).
 */
enum Events {

	ZERO("", 0), //
	ONE("""
			00:00:00.000 [main] INFO  test - test 0
				"""), //
	TWO("""
			00:00:00.000 [main] INFO  test - test 0
			00:00:00.000 [main] INFO  test - test 1
								""", 2), //
	THREE("""
			00:00:00.000 [main] INFO  test - test 0
			00:00:00.000 [main] INFO  test - test 1
			00:00:00.000 [main] INFO  test - test 2
								""", 3),;

	final String expected;

	private final int count;

	private Events(String expected) {
		this(expected, 1);
	}

	private Events(String expected, int count) {
		this.expected = expected;
		this.count = count;
	}

	LogProperties fileProperties() {
		return LogProperties.StandardProperties.EMPTY;
	}

	System.Logger.Level level() {
		return System.Logger.Level.INFO;
	}

	List<LogEvent> events() {
		List<LogEvent> events = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			var index = i;
			events.add(TestLogEventFactory.of().event("test " + index));
		}
		return events;
	}

	@Override
	public String toString() {
		return "EVENTS_" + name();
	}

}
