package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogResponse.Status;

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

	@Test
	@SuppressWarnings("StringSplitter")
	void testErrorWithConfigWritesToStderrAndRecordsStatusHistory() {
		var config = LogConfig.builder().build();
		MetaLog.error(config, MetaLogTest.class, new RuntimeException("expected"));

		String actual = outputStream.toString(StandardCharsets.UTF_8).split("\n")[0];
		assertEquals("[ERROR] - RAINBOW_GUM expected java.lang.RuntimeException: expected", actual);

		var recent = config.statusManager().recent();
		assertEquals(1, recent.size());
		var event = recent.get(0);
		assertEquals(MetaLogTest.class, event.type());
		assertEquals(MetaLogTest.class.getName(), event.name());
		assertEquals(new Status.ErrorStatus("expected"), event.status());
	}

	@Test
	@SuppressWarnings("StringSplitter")
	void testErrorWithConfigAndMessageUsesGivenMessageForStatusHistory() {
		var config = LogConfig.builder().build();
		MetaLog.error(config, MetaLogTest.class, "custom message", new RuntimeException("expected"));

		String actual = outputStream.toString(StandardCharsets.UTF_8).split("\n")[0];
		assertEquals("[ERROR] - RAINBOW_GUM custom message java.lang.RuntimeException: expected", actual);

		var recent = config.statusManager().recent();
		assertEquals(1, recent.size());
		assertEquals(new Status.ErrorStatus("expected"), recent.get(0).status());
	}

	@Test
	void testErrorWithRainbowGumRoutesToTheGumsOwnConfig() {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).build();
		try {
			MetaLog.error(gum, MetaLogTest.class, new RuntimeException("expected"));
			assertSame(config, gum.config());
			assertEquals(1, config.statusManager().recent().size());
		}
		finally {
			gum.close();
		}
	}

}
