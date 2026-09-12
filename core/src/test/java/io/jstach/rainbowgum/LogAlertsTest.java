package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.ValidationException;

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
		var alerts = alerts();
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
		var alerts = alerts();
		alerts.error(LogAlertsTest.class, "custom message", new RuntimeException("cause"));

		var dump = alerts.dump();
		assertEquals(1, dump.size());
		assertEquals("custom message", dump.get(0).message());
	}

	@Test
	void ringBufferEvictsOldestFirstOnceAtCapacity() {
		var alerts = new DefaultLogAlerts(2);

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
		var alerts = alerts();
		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());

		alerts.clear();

		assertTrue(alerts.dump().isEmpty());
		var stats = alerts.stats();
		assertEquals(2, stats.total());
		assertEquals(0, stats.size());
	}

	@Test
	void constructorRejectsNonPositiveCapacity() {
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new DefaultLogAlerts(0));
	}

	@Test
	void defaultCapacityIs128() {
		assertEquals(128, LogAlerts.DEFAULT_CAPACITY);
		assertEquals(128, alerts().stats().capacity());
	}

	@Test
	void capacityIsReadFromLogPropertiesLoadedPriorInTheBuilder() {
		var props = LogProperties.builder().fromProperties("logging.alerts.capacity=5").build();
		var config = LogConfig.builder().properties(props).build();
		assertEquals(5, config.alerts().stats().capacity());
	}

	@Test
	void badCapacityValueFailsLoudlyInsteadOfSilentlyResolvingToDefault() {
		var props = LogProperties.builder().fromProperties("logging.alerts.capacity=not-a-number").build();
		var e = assertThrows(ValidationException.class, () -> LogConfig.builder().properties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogAlerts:
						Error for property. key: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity], java.lang.NumberFormatException For input string: "not-a-number"
						Tried: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity]""",
				e.getMessage());
	}

	@Test
	void zeroCapacityValueFailsLoudlyViaConstructorValidation() {
		// 0 parses fine as an int (so ofInt()/or(default) let it straight through) -
		// this is specifically catching DefaultLogAlerts's own constructor validation
		// via map(DefaultLogAlerts::new), not the int-parsing validation the
		// not-a-number case above already covers.
		var props = LogProperties.builder().fromProperties("logging.alerts.capacity=0").build();
		var e = assertThrows(ValidationException.class, () -> LogConfig.builder().properties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogAlerts:
						Error for property. key: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity], java.lang.IllegalArgumentException capacity should be greater than 0
						Tried: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity]""",
				e.getMessage());
	}

	@Test
	void negativeCapacityValueFailsLoudlyViaConstructorValidation() {
		var props = LogProperties.builder().fromProperties("logging.alerts.capacity=-1").build();
		var e = assertThrows(ValidationException.class, () -> LogConfig.builder().properties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogAlerts:
						Error for property. key: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity], java.lang.IllegalArgumentException capacity should be greater than 0
						Tried: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity]""",
				e.getMessage());
	}

	@Test
	void unobservedErrorsActionNoneDoesNotDumpOrFail() {
		var props = LogProperties.builder().fromProperties("logging.alerts.unobservedErrorsAction=NONE").build();
		var config = assertDoesNotThrow(() -> LogConfig.builder().properties(props).configurator((c, pass) -> {
			c.alerts().error(LogAlertsTest.class, "boom", new RuntimeException("boom"));
			return true;
		}).build());
		assertEquals(1, config.alerts().dump().size());
		String reported = outputStream.toString(StandardCharsets.UTF_8);
		assertTrue(reported.contains("boom"), () -> "the per-event stderr echo still happens: " + reported);
		assertFalse(reported.contains("alert(s) were recorded before any LogAlerts.Listener was registered"),
				() -> "NONE must not add the summary/backlog-replay lines: " + reported);
	}

	@Test
	void unobservedErrorsActionDumpDefaultsToReportingBacklogButStillStarts() {
		// no logging.alerts.unobservedErrorsAction property set - DUMP is the default.
		var config = assertDoesNotThrow(() -> LogConfig.builder().configurator((c, pass) -> {
			c.alerts().error(LogAlertsTest.class, "boom", new RuntimeException("boom"));
			return true;
		}).build());
		assertEquals(1, config.alerts().dump().size());
		String reported = outputStream.toString(StandardCharsets.UTF_8);
		assertTrue(reported.contains("alert(s) were recorded before any LogAlerts.Listener was registered"),
				() -> "expected the unobserved-backlog summary, got: " + reported);
		// three separate FailsafeAppender.log(...) calls happened: the original
		// per-event echo (from error() itself), the summary line, and the
		// backlog-replay of that same event - each starts a fresh "RAINBOW_GUM" block.
		assertEquals(3, reported.split("RAINBOW_GUM", -1).length - 1,
				() -> "expected original + summary + replay, got: " + reported);
	}

	@Test
	void unobservedErrorsActionFailThrowsAndConfigNeverCompletes() {
		var props = LogProperties.builder().fromProperties("logging.alerts.unobservedErrorsAction=FAIL").build();
		var e = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().properties(props).configurator((c, pass) -> {
					c.alerts().error(LogAlertsTest.class, "boom", new RuntimeException("boom"));
					return true;
				}).build());
		assertEquals("1 alert(s) were recorded before any LogAlerts.Listener was registered and "
				+ "logging.alerts.unobservedErrorsAction=FAIL - refusing to start.", e.getMessage());
		String reported = outputStream.toString(StandardCharsets.UTF_8);
		assertTrue(reported.contains("alert(s) were recorded before any LogAlerts.Listener was registered"),
				() -> "FAIL must still dump before throwing, got: " + reported);
	}

	@Test
	void unobservedErrorsActionBadValueFailsLoudlyInsteadOfSilentlyResolvingToDefault() {
		var props = LogProperties.builder().fromProperties("logging.alerts.unobservedErrorsAction=BOGUS").build();
		var e = assertThrows(PropertyConvertException.class,
				() -> LogConfig.builder().properties(props).configurator((c, pass) -> {
					c.alerts().error(LogAlertsTest.class, "boom", new RuntimeException("boom"));
					return true;
				}).build());
		assertEquals(
				"""
						Error for property. key: 'logging.alerts.unobservedErrorsAction' from PROPERTIES_STRING[logging.alerts.unobservedErrorsAction], java.lang.IllegalArgumentException No enum constant io.jstach.rainbowgum.LogAlerts.UnobservedErrorsAction.BOGUS
						Tried: 'logging.alerts.unobservedErrorsAction' from PROPERTIES_STRING[logging.alerts.unobservedErrorsAction]""",
				e.getMessage());
	}

	@Test
	void aListenerRegisteredByAConfiguratorSuppressesTheUnobservedDump() {
		var config = assertDoesNotThrow(() -> LogConfig.builder().configurator((c, pass) -> {
			c.alerts().addListener(event -> {
			});
			c.alerts().error(LogAlertsTest.class, "boom", new RuntimeException("boom"));
			return true;
		}).build());
		assertEquals(1, config.alerts().dump().size());
		String reported = outputStream.toString(StandardCharsets.UTF_8);
		assertFalse(reported.contains("alert(s) were recorded before any LogAlerts.Listener was registered"),
				() -> "an already-registered listener must suppress the summary/backlog-replay lines: " + reported);
	}

	@Test
	void closeClearsListeners() {
		var alerts = alerts();
		List<String> seen = new ArrayList<>();
		alerts.addListener(e -> seen.add(e.message()));

		alerts.close();
		alerts.error(LogAlertsTest.class, "after close", new RuntimeException());

		assertTrue(seen.isEmpty(), () -> "closed listeners must not be notified, saw: " + seen);
	}

	@Test
	void logConfigProvidesAWorkingAlerts() {
		var config = LogConfig.builder().build();
		config.alerts().error(LogAlertsTest.class, "from config", new RuntimeException());
		assertEquals(1, config.alerts().dump().size());
	}

	private static LogAlerts alerts() {
		return LogConfig.builder().build().alerts();
	}

	@Test
	void listenerIsNotifiedSynchronouslyOnEachError() {
		var alerts = alerts();
		List<String> seen = new ArrayList<>();
		alerts.addListener(e -> seen.add(e.message()));

		alerts.error(LogAlertsTest.class, "first", new RuntimeException());
		alerts.error(LogAlertsTest.class, "second", new RuntimeException());

		assertEquals(List.of("first", "second"), seen);
	}

	@Test
	void closingRegistrationStopsFurtherNotifications() {
		var alerts = alerts();
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
		var alerts = alerts();
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
