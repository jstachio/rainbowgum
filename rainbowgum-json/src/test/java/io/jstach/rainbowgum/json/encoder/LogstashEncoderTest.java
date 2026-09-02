package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class LogstashEncoderTest {

	/*
	 * Confirms maxBufferSize threads all the way from LogstashEncoderBuilder through to
	 * the JsonBuffer's own isOversized() answer.
	 */
	@Test
	void testMaxBufferSizeThreadsThroughToBuffer() {
		var encoder = new LogstashEncoderBuilder("logstash").maxBufferSize(20_000).build();

		var buffer = encoder.buffer(WriteMethod.STRING);
		LogEvent e = LogEvent.of(Level.INFO, "logstash", "x".repeat(30_000), KeyValues.of(), null);
		encoder.encode(e, buffer);

		assertTrue(buffer.isOversized());
	}

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

	/*
	 * zoneId is an @Nullable (optional) property, so a malformed value goes through
	 * Validator.addIfError() rather than add() - a branch that, until now, no test
	 * anywhere in the suite exercised with a bad value (only ever with a missing or a
	 * valid one).
	 */
	@Test
	void testMalformedZoneIdReportsErrorViaAddIfError() {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=logstash
				logging.encoder.list.zoneId=Not/AZone
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new LogstashEncoderConfigurator())
			.build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		assertEquals(
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'list' from property: Property[logging.appenders]=[list]. cause:
						Error converting property. key: 'logging.appender.list.encoder' from PROPERTIES_STRING[logging.appender.list.encoder], value: 'logstash' cause:
						Validation failed for io.jstach.rainbowgum.json.encoder.LogstashEncoderBuilder:
						Error for property. key: 'logging.encoder.list.zoneId' from PROPERTIES_STRING[logging.encoder.list.zoneId], java.time.zone.ZoneRulesException Unknown time-zone ID: Not/AZone
						Tried: 'logging.encoder.list.zoneId' from PROPERTIES_STRING[logging.encoder.list.zoneId], [logging.appender.list.encoder]->URI(logstash:///)[zoneId]
						Tried: 'logging.appender.list.encoder' from PROPERTIES_STRING[logging.appender.list.encoder]""",
				e.getMessage());
	}

	/*
	 * testMalformedZoneIdReportsErrorViaAddIfError above always fails before reaching
	 * LogstashEncoderProvider.provide(ref)'s build() call, so the *successful* path
	 * through the real ServiceLoader-registered URI-scheme resolution
	 * (LogstashEncoderConfigurator -> LogstashEncoderProvider) was still only 47%
	 * covered. This exercises it end to end, mirroring GelfEncoderTest/EcsEncoderTest's
	 * testFullLoadUri.
	 */
	@Test
	void testFullLoadUri() throws Exception {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=logstash:///?prettyPrint=true
				logging.encoder.list.zoneId=UTC
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new LogstashEncoderConfigurator())
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			r.router()
				.eventBuilder("logstash", System.Logger.Level.INFO)
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
