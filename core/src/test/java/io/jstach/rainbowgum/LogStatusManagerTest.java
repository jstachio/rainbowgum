package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogResponse.Status;
import io.jstach.rainbowgum.LogStatusManager.StatusEvent;

class LogStatusManagerTest {

	@Test
	void capacityMustBePositive() {
		assertThrows(IllegalArgumentException.class, () -> LogStatusManager.of(0));
		assertThrows(IllegalArgumentException.class, () -> LogStatusManager.of(-1));
	}

	@Test
	void recentIsEmptyInitially() {
		var manager = LogStatusManager.of(3);
		assertEquals(List.of(), manager.recent());
	}

	@Test
	void recentIsOrderedOldestToNewest() {
		var manager = LogStatusManager.of(3);
		var first = event("first");
		var second = event("second");
		manager.add(first);
		manager.add(second);
		assertEquals(List.of(first, second), manager.recent());
	}

	@Test
	void oldestIsDroppedOnceCapacityIsReached() {
		var manager = LogStatusManager.of(2);
		var first = event("first");
		var second = event("second");
		var third = event("third");
		manager.add(first);
		manager.add(second);
		manager.add(third);
		assertEquals(List.of(second, third), manager.recent());
		assertEquals(2, manager.capacity());
	}

	private static StatusEvent event(String message) {
		return new StatusEvent(Instant.now(), LogStatusManagerTest.class, message, new Status.ErrorStatus(message));
	}

}
