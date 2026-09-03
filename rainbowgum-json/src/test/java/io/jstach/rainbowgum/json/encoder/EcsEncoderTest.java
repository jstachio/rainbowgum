package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.PropertiesParser;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class EcsEncoderTest {

	/*
	 * Confirms maxBufferSize threads all the way from EcsEncoderBuilder through to the
	 * JsonBuffer's own isOversized() answer.
	 */
	@Test
	void testMaxBufferSizeThreadsThroughToBuffer() {
		EcsEncoderBuilder b = new EcsEncoderBuilder("ecs");
		b.maxBufferSize(20_000);
		EcsEncoder encoder = b.build();

		var buffer = encoder.buffer(WriteMethod.STRING);
		LogEvent e = LogEventFactory.of("ecs").event(Level.INFO, "x".repeat(30_000), KeyValues.of(), (Throwable) null);
		encoder.encode(e, buffer);

		assertTrue(buffer.isOversized());
	}

	@Test
	void testBuilder() {
		EcsEncoderBuilder b = new EcsEncoderBuilder("ecs");
		b.serviceName("myapp");
		b.prettyPrint(true);
		Map<String, String> props = new LinkedHashMap<>();
		b.toProperties(props::put);
		String expected = """
				logging.encoder.ecs.serviceName=myapp
				logging.encoder.ecs.prettyPrint=true
				""";
		String actual = PropertiesParser.writeProperties(props);
		assertEquals(expected, actual);

		b = new EcsEncoderBuilder("ecs");
		props = PropertiesParser.readProperties(actual);
		b.fromProperties(props::get);

		EcsEncoder encoder = b.build();

		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "ecs", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String message = out.events().get(0).getValue();
		expected = """
				{
				 "@timestamp":"1970-01-01T00:00:00.001Z",
				 "log.level":"INFO",
				 "message":"hello",
				 "ecs.version":"1.2.0",
				 "service.name":"myapp",
				 "log.logger":"ecs",
				 "process.thread.name":"main"
				}
				""";

		assertEquals(expected, message);
	}

	@Test
	void testFullLoadUri() throws Exception {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=ecs:///?serviceName=myapp
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new EcsEncoderConfigurator())
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			r.log(LogEvent.of(instant, Thread.currentThread().getName(), 1, System.Logger.Level.INFO, "ecs", "hello",
					KeyValues.of(), null));
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			String actual = output.events().get(0).getValue();
			String expected = "{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"log.level\":\"INFO\","
					+ "\"message\":\"hello\",\"ecs.version\":\"1.2.0\",\"service.name\":\"myapp\","
					+ "\"log.logger\":\"ecs\",\"process.thread.name\":\"main\"}\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testStructuredSimple() {
		EcsEncoder encoder = new EcsEncoderBuilder("ecs").structured(true).build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "ecs", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		String expected = "{\"@timestamp\":\"1970-01-01T00:00:00.001Z\",\"log\":{\"level\":\"INFO\","
				+ "\"logger\":\"ecs\"},\"message\":\"hello\",\"ecs\":{\"version\":\"1.2.0\"},"
				+ "\"process\":{\"thread\":{\"name\":\"main\"}}}\n";
		assertEquals(expected, actual);
	}

	@Test
	void testStructuredServiceAndNode() {
		EcsEncoder encoder = new EcsEncoderBuilder("ecs").structured(true)
			.serviceName("myapp")
			.serviceVersion("1.0")
			.serviceEnvironment("prod")
			.serviceNodeName("node-1")
			.build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "ecs", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String actual = out.events().get(0).getValue();
		assertTrue(actual.contains(
				"\"service\":{\"name\":\"myapp\",\"version\":\"1.0\",\"environment\":\"prod\",\"node\":{\"name\":\"node-1\"}}"),
				"Got: " + actual);
	}

	@Test
	void testStructuredErrorAndMdc() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(EcsEncoder.of(ecs -> ecs.structured(true)));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			var kvs = MutableKeyValues.of().add("requestId", "abc123");
			Throwable t = new RuntimeException("boom");
			LogEvent e = LogEventFactory.of("ecs").event(System.Logger.Level.INFO, "hello", kvs, t).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"error\":{\"type\":\"java.lang.RuntimeException\",\"message\":\"boom\","),
					"Got: " + actual);
			assertTrue(actual.contains("\"requestId\":\"abc123\""), "Got: " + actual);
			// custom key values stay flattened top-level even in structured mode.
			assertTrue(!actual.contains("\"requestId\":{"), "Got: " + actual);
		}
	}

	@Test
	void testMdcIsFlattenedTopLevel() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(EcsEncoder.of(ecs -> {
			}));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			var kvs = MutableKeyValues.of().add("requestId", "abc123");
			LogEvent e = LogEventFactory.of("ecs")
				.event(System.Logger.Level.INFO, "hello", kvs, (Throwable) null)
				.freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"requestId\":\"abc123\""), "Got: " + actual);
		}
	}

	@ParameterizedTest
	@EnumSource(EcsTest.class)
	void test(EcsTest test) throws Exception {
		var config = LogConfig.builder().level(Level.TRACE).build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(EcsEncoder.of(ecs -> ecs.prettyPrint(true)));
			a.output(output);
		})).build().start()) {
			for (var e : test.events()) {
				g.log(e);
			}
			String actual = output.toString();
			test.assertOutput(actual);
		}
	}

	enum EcsTest {

		hello("""
				{
				 "@timestamp":"1970-01-01T00:00:00.001Z",
				 "log.level":"INFO",
				 "message":"hello",
				 "ecs.version":"1.2.0",
				 "log.logger":"ecs",
				 "process.thread.name":"main"
				}
				"""), error("""
				 "error.type":"java.lang.RuntimeException",
				 "error.message":"boom",
				""") {

			@Override
			void assertOutput(String actual) {
				assertTrue(actual.contains(expected()), "Got: " + actual);
			}

			@Override
			@org.eclipse.jdt.annotation.Nullable
			Throwable throwable() {
				return new RuntimeException("boom");
			}

		};

		private static final Instant instant = Instant.ofEpochMilli(1);

		private final String expected;

		private EcsTest(String expected) {
			this.expected = expected;
		}

		String expected() {
			return expected;
		}

		void assertOutput(String actual) {
			assertEquals(expected, actual);
		}

		@org.eclipse.jdt.annotation.Nullable
		Throwable throwable() {
			return null;
		}

		List<LogEvent> events() {
			LogEvent e = LogEvent
				.ofAll(instant, "main", 1L, Level.INFO, "ecs", "hello", KeyValues.of(), throwable(),
						StandardMessageFormatter.SLF4J, List.of())
				.freeze(instant);
			return List.of(e);
		}

	}

}
