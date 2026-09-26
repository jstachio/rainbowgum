package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogReporter.Section;

class LogReporterTest {

	/*
	 * Golden string: no format guarantee to third parties, but still worth pinning here
	 * so an accidental change to the output is caught, same convention already used for
	 * error message golden strings. Sections(COMPONENTS) only, not the builder default:
	 * the default also includes VERSION, which is not a stable value to golden string
	 * across releases.
	 */
	@Test
	void testComponentsOnly() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.COMPONENTS)).build();
		try (var gum = RainbowGum.builder().build()) {
			String actual = reporter.report(gum);
			String expected = """
					Global Properties:
					  logging.global.change = (unset)
					  logging.global.queue.level = (unset)
					  logging.global.queue.error = (unset)
					  logging.global.ansi.disable = (unset)
					  logging.global.appender.reentrantLock = (unset)
					  logging.global.threadlocalDisabled = (unset)
					  logging.global.optimize = (unset)

					Router: default
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: console
					      Type: LockThreadLocalBufferLogAppender
					      Flags: []
					      Output: StdOutOutput (type=CONSOLE_OUT, uri=stdout:///)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8)
					""";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testDefaultSectionsIncludeVersion() {
		var reporter = LogReporter.builder().build();
		try (var gum = RainbowGum.builder().build()) {
			String actual = reporter.report(gum);
			assertTrue(actual.startsWith("Rainbow Gum "), () -> "expected version header, got: " + actual);
			assertTrue(actual.contains("Global Properties:"));
		}
	}

	@Test
	void testEmptySectionsFallsBackToDefault() {
		var reporter = LogReporter.builder().sections(EnumSet.noneOf(Section.class)).build();
		try (var gum = RainbowGum.builder().build()) {
			String actual = reporter.report(gum);
			assertTrue(actual.startsWith("Rainbow Gum "));
		}
	}

	@Test
	void testMetrics() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.METRICS)).build();
		try (var gum = RainbowGum.builder().build()) {
			gum.config().metrics().errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, 3);
			gum.config().metrics().warnCounter(LogMetrics.BUFFER_TRIMMED_METRIC, 12);
			String actual = reporter.report(gum);
			String expected = """
					Metrics:
					  events.dropped = 3 (ERROR)
					  buffer.trimmed = 12 (WARNING)

					""";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testAlertsShowsOnlyMostRecent() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.ALERTS)).maxAlerts(2).build();
		try (var gum = RainbowGum.builder().build()) {
			var alerts = gum.config().alerts();
			alerts.error(LogReporterTest.class, "first", new IllegalStateException());
			alerts.error(LogReporterTest.class, "second", new IllegalStateException());
			alerts.error(LogReporterTest.class, "third", new IllegalStateException());
			String actual = reporter.report(gum);
			assertTrue(actual.startsWith("Alerts (total=3, capacity=128):\n"), actual);
			assertTrue(actual.contains("second"), actual);
			assertTrue(actual.contains("third"), actual);
			assertTrue(!actual.contains("] ERROR io.jstach.rainbowgum.LogReporterTest - first"), actual);
		}
	}

}
