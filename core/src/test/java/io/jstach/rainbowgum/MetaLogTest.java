package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * Mutates the shared static MetaLog.output field, also mutated by several other test
 * classes (LogAppenderFlagTest, DefaultAppenderSelectionTest, AppenderAsModeReentryTest,
 * LogAlertsTest) - @Isolated keeps this from racing any of them under parallel test
 * execution (see the "fast" Maven profile), and SAME_THREAD keeps this class's own
 * methods from racing each other.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class MetaLogTest {

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
	void testError() {
		MetaLog.error(MetaLogTest.class, new RuntimeException("expected"));
		String actual = outputStream.toString(StandardCharsets.UTF_8).split("\n")[0];
		assertEquals("[ERROR] - RAINBOW_GUM - MetaLogTest - expected java.lang.RuntimeException: expected", actual);
	}

	@Test
	void eventLevelIsNotFilteredOrRelabeled() {
		MetaLog.error(LogEventFactory.of("test").eventNoArg(Level.INFO, "startup info", KeyValues.of(), null));
		assertEquals("[INFO] - RAINBOW_GUM - test - startup info" + System.lineSeparator(),
				outputStream.toString(StandardCharsets.UTF_8));
	}

	@Test
	void queueErrorLevelControlsOutput() {
		var originalQueueLevel = System.getProperty(LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY);
		var originalErrorLevel = System.getProperty(LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY);
		System.setProperty(LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY, "INFO");
		System.setProperty(LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY, "WARNING");
		try {
			var factory = LogEventFactory.of("test");
			var router = QueueEventsRouter.of();
			router.log(factory.eventNoArg(Level.INFO, "queued only", KeyValues.of(), null));
			router.log(factory.eventNoArg(Level.WARNING, "reported warning", KeyValues.of(), null));
			assertEquals("[WARN] - RAINBOW_GUM - test - reported warning" + System.lineSeparator(),
					outputStream.toString(StandardCharsets.UTF_8));

			outputStream.reset();
			System.setProperty(LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY, "INFO");
			QueueEventsRouter.of().log(factory.eventNoArg(Level.INFO, "reported info", KeyValues.of(), null));
			assertEquals("[INFO] - RAINBOW_GUM - test - reported info" + System.lineSeparator(),
					outputStream.toString(StandardCharsets.UTF_8));
		}
		finally {
			if (originalQueueLevel == null) {
				System.clearProperty("logging.global.queue.level");
			}
			else {
				System.setProperty(LogProperties.GLOBAL_QUEUE_LEVEL_PROPERTY, originalQueueLevel);
			}
			if (originalErrorLevel == null) {
				System.clearProperty("logging.global.queue.error");
			}
			else {
				System.setProperty(LogProperties.GLOBAL_QUEUE_ERROR_PROPERTY, originalErrorLevel);
			}
		}
	}

}
