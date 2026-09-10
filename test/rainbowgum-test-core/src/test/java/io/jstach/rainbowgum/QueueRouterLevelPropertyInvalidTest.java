package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogProperty.ValidationException;

/**
 * {@code QueueEventsRouter.of()} runs exactly once per JVM, inside
 * {@code GlobalLogRouter}'s own enum {@code <clinit>}, triggered by the first touch of
 * {@link LogRouter#global()} anywhere in that JVM's lifetime. A failure there permanently
 * poisons the class for the rest of the JVM - every later touch throws
 * {@code NoClassDefFoundError} instead of the real cause, not
 * {@code ExceptionInInitializerError} again - so this test (and its sibling,
 * {@link QueueRouterBothPropertiesInvalidTest}) must be the only thing that ever touches
 * {@link LogRouter#global()}/{@code GlobalLogRouter} in whatever JVM they run in. See
 * this module's {@code pom.xml} for the dedicated, non-reused-fork Surefire execution
 * both are scoped to (Surefire's default {@code reuseForks=true} would otherwise share
 * one JVM with every other test class in this module, some of which may have already
 * touched {@code GlobalLogRouter} with real system properties before this test ever
 * runs). {@code @Isolated} is a second guard against the same problem under the
 * (currently opt-in, {@code -Pfast}) JUnit5 class-concurrency profile.
 */
@Isolated
class QueueRouterLevelPropertyInvalidTest {

	@Test
	void testQueueLevelPropertyInvalidThrowsValidationException() {
		System.setProperty(LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY, "BOGUS_LEVEL");

		var error = assertThrows(ExceptionInInitializerError.class, LogRouter::global);
		var cause = assertInstanceOf(ValidationException.class, error.getCause());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.QueueEventsRouter:
						Error for property. key: 'logging.global.queue.level' from SYSTEM_PROPERTIES[logging.global.queue.level], java.lang.IllegalArgumentException Cannot parse Level from input. input='BOGUS_LEVEL'
						Tried: 'logging.global.queue.level' from SYSTEM_PROPERTIES[logging.global.queue.level]""",
				cause.getMessage());
	}

}
