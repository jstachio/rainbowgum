package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Confirms every concrete {@link DirectLogAppender} implementation - not just the default
 * one - catches encoder and output failures instead of letting them propagate back to the
 * caller of {@link LogAppender#append(LogEvent)}/
 * {@link LogAppender#append(LogEvent[], int)}, and reports them through the appender's
 * own {@link LogAlerts} instead. Deliberately does not distinguish encoder vs output
 * failures in the alert itself - both land as one appender-level "failed to append"
 * alert.
 */
class AppenderAlertReportingTest {

	static Stream<Arguments> appenderFlags() {
		return Stream.of(Arguments.of(EnumSet.noneOf(AppenderFlag.class), LockThreadLocalBufferLogAppender.class),
				Arguments.of(EnumSet.of(AppenderFlag.REUSE_BUFFER), ReuseBufferLogAppender.class),
				Arguments.of(EnumSet.of(AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER),
						SynchronizedThreadLocalBufferLogAppender.class));
	}

	@ParameterizedTest
	@MethodSource("appenderFlags")
	void outputFailureIsCaughtAndReported(Set<AppenderFlag> flags, Class<?> expectedType) {
		var output = new ListLogOutput();
		output.setConsumer((e, s) -> {
			throw new RuntimeException("output boom");
		});
		var alerts = LogAlerts.of();
		var appender = appender(flags, output, encoder(), alerts);
		assertEquals(expectedType, appender.getClass());

		assertDoesNotThrow(() -> appender.append(TestEventBuilder.of().build(b -> b.message("event"))));

		assertEquals(1, alerts.dump().size());
		assertTrue(alerts.dump().get(0).message().contains("failed to append"));
	}

	@ParameterizedTest
	@MethodSource("appenderFlags")
	void encoderFailureIsCaughtAndReported(Set<AppenderFlag> flags, Class<?> expectedType) {
		var output = new ListLogOutput();
		var throwingEncoder = LogEncoder.of((LogFormatter.EventFormatter) (sb, event) -> {
			throw new RuntimeException("encode boom");
		});
		var alerts = LogAlerts.of();
		var appender = appender(flags, output, throwingEncoder, alerts);
		assertEquals(expectedType, appender.getClass());

		assertDoesNotThrow(() -> appender.append(TestEventBuilder.of().build(b -> b.message("event"))));

		assertEquals(1, alerts.dump().size());
		assertTrue(alerts.dump().get(0).message().contains("failed to append"));
	}

	@ParameterizedTest
	@MethodSource("appenderFlags")
	void batchOutputFailureIsCaughtAndReported(Set<AppenderFlag> flags, Class<?> expectedType) {
		var output = new ListLogOutput();
		output.setConsumer((e, s) -> {
			throw new RuntimeException("output boom");
		});
		var alerts = LogAlerts.of();
		var appender = appender(flags, output, encoder(), alerts);
		assertEquals(expectedType, appender.getClass());

		var events = new LogEvent[] { TestEventBuilder.of().build(b -> b.message("one")),
				TestEventBuilder.of().build(b -> b.message("two")) };

		assertDoesNotThrow(() -> appender.append(events, events.length));

		assertEquals(1, alerts.dump().size());
		assertTrue(alerts.dump().get(0).message().contains("failed to append batch"));
	}

	private static LogEncoder encoder() {
		return LogEncoder.of(LogFormatter.builder().message().build());
	}

	private static LogAppender appender(Set<AppenderFlag> flags, ListLogOutput output, LogEncoder encoder,
			LogAlerts alerts) {
		return DirectLogAppender.of("test", output, encoder, flags, alerts);
	}

}
