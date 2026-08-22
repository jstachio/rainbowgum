package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LogbackJsonEncoderTest {

	/*
	 * Every other test in this file builds LogbackJsonEncoder directly (either via
	 * LogbackJsonEncoderBuilder or LogbackJsonEncoder.of(...)), bypassing
	 * LogbackJsonEncoderConfigurator (the ServiceLoader-registered URI-scheme resolution
	 * path) entirely - it had 0% coverage. This goes through the real pipeline:
	 * logging.appender.list.encoder=logback resolves via the configurator's registered
	 * scheme, same as GelfEncoderTest/EcsEncoderTest's testFullLoadUri.
	 */
	@Test
	void testFullLoadUri() throws Exception {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=logback:///?prettyPrint=true
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new LogbackJsonEncoderConfigurator())
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			r.router()
				.eventBuilder("logback", System.Logger.Level.INFO)
				.message("hello")
				.threadId(1)
				.timestamp(instant)
				.log();
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			String actual = output.events().get(0).getValue();
			assertTrue(actual.startsWith("{\n "),
					"expected pretty-printed output from ?prettyPrint=true, got: " + actual);
			assertTrue(actual.contains("\"message\":\"hello\""), "Got: " + actual);
		}
	}

	@Test
	void testSimpleMessage() {
		var encoder = new LogbackJsonEncoderBuilder("logback").build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "logback", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		String expected = "{\"timestamp\":1,\"nanoseconds\":1000000,\"level\":\"INFO\",\"threadName\":\"main\","
				+ "\"loggerName\":\"logback\",\"mdc\":{},\"message\":\"hello\"}\n";
		assertEquals(expected, actual);
	}

	@Test
	void testMdcNestedObject() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(LogbackJsonEncoder.of(logback -> {
			}));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			var kvs = MutableKeyValues.of().add("k1", "v1").add("k2", "v2");
			LogEvent e = LogEvent.of(System.Logger.Level.INFO, "logback", "hello", kvs, null).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"mdc\":{\"k1\":\"v1\",\"k2\":\"v2\"}"), "Got: " + actual);
		}
	}

	@Test
	void testThrowableStepArrayAndCause() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(LogbackJsonEncoder.of(logback -> {
			}));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			Throwable cause = new IllegalStateException("root cause");
			Throwable t = new RuntimeException("boom", cause);
			LogEvent e = LogEvent.of(System.Logger.Level.INFO, "logback", "hello", KeyValues.of(), t).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"throwable\":{\"className\":\"java.lang.RuntimeException\","
					+ "\"message\":\"boom\",\"stepArray\":[{"), "Got: " + actual);
			assertTrue(actual.contains("\"cause\":{\"className\":\"java.lang.IllegalStateException\","
					+ "\"message\":\"root cause\",\"stepArray\":[{"), "Got: " + actual);
		}
	}

	@Test
	void testPrettyPrint() {
		var encoder = new LogbackJsonEncoderBuilder("logback").prettyPrint(true).build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "logback", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		assertTrue(actual.startsWith("{\n "), "Got: " + actual);
		assertTrue(actual.contains("\"mdc\":{}"), "Got: " + actual);
	}

}
