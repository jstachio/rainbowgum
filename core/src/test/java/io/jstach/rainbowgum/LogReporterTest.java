package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogReporter.Section;

class LogReporterTest {

	@ParameterizedTest
	@EnumSource(OutputUri.class)
	void outputUriRedactsCredentials(OutputUri test) {
		URI uri = URI.create(test.input);
		var output = new UriOutput(uri);
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.COMPONENTS)).build();
		try (var gum = RainbowGum.builder(config)
			.route(route -> route.appender("custom", appender -> appender.output(output)))
			.build()) {
			assertEquals("""
					Properties: EMPTY
					Global Properties:
					  logging.global.change = (unset)
					  logging.global.queue.level = (unset)
					  logging.global.queue.error = (unset)
					  logging.global.ansi.disable = (unset)
					  logging.global.appender.reentrantLock = (unset)
					  logging.global.threadlocalDisabled = (unset)
					  logging.global.optimize = (unset)
					Debug mode: OFF

					Router: default
					  Publisher: DefaultSyncLogPublisher (synchronous)
					    Appender: custom
					      Type: LockThreadLocalBufferLogAppender
					      Flags: []
					      Output: UriOutput (type=MEMORY, uri=%s)
					      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8)
					""".formatted(test.expected), reporter.report(gum));
			assertEquals(uri, output.uri());
		}
	}

	enum OutputUri {

		PASSWORD_QUERY("custom:///?password=kenny", "custom:///?password=<REDACTED>"),
		SENSITIVE_KEY_VARIANTS("custom:///path?private_key=kenny&clientSecret=kenny&api.key=kenny&access-key=kenny",
				"custom:///path?private_key=<REDACTED>&clientSecret=<REDACTED>&api.key=<REDACTED>&access-key=<REDACTED>"),
		ENCODED_KEYS("custom:///path?%70assword=kenny&api%5Fkey=kenny",
				"custom:///path?%70assword=<REDACTED>&api%5Fkey=<REDACTED>"),
		REPEATED_KEYS("custom:///path?password=kenny&password=other,secret=third&plain=a%26b%3Dc",
				"custom:///path?password=<REDACTED>&password=<REDACTED>,secret=<REDACTED>&plain=a%26b%3Dc"),
		EMPTY_VALUES_AND_FLAGS("custom:///path?password=&token&flag&plain=",
				"custom:///path?password=<REDACTED>&token&flag&plain="),
		USER_INFO("custom://alice:kenny@example.com:123/path?password=kenny&plain=hello#fragment",
				"custom://<REDACTED>@example.com:123/path?password=<REDACTED>&plain=hello#fragment"),
		USERNAME_ONLY("custom://kenny@example.com/path", "custom://<REDACTED>@example.com/path"),
		REGISTRY_AUTHORITY("custom://alice:kenny@my_host/path", "custom://<REDACTED>@my_host/path"),
		ORDINARY_PARAMETERS("custom:///a%20b?plain=%26%3D%20&mode=append#hello%20there",
				"custom:///a%20b?plain=%26%3D%20&mode=append#hello%20there"),
		EMPTY_QUERY("custom:///?#fragment", "custom:///?#fragment"), NO_QUERY("custom:///path", "custom:///path"),
		NO_AUTHORITY("custom:/path?token=kenny", "custom:/path?token=<REDACTED>"), RELATIVE_AUTHORITY(
				"//alice:kenny@example.com/path?token=kenny", "//<REDACTED>@example.com/path?token=<REDACTED>");

		final String input;

		final String expected;

		OutputUri(String input, String expected) {
			this.input = input;
			this.expected = expected;
		}

	}

	static final class UriOutput extends LogOutput.AbstractOutputStreamOutput {

		UriOutput(URI uri) {
			super(uri, new ByteArrayOutputStream());
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

	}

	@Test
	void reportableToStringUsesReportableDescriptionAndFallsBackForLambdas() {
		LogReporter.Reportable reportable = out -> out.append("named component");
		assertEquals("named component", LogReporter.Reportable.toString(reportable, "unknown component"));

		Runnable lambda = () -> {
		};
		assertEquals("unknown component", LogReporter.Reportable.toString(lambda, "unknown component"));
	}

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
		var config = LogConfig.builder().debug(LogConfig.DebugModeType.ERROR).build();
		try (var gum = RainbowGum.builder(config).build()) {
			String actual = reporter.report(gum);
			String expected = """
					Properties: SYSTEM_PROPERTIES
					Global Properties:
					  logging.global.change = (unset)
					  logging.global.queue.level = (unset)
					  logging.global.queue.error = (unset)
					  logging.global.ansi.disable = (unset)
					  logging.global.appender.reentrantLock = (unset)
					  logging.global.threadlocalDisabled = (unset)
					  logging.global.optimize = (unset)
					Debug mode: ERROR

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
			assertTrue(actual.startsWith("Alerts (total=3, capacity=512):\n"), actual);
			assertTrue(actual.contains("second"), actual);
			assertTrue(actual.contains("third"), actual);
			assertTrue(!actual.contains("] ERROR io.jstach.rainbowgum.LogReporterTest - first"), actual);
		}
	}

	@Test
	void testLoggers() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.LOGGERS)).build();
		try (var gum = RainbowGum.builder().build()) {
			var registry = gum.config().loggerRegistry();
			registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Bar");
			registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");
			String actual = reporter.report(gum);
			String expected = """
					Loggers:
					  com.example.Bar = ALL
					  com.example.Foo = ALL
					""";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testLoggersWithoutLevels() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.LOGGERS)).loggerLevels(false).build();
		try (var gum = RainbowGum.builder().build()) {
			gum.config().loggerRegistry().registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");
			String actual = reporter.report(gum);
			String expected = """
					Loggers:
					  com.example.Foo
					""";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testFacades() {
		var reporter = LogReporter.builder().sections(EnumSet.of(Section.FACADES)).build();
		try (var gum = RainbowGum.builder().build()) {
			var registry = gum.config().loggerRegistry();
			registry.registerLoggerName(LoggerAPI.Standard.JUL, "com.example.Foo");
			registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Bar");
			registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Baz");
			String actual = reporter.report(gum);
			String expected = """
					Facades:
					  JUL
					  SLF4J
					""";
			assertEquals(expected, actual);
		}
	}

}
