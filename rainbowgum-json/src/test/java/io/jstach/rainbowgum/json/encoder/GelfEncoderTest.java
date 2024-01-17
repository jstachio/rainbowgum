package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.PropertiesParser;
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
		LogEvent e = LogEvent.of(System.Logger.Level.INFO, "gelf", "hello", null).freeze(instant);
		var buffer = encoder.buffer();
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
				 _"header1":"1",
				 "version":"1.1"
				}
				""";

		assertEquals(expected, message);
	}

}
