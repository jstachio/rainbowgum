package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.PropertiesParser;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class GelfEncoderTest {

	@Test
	void testBuilder() {
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		b.headers(Map.of("header1", "1"));
		b.host("localhost");
		b.prettyPrint(true);
		Map<String, String> props = new LinkedHashMap<>();
		b.toProperties(props::put);
		String expected = """
				logging.encoder.gelf.host=localhost
				logging.encoder.gelf.headers=header1\\=1
				logging.encoder.gelf.prettyPrint=true
				""";
		String actual = PropertiesParser.writeProperties(props);
		assertEquals(expected, actual);

		b = new GelfEncoderBuilder("gelf");
		String propString = actual;
		props = PropertiesParser.readProperties(propString);
		b.fromProperties(props::get);

		GelfEncoder encoder = b.build();

		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "gelf", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String message = out.events().get(0).getValue();
		expected = """
				{
				 "host":"localhost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":6,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"INFO",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "_header1":"1",
				 "version":"1.1"
				}
				""";

		assertEquals(expected, message);
	}

	@Test
	void testFullLoad() throws Exception {
		String properties = """
				logging.appender.console.encoder=gelf:///
				logging.encoder.console.host=localhost
				logging.encoder.console.headers=header1\\=1
				logging.encoder.console.prettyPrint=true
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new GelfEncoderConfigurator())
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent
				.ofAll(instant, "main", 1L, Level.INFO, "gelf", "hello", KeyValues.of(), null,
						StandardMessageFormatter.SLF4J, List.of())
				.freeze(instant);
			r.log(e);
		}
	}

	@Test
	void testFullLoadUri() throws Exception {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=gelf://somehost/?prettyPrint=false
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new GelfEncoderConfigurator())
			.build();
		try (var r = RainbowGum.builder(config).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			// LogEvent e = LogEvent.of(System.Logger.Level.INFO, "gelf", "hello",
			// null).freeze(instant);
			// LogEvent e = LogEvent.ofAll(instant, "main", 1L, Level.INFO, "gelf",
			// "hello", KeyValues.of(), throwable(), StandardMessageFormatter.SLF4J,
			// List.of()).freeze(instant);
			r.router()
				.eventBuilder("gelf", System.Logger.Level.INFO)
				.message("hello")
				.threadId(1)
				.timestamp(instant)
				.log();
			// r.log(e);
			ListLogOutput output = (ListLogOutput) config.outputRegistry().output("list").orElseThrow();
			String actual = output.events().get(0).getValue();
			String expected = "{\"host\":\"somehost\",\"short_message\":\"hello\","
					+ "\"timestamp\":0.001,\"level\":6,\"_time\":\"1970-01-01T00:00:00.001Z\","
					+ "\"_level\":\"INFO\",\"_logger\":\"gelf\",\"_thread_name\":\"main\",\"_thread_id\":\"1\",\"version\":\"1.1\"}\n";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testFullLoadBuilder() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(GelfEncoder.of(gelf -> {
				gelf.prettyPrint(true);
				gelf.host("somehost");
			}));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			g.router()
				.eventBuilder("gelf", System.Logger.Level.INFO)
				.message("hello")
				.threadId(1)
				.timestamp(instant)
				.log();
			String actual = output.events().get(0).getValue();
			String expected = """
					{
					 "host":"somehost",
					 "short_message":"hello",
					 "timestamp":0.001,
					 "level":6,
					 "_time":"1970-01-01T00:00:00.001Z",
					 "_level":"INFO",
					 "_logger":"gelf",
					 "_thread_name":"main",
					 "_thread_id":"1",
					 "version":"1.1"
					}
					""";
			assertEquals(expected, actual);
		}
	}

	@Test
	void testMdcKeyIsEscaped() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(GelfEncoder.of(gelf -> gelf.host("somehost")));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			var kvs = MutableKeyValues.of().add("k\"1", "v1");
			LogEvent e = LogEvent.of(System.Logger.Level.INFO, "gelf", "hello", kvs, null).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			assertTrue(actual.contains("\"_k\\\"1\":\"v1\""),
					"MDC key containing a quote must be escaped, not break out of the field name. Got: " + actual);
		}
	}

	@Test
	void testUnpairedSurrogateDoesNotThrow() throws Exception {
		var config = LogConfig.builder().build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(GelfEncoder.of(gelf -> gelf.host("somehost")));
			a.output(output);
		})).build().start()) {
			Instant instant = Instant.ofEpochMilli(1);
			// lone high surrogate with no matching low surrogate.
			String malformed = "bad\uD800end";
			LogEvent e = LogEvent.of(System.Logger.Level.INFO, "gelf", malformed, null).freeze(instant);
			g.log(e);
			String actual = output.events().get(0).getValue();
			// U+FFFD replacement character encoded as UTF-8.
			assertTrue(actual.contains("bad�end"), "Got: " + actual);
		}
	}

	@ParameterizedTest
	@EnumSource(GelfTest.class)
	void test(GelfTest test) throws Exception {
		var config = LogConfig.builder().level(Level.TRACE).build();
		ListLogOutput output = new ListLogOutput();
		try (var g = RainbowGum.builder(config).route(rb -> rb.appender("list", a -> {
			a.encoder(GelfEncoder.of(gelf -> {
				gelf.prettyPrint(true);
				gelf.host("somehost");
			}));
			a.output(output);
		})).build().start()) {
			var events = test.events();
			for (var e : events) {
				g.log(e);
			}
			String actual = output.toString();
			test.assertOutput(actual);
		}
	}

	enum GelfTest {

		hello("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":6,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"INFO",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				"""), TRACE("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":7,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"TRACE",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				""", Level.TRACE), DEBUG("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":7,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"DEBUG",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				""", Level.DEBUG), INFO("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":6,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"INFO",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				""", Level.INFO), WARN("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":4,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"WARN",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				""", Level.WARNING), ERROR("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":3,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"ERROR",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
				""", Level.ERROR), quote("""
				{
				 "host":"somehost",
				 "short_message":"Let us put some double quotes \\"\\nand = and ' andd \\\\ < slash\\n\\f \\r \\b\\n",
				 "timestamp":0.001,
				 "level":6,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"INFO",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "version":"1.1"
				}
								""") {
			@Override
			String message() {
				return """
						Let us put some double quotes "
						and = and ' andd \\ < slash
						\f \r \b
						""";
			}
		},
		mdc("""
				{
				 "host":"somehost",
				 "short_message":"hello",
				 "timestamp":0.001,
				 "level":6,
				 "_time":"1970-01-01T00:00:00.001Z",
				 "_level":"INFO",
				 "_logger":"gelf",
				 "_thread_name":"main",
				 "_thread_id":"1",
				 "_k1":"v1",
				 "_k2":"v2",
				 "version":"1.1"
				}
				""")

		{
			@Override
			List<LogEvent> events() {
				var kvs = MutableKeyValues.of().add("k1", "v1").add("k2", "v2");
				LogEvent e = LogEvent
					.ofAll(instant, "main", 1L, level(), "gelf", message(), kvs, throwable(),
							StandardMessageFormatter.SLF4J, List.of())
					.freeze(instant);
				return List.of(e);

			}
		},
		throwable("""
				"_throwable":"java.lang.RuntimeException",
				""") {

			@Override
			@Nullable
			Throwable throwable() {
				return new RuntimeException("expected");
			}

			@Override
			void assertOutput(String actual) {
				System.out.println(actual);
				assertTrue(actual.contains(expected()));
			}
		};

		private final String expected;

		private final System.Logger.Level level;

		private static final Instant instant = Instant.ofEpochMilli(1);

		private GelfTest(String expected) {
			this(expected, System.Logger.Level.INFO);
		}

		void assertOutput(String actual) {
			assertEquals(expected, actual);

		}

		private GelfTest(String expected, System.Logger.Level level) {
			this.expected = expected;
			this.level = level;
		}

		System.Logger.Level level() {
			return level;
		}

		String expected() {
			return this.expected;
		}

		List<LogEvent> events() {
			LogEvent e = LogEvent
				.ofAll(instant, "main", 1L, level(), "gelf", message(), KeyValues.of(), throwable(),
						StandardMessageFormatter.SLF4J, List.of())
				.freeze(instant);
			return List.of(e);
		}

		@Nullable
		Throwable throwable() {
			return null;
		}

		String message() {
			return "hello";
		}

	}

}
