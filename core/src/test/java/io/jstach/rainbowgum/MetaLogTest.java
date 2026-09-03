package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
		assertEquals("[ERROR] - RAINBOW_GUM expected java.lang.RuntimeException: expected", actual);
	}

}
