package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * Mutates the shared static MetaLog.output field - see MetaLogTest's identical note for
 * why @Isolated/@Execution(SAME_THREAD) are needed under the "fast" profile's parallel
 * test execution.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LogAlertsTest {

	ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

	PrintStream ps = new PrintStream(outputStream);

	@BeforeEach
	void before() {
		MetaLog.output = () -> ps;
	}

	@AfterEach
	void after() {
		MetaLog.output = () -> System.err;
	}

	@Test
	@SuppressWarnings("StringSplitter")
	void errorRecordsIntoDumpAndStillReportsToStderr() {
		var alerts = LogAlerts.of();
		alerts.error(LogAlertsTest.class, new RuntimeException("expected"));

		var dump = alerts.dump();
		assertEquals(1, dump.size());
		assertEquals("expected", dump.get(0).message());

		var stats = alerts.stats();
		assertEquals(1, stats.total());
		assertEquals(1, stats.size());

		String reported = outputStream.toString(StandardCharsets.UTF_8).split("\n")[0];
		assertEquals("[ERROR] - RAINBOW_GUM expected java.lang.RuntimeException: expected", reported);
	}

	@Test
	void errorWithMessageOverload() {
		var alerts = LogAlerts.of();
		alerts.error(LogAlertsTest.class, "custom message", new RuntimeException("cause"));

		var dump = alerts.dump();
		assertEquals(1, dump.size());
		assertEquals("custom message", dump.get(0).message());
	}

	@Test
	void ringBufferEvictsOldestFirstOnceAtCapacity() {
		var alerts = LogAlerts.of(2);

		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());
		alerts.error(LogAlertsTest.class, "third", new RuntimeException());

		List<String> messages = alerts.dump().stream().map(LogEvent::message).toList();
		assertEquals(List.of("second", "third"), messages);

		var stats = alerts.stats();
		assertEquals(3, stats.total());
		assertEquals(2, stats.size());
		assertEquals(2, stats.capacity());
	}

	@Test
	void clearEmptiesRingBufferButKeepsTotal() {
		var alerts = LogAlerts.of();
		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());

		alerts.clear();

		assertTrue(alerts.dump().isEmpty());
		var stats = alerts.stats();
		assertEquals(2, stats.total());
		assertEquals(0, stats.size());
	}

	@Test
	void ofRejectsNonPositiveCapacity() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> LogAlerts.of(0));
	}

	@Test
	void logConfigProvidesAWorkingAlerts() {
		var config = LogConfig.builder().build();
		config.alerts().error(LogAlertsTest.class, "from config", new RuntimeException());
		assertEquals(1, config.alerts().dump().size());
	}

	@Test
	void listenerIsNotifiedSynchronouslyOnEachError() {
		var alerts = LogAlerts.of();
		List<String> seen = new ArrayList<>();
		alerts.addListener(e -> seen.add(e.message()));

		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());

		assertEquals(List.of("first", "second"), seen);
	}

	@Test
	void closingRegistrationStopsFurtherNotifications() {
		var alerts = LogAlerts.of();
		List<String> seen = new ArrayList<>();
		var registration = alerts.addListener(e -> seen.add(e.message()));

		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		try {
			registration.close();
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());

		assertEquals(List.of("first"), seen);
	}

	@Test
	@SuppressWarnings("StringSplitter")
	void aThrowingListenerDoesNotStopRecordingOrOtherListeners() {
		var alerts = LogAlerts.of();
		List<String> seen = new ArrayList<>();
		alerts.addListener(e -> {
			throw new RuntimeException("listener boom");
		});
		alerts.addListener(e -> seen.add(e.message()));

		alerts.error(LogAlertsTest.class, "first", new RuntimeException());

		assertEquals(List.of("first"), seen);
		assertEquals(1, alerts.dump().size());
		String reported = outputStream.toString(StandardCharsets.UTF_8).split("\n")[0];
		assertTrue(reported.contains("LogAlerts.Listener threw"),
				() -> "expected listener failure to be reported, got: " + reported);
	}

}
