package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.PropertiesParser;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class GelfEncoderTest {

	/*
	 * Regression test for a real bug found while investigating LogProperty coverage:
	 * MapGetter._propertyString/ListGetter._propertyString had "if (first) { first =
	 * true; }" instead of "first = false", so entries after the first were never
	 * separated. Every existing headers test used a single-entry map, which can't reveal
	 * this - "&" is only ever appended starting from the second entry.
	 */
	@Test
	void testHeadersWithMultipleEntriesAreSeparated() {
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		var headers = new java.util.LinkedHashMap<String, String>();
		headers.put("header1", "1");
		headers.put("header2", "2");
		headers.put("header3", "3");
		b.headers(headers);
		b.host("localhost");
		Map<String, String> props = new LinkedHashMap<>();
		b.toProperties(props::put);
		assertEquals("header1=1&header2=2&header3=3", props.get("logging.encoder.gelf.headers"));
	}

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
	void testTimeSubMillisecondPrecisionIsTruncated() {
		/*
		 * Instant with a real sub-millisecond remainder (unlike the round-millisecond
		 * Instant.ofEpochMilli(1) used elsewhere in this file) - confirms "_time" is
		 * fixed at millisecond precision (DEFAULT_TIME_FRACTIONAL_DIGITS) rather than
		 * DateTimeFormatter.ISO_INSTANT's old variable-width behavior, which would have
		 * rendered ".001456789" here instead of ".001".
		 */
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		b.host("localhost");
		GelfEncoder encoder = b.build();

		Instant instant = Instant.ofEpochMilli(1).plusNanos(456_789);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "gelf", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String message = out.events().get(0).getValue();
		assertTrue(message.contains("\"_time\":\"1970-01-01T00:00:00.001Z\""), message);
	}

	/*
	 * Confirms maxBufferSize threads all the way from GelfEncoderBuilder through to the
	 * JsonBuffer's own isOversized() answer, the same way prettyPrint/host thread through
	 * to actual encoding behavior elsewhere in this file.
	 */
	@Test
	void testMaxBufferSizeThreadsThroughToBuffer() {
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		b.host("localhost");
		b.maxBufferSize(20_000);
		GelfEncoder encoder = b.build();

		var buffer = encoder.buffer(WriteMethod.STRING);
		LogEvent e = LogEventFactory.of("gelf").event(Level.INFO, "x".repeat(30_000), KeyValues.of(), (Throwable) null);
		encoder.encode(e, buffer);

		assertTrue(buffer.isOversized());
	}

	/*
	 * host is a required property with no default value, so GelfEncoderBuilder's field
	 * starts out null - if build() is called directly (bypassing fromProperties())
	 * without ever calling .host(...), Property.require() throws. Every other test in
	 * this file always sets host, so this branch of require() had no coverage anywhere
	 * before this.
	 */
	@Test
	void testBuildWithoutRequiredHostThrows() {
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		var e = assertThrows(LogProperty.PropertyMissingException.class, b::build);
		assertEquals("Value is required not null. property key='logging.encoder.gelf.host'", e.getMessage());
	}

	@Test
	void testTimeFractionalDigitsConfigurable() {
		GelfEncoderBuilder b = new GelfEncoderBuilder("gelf");
		b.host("localhost");
		b.timeFractionalDigits(6);
		GelfEncoder encoder = b.build();

		Instant instant = Instant.ofEpochMilli(1).plusNanos(456_789);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "gelf", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);

		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(e, buffer);
		ListLogOutput out = new ListLogOutput();
		buffer.drain(out, e);
		String message = out.events().get(0).getValue();
		assertTrue(message.contains("\"_time\":\"1970-01-01T00:00:00.001456Z\""), message);
	}

	/*
	 * timeFractionalDigits is an @Nullable (optional) property, so a malformed value goes
	 * through Validator.addIfError() rather than add() - a branch that, until now, no
	 * test anywhere in the suite exercised with a bad value (only ever with a missing or
	 * a valid one).
	 */
	@Test
	void testMalformedTimeFractionalDigitsReportsErrorViaAddIfError() {
		String properties = """
				logging.appenders=list
				logging.appender.list.output=list:///
				logging.appender.list.encoder=gelf
				logging.encoder.list.host=localhost
				logging.encoder.list.timeFractionalDigits=notanumber
				""";
		LogConfig config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new GelfEncoderConfigurator())
			.build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		assertEquals(
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'list' from property: Property[logging.appenders]=[list]. cause:
						Error converting property. key: 'logging.appender.list.encoder' from PROPERTIES_STRING[logging.appender.list.encoder], value: 'gelf' cause:
						Validation failed for io.jstach.rainbowgum.json.encoder.GelfEncoderBuilder:
						Error for property. key: 'logging.encoder.list.timeFractionalDigits' from PROPERTIES_STRING[logging.encoder.list.timeFractionalDigits], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.encoder.list.timeFractionalDigits' from PROPERTIES_STRING[logging.encoder.list.timeFractionalDigits], [logging.appender.list.encoder]->URI(gelf:///)[timeFractionalDigits]
						Tried: 'logging.appender.list.encoder' from PROPERTIES_STRING[logging.appender.list.encoder]""",
				e.getMessage());
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
			r.log(LogEvent.of(instant, Thread.currentThread().getName(), 1, System.Logger.Level.INFO, "gelf", "hello",
					KeyValues.of(), null));
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
			g.log(LogEvent.of(instant, Thread.currentThread().getName(), 1, System.Logger.Level.INFO, "gelf", "hello",
					KeyValues.of(), null));
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
			LogEvent e = LogEventFactory.of("gelf")
				.event(System.Logger.Level.INFO, "hello", kvs, (Throwable) null)
				.freeze(instant);
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
			LogEvent e = LogEventFactory.of("gelf")
				.event(System.Logger.Level.INFO, malformed, KeyValues.of(), (Throwable) null)
				.freeze(instant);
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
