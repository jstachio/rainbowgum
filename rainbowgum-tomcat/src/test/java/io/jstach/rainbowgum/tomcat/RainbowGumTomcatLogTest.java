package io.jstach.rainbowgum.tomcat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter.RootRouter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Covers both dispatch paths {@link RainbowGumTomcatLog} picks between: the fixed-level
 * fast path ({@link TomcatLevelLog}, used when the router reports the logger's level as
 * not changeable) and {@link ChangeableRainbowGumTomcatLog} (used when it is). Uses the
 * {@code RainbowGumTomcatLog(String, LogRouter.RootRouter)} constructor added
 * specifically so these tests don't have to touch (or lock around) the {@code global()}
 * static singleton - see {@code JDKSetupTest} in rainbowgum-test-jdk for what that looks
 * like when it can't be avoided.
 */
class RainbowGumTomcatLogTest {

	private static final String LOGGER_NAME = "org.apache.tomcat.TestComponent";

	private record Fixture(RainbowGum gum, ListLogOutput output, RootRouter router) implements AutoCloseable {
		@Override
		public void close() {
			gum.close();
		}
	}

	private static Fixture fixture(Level configuredLevel, boolean changeable) {
		var propsBuilder = LogProperties.MutableLogProperties.builder()
			.description("test_props")
			.build()
			.put("logging.level." + LOGGER_NAME, configuredLevel.toString());
		if (changeable) {
			propsBuilder.put("logging.global.change", "true").put("logging.change", "level");
		}
		LogConfig config = LogConfig.builder().properties(propsBuilder).build();
		ListLogOutput output = new ListLogOutput();
		var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).build().start();
		var router = gum.router();
		assertEquals(changeable, router.isChangeable(LOGGER_NAME),
				"test setup should have produced a router.isChangeable() of " + changeable);
		return new Fixture(gum, output, router);
	}

	private static Stream<Arguments> levelsAndChangeable() {
		return Stream.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARNING, Level.ERROR, Level.OFF)
			.flatMap(level -> Stream.of(Arguments.of(level, true), Arguments.of(level, false)));
	}

	@ParameterizedTest
	@MethodSource("levelsAndChangeable")
	void testEnabledChecksAndLogging(Level configuredLevel, boolean changeable) {
		try (var fx = fixture(configuredLevel, changeable)) {
			var log = new RainbowGumTomcatLog(LOGGER_NAME, fx.router());

			assertEquals(enabledFor(Level.TRACE, configuredLevel), log.isTraceEnabled(), "isTraceEnabled");
			assertEquals(enabledFor(Level.DEBUG, configuredLevel), log.isDebugEnabled(), "isDebugEnabled");
			assertEquals(enabledFor(Level.INFO, configuredLevel), log.isInfoEnabled(), "isInfoEnabled");
			assertEquals(enabledFor(Level.WARNING, configuredLevel), log.isWarnEnabled(), "isWarnEnabled");
			assertEquals(enabledFor(Level.ERROR, configuredLevel), log.isErrorEnabled(), "isErrorEnabled");
			assertEquals(enabledFor(Level.ERROR, configuredLevel), log.isFatalEnabled(), "isFatalEnabled");

			assertLogged(fx, () -> log.trace("hello", new RuntimeException("trace boom")), Level.TRACE,
					enabledFor(Level.TRACE, configuredLevel), "hello", "trace boom");
			assertLogged(fx, () -> log.debug("hello", new RuntimeException("debug boom")), Level.DEBUG,
					enabledFor(Level.DEBUG, configuredLevel), "hello", "debug boom");
			assertLogged(fx, () -> log.info("hello", new RuntimeException("info boom")), Level.INFO,
					enabledFor(Level.INFO, configuredLevel), "hello", "info boom");
			assertLogged(fx, () -> log.warn("hello", new RuntimeException("warn boom")), Level.WARNING,
					enabledFor(Level.WARNING, configuredLevel), "hello", "warn boom");
			assertLogged(fx, () -> log.error("hello", new RuntimeException("error boom")), Level.ERROR,
					enabledFor(Level.ERROR, configuredLevel), "hello", "error boom");
			assertLogged(fx, () -> log.fatal("hello", new RuntimeException("fatal boom")), Level.ERROR,
					enabledFor(Level.ERROR, configuredLevel), "hello", "fatal boom");
		}
	}

	@ParameterizedTest
	@MethodSource("levelsAndChangeable")
	void testNullMessageIsNotEmptyString(Level configuredLevel, boolean changeable) {
		try (var fx = fixture(configuredLevel, changeable)) {
			var log = new RainbowGumTomcatLog(LOGGER_NAME, fx.router());
			fx.output().clear();
			log.error(null);
			if (enabledFor(Level.ERROR, configuredLevel)) {
				List<Entry<LogEvent, String>> events = fx.output().events();
				assertEquals(1, events.size());
				assertNull(events.get(0).getKey().messageOrNull(), "a null message should stay null, not \"\"");
			}
			else {
				assertTrue(fx.output().events().isEmpty());
			}
		}
	}

	private static boolean enabledFor(Level methodLevel, Level configuredLevel) {
		return methodLevel.compareTo(configuredLevel) >= 0;
	}

	private static void assertLogged(Fixture fx, Runnable call, Level expectedLevel, boolean expectedEnabled,
			String expectedMessage, String expectedThrowableMessage) {
		fx.output().clear();
		call.run();
		List<Entry<LogEvent, String>> events = fx.output().events();
		if (!expectedEnabled) {
			assertTrue(events.isEmpty(), "expected no event for a disabled level but got: " + events);
			return;
		}
		assertEquals(1, events.size());
		LogEvent event = events.get(0).getKey();
		assertEquals(expectedLevel, event.level());
		assertEquals(LOGGER_NAME, event.loggerName());
		assertEquals(expectedMessage, event.messageOrNull());
		var throwable = event.throwableOrNull();
		assertEquals(expectedThrowableMessage, throwable == null ? null : throwable.getMessage(),
				"throwable should have been passed through, not dropped");
	}

}
