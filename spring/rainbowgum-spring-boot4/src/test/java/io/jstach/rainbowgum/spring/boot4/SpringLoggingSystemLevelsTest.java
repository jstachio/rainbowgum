package io.jstach.rainbowgum.spring.boot4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LoggerAPI;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Binds Rainbow Gum globally the way Spring Boot's LoggingApplicationListener does.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class SpringLoggingSystemLevelsTest {

	private final LoggingSystem system = new RainbowGumLoggingSystemFactory()
		.getLoggingSystem(getClass().getClassLoader());

	@AfterEach
	void shutdown() {
		Objects.requireNonNull(system.getShutdownHandler()).run();
	}

	private RainbowGum initialize(String... keyValues) {
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			map.put(keyValues[i], keyValues[i + 1]);
		}
		var environment = new StandardEnvironment();
		environment.setConversionService(new ApplicationConversionService());
		environment.getPropertySources().addFirst(new MapPropertySource("test", map));
		system.initialize(new LoggingInitializationContext(environment), null, null);
		return Objects.requireNonNull(RainbowGum.getOrNull());
	}

	private LoggerConfiguration logger(String name) {
		return Objects.requireNonNull(system.getLoggerConfiguration(name));
	}

	@Test
	void levelsSpringBootSetsAtStartupAreAlreadyApplied() {
		initialize("logging.level.com.example", "debug", "debug", "");
		// LoggingApplicationListener's calls for logging.level.*, a group member and the
		// debug loggers.
		system.setLogLevel("com.example", LogLevel.DEBUG);
		system.setLogLevel("org.hibernate.SQL", LogLevel.DEBUG);
		system.setLogLevel("org.springframework.boot", LogLevel.DEBUG);
	}

	@Test
	void debugSetsSpringBootsLoggersAndGroups() {
		initialize("debug", "");
		assertEquals(LogLevel.DEBUG, logger("org.springframework.boot.SpringApplication").getEffectiveLevel());
		assertEquals(LogLevel.DEBUG, logger("org.hibernate.SQL").getEffectiveLevel());
		assertEquals(LogLevel.DEBUG, logger("org.springframework.web").getEffectiveLevel());
	}

	@Test
	void traceSetsOnlyItsOwnLoggers() {
		initialize("trace", "", "logging.level.root", "info");
		assertEquals(LogLevel.TRACE, logger("org.springframework.context").getEffectiveLevel());
		assertEquals(LogLevel.INFO, logger("org.hibernate.SQL").getEffectiveLevel());
	}

	@Test
	void anExplicitLevelWinsOverDebug() {
		initialize("debug", "", "logging.level.org.springframework.boot", "warn");
		assertEquals(LogLevel.WARN, logger("org.springframework.boot").getEffectiveLevel());
	}

	@Test
	void groupsWorkWithoutBeingListed() {
		initialize("logging.group.tomcat", "org.apache.catalina,org.apache.coyote", "logging.level.tomcat", "trace",
				"logging.level.web", "error");
		assertEquals(LogLevel.TRACE, logger("org.apache.coyote.http11").getEffectiveLevel());
		assertEquals(LogLevel.ERROR, logger("org.springframework.http").getEffectiveLevel());
	}

	@Test
	void levelChangesAreRefusedUnlessAllowed() {
		initialize("logging.level.com.example", "debug");
		var e = assertThrows(UnsupportedOperationException.class,
				() -> system.setLogLevel("com.example", LogLevel.WARN));
		assertEquals(
				"Level changes are off for logger 'com.example'. "
						+ "Set logging.change.mode=on and logging.change.logger.{logger}=level to allow them.",
				e.getMessage());
		assertEquals(LogLevel.DEBUG, logger("com.example").getEffectiveLevel());
	}

	@Test
	void allowedLevelChangesApplyAndReset() {
		initialize("logging.level.com.example", "debug", "logging.change.mode", "on",
				"logging.change.logger.com.example", "level");
		system.setLogLevel("com.example", LogLevel.WARN);
		assertEquals(LogLevel.WARN, logger("com.example").getConfiguredLevel());
		assertEquals(LogLevel.WARN, logger("com.example.child").getEffectiveLevel());
		system.setLogLevel("com.example", null);
		assertEquals(LogLevel.DEBUG, logger("com.example").getConfiguredLevel());
	}

	@Test
	void rootLevelChangesUseTheRootName() {
		initialize("logging.level.root", "info", "logging.change.mode", "on", "logging.change.logger", "level");
		system.setLogLevel(LoggingSystem.ROOT_LOGGER_NAME, LogLevel.ERROR);
		assertEquals(LogLevel.ERROR, logger(LoggingSystem.ROOT_LOGGER_NAME).getConfiguredLevel());
		assertEquals(LogLevel.ERROR, logger("com.example").getEffectiveLevel());
	}

	@Test
	void loggerConfigurationsListRootThenLoggersByName() {
		var gum = initialize("logging.level.root", "info", "logging.level.com.b", "debug");
		gum.config().loggerRegistry().registerLoggerName(LoggerAPI.Standard.SLF4J, "com.b");
		gum.config().loggerRegistry().registerLoggerName(LoggerAPI.Standard.SLF4J, "com.a");
		var actual = system.getLoggerConfigurations()
			.stream()
			.map(c -> c.getName() + " " + c.getConfiguredLevel() + " " + c.getEffectiveLevel())
			.toList();
		assertEquals(List.of("ROOT INFO INFO", "com.a null INFO", "com.b DEBUG DEBUG"), actual);
	}

	@Test
	void cleanUpKeepsLoggingUntilTheNextInitialize() {
		var first = initialize();
		assertSame(first, initialize(), "already initialized");
		system.cleanUp();
		assertSame(first, RainbowGum.getOrNull());
		var second = initialize();
		assertNotSame(first, second);
		assertSame(second, RainbowGum.getOrNull());
	}

	@Test
	void theShutdownHandlerClosesInsteadOfAJvmHook() {
		var gum = initialize();
		assertEquals(LogConfig.Globals.ShutdownHookMode.OFF, gum.config().globals().shutdownHook());
		Objects.requireNonNull(system.getShutdownHandler()).run();
		assertNull(RainbowGum.getOrNull());
	}

	@Test
	void theShutdownHookPropertyStillWins() {
		var gum = initialize("logging.global.shutdownHook", "on");
		assertTrue(gum.config().globals().shutdownHook() == LogConfig.Globals.ShutdownHookMode.ON);
	}

}
