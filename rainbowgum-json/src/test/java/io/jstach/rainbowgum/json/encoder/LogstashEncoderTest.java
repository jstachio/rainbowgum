package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LogstashEncoderTest {

	@Test
	void testSimpleMessage() {
		var encoder = new LogstashEncoderBuilder("logstash").zoneId(java.time.ZoneOffset.UTC).build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "logstash", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		String expected = "{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"@version\":\"1\",\"message\":\"hello\","
				+ "\"logger_name\":\"logstash\",\"thread_name\":\"main\",\"level\":\"INFO\",\"level_value\":20000}\n";
		assertEquals(expected, actual);
	}

	@Test
	void testZoneIdOffset() {
		var encoder = new LogstashEncoderBuilder("logstash").zoneId(ZoneOffset.ofHours(-5)).build();
		assertEquals(ZoneOffset.ofHours(-5), encoder.zoneId());
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "logstash", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		assertTrue(actual.contains("\"@timestamp\":\"1969-12-31T19:00:00.001-05:00\""), "Got: " + actual);
	}

	@Test
	void testMdcIsFlattenedTopLevel() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(LogstashEncoder.of(logstash -> logstash.zoneId(java.time.ZoneOffset.UTC)));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			var kvs = MutableKeyValues.of().add("requestId", "abc123");
			LogEvent e = LogEvent.of(System.Logger.Level.INFO, "logstash", "hello", kvs, null).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"requestId\":\"abc123\""), "Got: " + actual);
		}
	}

	@Test
	void testStackTrace() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(LogstashEncoder.of(logstash -> logstash.zoneId(java.time.ZoneOffset.UTC)));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			Throwable t = new RuntimeException("boom");
			LogEvent e = LogEvent.of(System.Logger.Level.ERROR, "logstash", "hello", KeyValues.of(), t).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"level_value\":40000"), "Got: " + actual);
			assertTrue(actual.contains("\"stack_trace\":\"java.lang.RuntimeException: boom"), "Got: " + actual);
		}
	}

}
