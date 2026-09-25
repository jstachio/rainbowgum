package io.jstach.rainbowgum.micronaut5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.context.ApplicationContext;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;

/*
 * A real io.micronaut.context.ApplicationContext, not a hand built
 * RainbowGumLoggingSystem, so this also proves the compile time generated
 * $RainbowGumLoggingSystem$Definition bean metadata actually wires up correctly, and
 * that io.micronaut.logging.PropertiesLoggingLevelsConfigurer (bundled in
 * micronaut-context, not something this module writes) really does read
 * "logger.levels.*" from context properties and call setLogLevel(...) on this bean with
 * no extra configuration.
 */
class RainbowGumLoggingSystemTest {

	@Test
	void testApplicationYamlDrivenLevel() {
		try (ApplicationContext ctx = ApplicationContext.run(Map.of("logger.levels.rainbowgum.micronaut5.test.foo",
				"DEBUG", "micronaut.application.name", "rainbowgum-micronaut5-test"))) {

			var system = ctx.getBean(ManagedLoggingSystem.class);
			assertNotNull(system);

			var levelResolver = io.jstach.rainbowgum.RainbowGum.of().config().levelResolver();
			assertEquals(Level.DEBUG, levelResolver.resolveLevel("rainbowgum.micronaut5.test.foo"));

			var logger = system.getLogger("rainbowgum.micronaut5.test.foo");
			assertEquals(io.micronaut.logging.LogLevel.DEBUG, logger.effectiveLevel());
		}
	}

	@Test
	void testProgrammaticSetLogLevelAndRoot() {
		try (ApplicationContext ctx = ApplicationContext
			.run(Map.of("micronaut.application.name", "rainbowgum-micronaut5-test2"))) {

			var system = ctx.getBean(ManagedLoggingSystem.class);

			system.setLogLevel("rainbowgum.micronaut5.test.bar", LogLevel.WARN);
			var levelResolver = io.jstach.rainbowgum.RainbowGum.of().config().levelResolver();
			assertEquals(Level.WARNING, levelResolver.resolveLevel("rainbowgum.micronaut5.test.bar"));

			system.setLogLevel("ROOT", LogLevel.ERROR);
			assertEquals(Level.ERROR, levelResolver.resolveLevel(""));
			assertEquals(LogLevel.ERROR, system.getLogger("ROOT").effectiveLevel());
		}
	}

}
