package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
