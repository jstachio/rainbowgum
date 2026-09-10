package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogProperty.ValidationException;

/**
 * The actual bug this exists to catch: with the old (unvalidated) {@code queueLevel()}/
 * {@code errorLevel()} implementation, {@code QueueEventsRouter.of()} calls
 * {@code queueLevel().value()} as the first constructor argument and
 * {@code errorLevel().value()} as the second - Java evaluates constructor arguments left
 * to right, so if {@code logging.global.queue.level} is malformed,
 * {@code queueLevel().value()} throws immediately and {@code errorLevel()} is never even
 * called, silently never checking whether {@code logging.global.queue.error} is malformed
 * too. Using a shared {@link io.jstach.rainbowgum.LogProperty.Validator} instead means
 * both properties are read and registered before either is asked for its value, so a
 * failure in one no longer hides a failure in the other - both are reported together in
 * one exception.
 * <p>
 * See {@link QueueRouterLevelPropertyInvalidTest} for why this needs its own dedicated,
 * non-reused-fork JVM (this module's {@code pom.xml}) and {@code @Isolated}.
 */
@Isolated
class QueueRouterBothPropertiesInvalidTest {

	@Test
	void testBothQueuePropertiesInvalidAreReportedTogether() {
		System.setProperty(LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY, "BOGUS1");
		System.setProperty(LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY, "BOGUS2");

		var error = assertThrows(ExceptionInInitializerError.class, LogRouter::global);
		var cause = assertInstanceOf(ValidationException.class, error.getCause());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.QueueEventsRouter:
						Error for property. key: 'logging.global.queue.level' from SYSTEM_PROPERTIES[logging.global.queue.level], java.lang.IllegalArgumentException Cannot parse Level from input. input='BOGUS1'
						Tried: 'logging.global.queue.level' from SYSTEM_PROPERTIES[logging.global.queue.level]
						Error for property. key: 'logging.global.queue.error' from SYSTEM_PROPERTIES[logging.global.queue.error], java.lang.IllegalArgumentException Cannot parse Level from input. input='BOGUS2'
						Tried: 'logging.global.queue.error' from SYSTEM_PROPERTIES[logging.global.queue.error]""",
				cause.getMessage());
	}

}
