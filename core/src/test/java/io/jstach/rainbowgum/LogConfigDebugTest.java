package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LogConfigDebugTest {

	private final ByteArrayOutputStream output = new ByteArrayOutputStream();

	private final PrintStream printStream = new PrintStream(output);

	private @Nullable String originalDebug;

	@BeforeEach
	void before() {
		originalDebug = System.getProperty(LogProperties.DEBUG_PROPERTY);
		System.setProperty(LogProperties.DEBUG_PROPERTY, "ERROR");
		MetaLog.output = () -> printStream;
	}

	@AfterEach
	void after() {
		if (originalDebug == null) {
			System.clearProperty("logging.debug");
		}
		else {
			System.setProperty(LogProperties.DEBUG_PROPERTY, originalDebug);
		}
		MetaLog.output = () -> System.err;
	}

	@ParameterizedTest
	@ValueSource(strings = { "ERROR", "INFO", "ALL", "true" })
	void dumpsPrePropertiesAlertsWhenProviderFails(String mode) {
		System.setProperty(LogProperties.DEBUG_PROPERTY, mode);
		var failure = new IllegalStateException("provider failed");
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "profile selected");
					throw failure;
				}).build());
		assertSame(failure, thrown);
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfig - LogConfig build failed; dumping 2 alert(s) "
						+ "java.lang.IllegalStateException: provider failed",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - profile selected"), reportedEvents());
	}

	@Test
	void dumpsPrePropertiesAlertsWhenRegularAlertsCannotBeBuilt() {
		var properties = LogProperties.builder().fromProperties("logging.alerts.capacity=0").build();
		var thrown = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "loaded properties");
					return List.of(properties);
				}).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogAlerts:
						Error for property. key: 'logging.alerts.capacity' from PROPERTIES_STRING[logging.alerts.capacity], capacity should be greater than 0
						Tried: 'logging.alerts.capacity' from SYSTEM_PROPERTIES[logging.alerts.capacity], PROPERTIES_STRING[logging.alerts.capacity]""",
				thrown.getMessage());
		assertEquals(List.of("[ERROR] - RAINBOW_GUM - LogConfig - LogConfig build failed; dumping 2 alert(s) "
				+ "io.jstach.rainbowgum.LogProperty$ValidationException: Validation failed for io.jstach.rainbowgum.LogAlerts:",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - loaded properties"), reportedEvents());
	}

	@Test
	void dumpsReplayedAndRegularAlertsWithoutCallingListeners() {
		var failure = new IllegalStateException("configurator failed");
		var listenerCalls = new AtomicInteger();
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "loaded properties");
					return List.of();
				}).configurator((config, pass) -> {
					config.alerts().info(LogConfigDebugTest.class, "configuring");
					config.alerts().addListener(event -> listenerCalls.incrementAndGet());
					throw failure;
				}).build());
		assertSame(failure, thrown);
		assertEquals(0, listenerCalls.get());
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfig - LogConfig build failed; dumping 4 alert(s) "
						+ "java.lang.IllegalStateException: configurator failed",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - loaded properties",
				"[INFO] - RAINBOW_GUM - LogConfig - Adding configurator: unknown Configurator",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - configuring"), reportedEvents());
	}

	@Test
	void errorModeDoesNotDumpAfterSuccessfulBuild() {
		LogConfig.builder().propertiesProvider((registry, alerts) -> {
			alerts.info(LogConfigDebugTest.class, "loaded properties");
			return List.of();
		}).build();
		assertEquals("", output.toString(StandardCharsets.UTF_8));
	}

	@Test
	void allModeDumpsEvenWhenThereAreNoAlerts() {
		System.setProperty(LogProperties.DEBUG_PROPERTY, "ALL");
		LogConfig.builder().build();
		assertEquals(List.of("[INFO] - RAINBOW_GUM - LogConfig - LogConfig built; dumping 0 alert(s)"),
				reportedEvents());
	}

	@ParameterizedTest
	@ValueSource(strings = { "ALL", "true", "INFO", "info", "ERROR", "OFF", "false" })
	void startupReportIsOnlyPrintedAfterStartingInAllMode(String mode) {
		System.setProperty(LogProperties.DEBUG_PROPERTY, mode);
		var config = LogConfig.builder().debug(LogConfig.DebugModeType.ALL).build();
		boolean report = mode.equals("ALL") || mode.equals("true");
		String expected = report || mode.equalsIgnoreCase("INFO")
				? "[INFO] - RAINBOW_GUM - LogConfig - LogConfig built; dumping 0 alert(s)\n" : "";
		assertEquals(expected, output.toString(StandardCharsets.UTF_8));
		var listenerCalls = new AtomicInteger();
		config.alerts().addListener(event -> listenerCalls.incrementAndGet());
		try (var gum = RainbowGum.builder(config).build()) {
			assertEquals(expected, output.toString(StandardCharsets.UTF_8));
			gum.start();
			if (report) {
				expected += """
						[INFO] - RAINBOW_GUM - RainbowGum - Rainbow Gum started:
						Rainbow Gum VERSION_PLACEHOLDER

						Properties: SYSTEM_PROPERTIES
						Global Properties:
						  logging.global.change = (unset)
						  logging.global.queue.level = (unset)
						  logging.global.queue.error = (unset)
						  logging.global.ansi.disable = (unset)
						  logging.global.appender.reentrantLock = (unset)
						  logging.global.threadlocalDisabled = (unset)
						  logging.global.optimize = (unset)
						Debug mode: ALL

						Router: default
						  Publisher: DefaultSyncLogPublisher (synchronous)
						    Appender: console
						      Type: LockThreadLocalBufferLogAppender
						      Flags: []
						      Output: StdOutOutput (type=CONSOLE_OUT, uri=stdout:///)
						      Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8)

						""".replace("VERSION_PLACEHOLDER", RainbowGumVersion.VERSION);
			}
			assertEquals(expected, output.toString(StandardCharsets.UTF_8));
			assertEquals(0, listenerCalls.get());
		}
	}

	@Test
	void trueAliasDumpsAfterSuccessfulBuildWithoutNotifyingListeners() {
		System.setProperty(LogProperties.DEBUG_PROPERTY, "true");
		var listenerCalls = new AtomicInteger();
		LogConfig.builder().propertiesProvider((registry, alerts) -> {
			alerts.info(LogConfigDebugTest.class, "loaded properties");
			return List.of();
		}).configurator((config, pass) -> {
			config.alerts().addListener(event -> listenerCalls.incrementAndGet());
			config.alerts().info(LogConfigDebugTest.class, "configured");
			return true;
		}).build();
		assertEquals(1, listenerCalls.get());
		assertEquals(List.of("[INFO] - RAINBOW_GUM - LogConfig - LogConfig built; dumping 4 alert(s)",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - loaded properties",
				"[INFO] - RAINBOW_GUM - LogConfig - Adding configurator: unknown Configurator",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - configured"), reportedEvents());
	}

	@Test
	void allModeDumpsAfterTheNormalStartPolicy() {
		System.setProperty(LogProperties.DEBUG_PROPERTY, "ALL");
		LogConfig.builder().configurator((config, pass) -> {
			config.alerts().error(LogConfigDebugTest.class, "failure", new IllegalStateException("cause"));
			return true;
		}).build();
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfigDebugTest - failure java.lang.IllegalStateException: cause",
				"[ERROR] - RAINBOW_GUM - DefaultLogAlerts - 2 alert(s) were recorded before any LogAlerts.Listener was registered - "
						+ "dumping the backlog now since nothing else will see it:",
				"[INFO] - RAINBOW_GUM - LogConfig - Adding configurator: unknown Configurator",
				"[ERROR] - RAINBOW_GUM - LogConfigDebugTest - failure java.lang.IllegalStateException: cause",
				"[INFO] - RAINBOW_GUM - LogConfig - LogConfig built; dumping 2 alert(s)",
				"[INFO] - RAINBOW_GUM - LogConfig - Adding configurator: unknown Configurator",
				"[ERROR] - RAINBOW_GUM - LogConfigDebugTest - failure java.lang.IllegalStateException: cause"),
				reportedEvents());
	}

	@Test
	void doesNotDumpWhenDebugIsDisabled() {
		var failure = new IllegalStateException("provider failed");
		for (var mode : List.of("OFF", "false")) {
			System.setProperty(LogProperties.DEBUG_PROPERTY, mode);
			var thrown = assertThrows(IllegalStateException.class,
					() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
						alerts.info(LogConfigDebugTest.class, "profile selected");
						throw failure;
					}).build());
			assertSame(failure, thrown);
			assertEquals("", output.toString(StandardCharsets.UTF_8));
		}
	}

	@Test
	void invalidDebugModeFailsInsteadOfSilentlyDisablingTheDump() {
		System.setProperty(LogProperties.DEBUG_PROPERTY, "BOGUS");
		var thrown = assertThrows(LogProperty.ValidationException.class, () -> LogConfig.builder().build());
		assertEquals("", output.toString(StandardCharsets.UTF_8));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$Builder:
						Error for property. key: 'logging.debug' from SYSTEM_PROPERTIES[logging.debug], 'BOGUS' is not a valid value for io.jstach.rainbowgum.LogConfig.DebugModeType. Valid values: 'off', 'error', 'info', 'all', 'true', 'false'""",
				thrown.getMessage());
	}

	@Test
	void builderDebugModeIsUsedWithoutAnExternalOverride() {
		System.clearProperty("logging.debug");
		var failure = new IllegalStateException("provider failed");
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder()
					.debug(LogConfig.DebugModeType.ERROR)
					.propertiesProvider((registry, alerts) -> {
						alerts.info(LogConfigDebugTest.class, "profile selected");
						throw failure;
					})
					.build());
		assertSame(failure, thrown);
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfig - LogConfig build failed; dumping 2 alert(s) "
						+ "java.lang.IllegalStateException: provider failed",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - profile selected"), reportedEvents());
	}

	@Test
	void systemPropertyOverridesBuilderDebugMode() {
		var failure = new IllegalStateException("provider failed");
		System.setProperty(LogProperties.DEBUG_PROPERTY, "OFF");
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().debug(LogConfig.DebugModeType.ALL).propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "profile selected");
					throw failure;
				}).build());
		assertSame(failure, thrown);
		assertEquals("", output.toString(StandardCharsets.UTF_8));

		System.setProperty(LogProperties.DEBUG_PROPERTY, "ALL");
		thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().debug(LogConfig.DebugModeType.OFF).propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "profile selected");
					throw failure;
				}).build());
		assertSame(failure, thrown);
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfig - LogConfig build failed; dumping 2 alert(s) "
						+ "java.lang.IllegalStateException: provider failed",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - profile selected"), reportedEvents());
	}

	@Test
	void doesNotDuplicateDumpWhenStartRejectsUnobservedErrors() {
		var properties = LogProperties.builder().fromProperties("logging.alerts.unobservedErrorsAction=FAIL").build();
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "profile selected");
					return List.of(properties);
				}).configurator((config, pass) -> {
					config.alerts().error(LogConfigDebugTest.class, "failure", new IllegalStateException("cause"));
					return true;
				}).build());
		assertEquals("4 alert(s) were recorded before any LogAlerts.Listener was registered and "
				+ "logging.alerts.unobservedErrorsAction=FAIL - refusing to start.", thrown.getMessage());
		assertEquals(List.of(
				"[ERROR] - RAINBOW_GUM - LogConfigDebugTest - failure java.lang.IllegalStateException: cause",
				"[ERROR] - RAINBOW_GUM - DefaultLogAlerts - 4 alert(s) were recorded before any LogAlerts.Listener was registered - "
						+ "dumping the backlog now since nothing else will see it:",
				"[INFO] - RAINBOW_GUM - LogConfig - Loading properties from unknown PropertiesProvider",
				"[INFO] - RAINBOW_GUM - LogConfigDebugTest - profile selected",
				"[INFO] - RAINBOW_GUM - LogConfig - Adding configurator: unknown Configurator",
				"[ERROR] - RAINBOW_GUM - LogConfigDebugTest - failure java.lang.IllegalStateException: cause"),
				reportedEvents());
	}

	@Test
	void reportingFailureDoesNotReplaceBuildFailure() {
		MetaLog.output = () -> {
			throw new IllegalStateException("output failed");
		};
		var failure = new IllegalStateException("provider failed");
		var thrown = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().propertiesProvider((registry, alerts) -> {
					alerts.info(LogConfigDebugTest.class, "profile selected");
					throw failure;
				}).build());
		assertSame(failure, thrown);
		assertEquals(1, thrown.getSuppressed().length);
		assertEquals("output failed", thrown.getSuppressed()[0].getMessage());
	}

	private List<String> reportedEvents() {
		return output.toString(StandardCharsets.UTF_8).lines().filter(line -> line.startsWith("[")).toList();
	}

}
