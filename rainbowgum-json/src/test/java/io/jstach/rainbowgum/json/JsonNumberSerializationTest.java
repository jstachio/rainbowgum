package io.jstach.rainbowgum.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.json.encoder.GelfEncoder;
import io.jstach.rainbowgum.output.ListLogOutput;

class JsonNumberSerializationTest {

	private static final Pattern NUMBER = Pattern
		.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");

	@Test
	void specialValuesAreValidJsonStrings() {
		assertEquals("\"Infinity\"", write(Double.POSITIVE_INFINITY));
		assertEquals("\"-Infinity\"", write(Double.NEGATIVE_INFINITY));
		assertEquals("\"NaN\"", write(Double.NaN));
	}

	@Test
	void signedZerosAreNumbers() {
		assertEquals("0.0", write(0.0));
		assertEquals("-0.0", write(-0.0));
	}

	@Test
	void finiteNumbersRoundTripWithoutChangingTheirJsonType() {
		var random = new Random(47);
		for (int i = 0; i < 10_000; i++) {
			double value = Double.longBitsToDouble(random.nextLong());
			if (!Double.isFinite(value)) {
				continue;
			}
			String encoded = write(value);
			assertTrue(NUMBER.matcher(encoded).matches(), () -> Double.toHexString(value) + " became " + encoded);
			assertEquals(Double.doubleToLongBits(value), Double.doubleToLongBits(Double.parseDouble(encoded)));
		}
	}

	@Test
	void gelfEpochTimestampIsANumberThroughTheLoggingPipeline() {
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("gelf", a -> a.output(output).encoder(GelfEncoder.of(b -> b.host("localhost")))))
			.build()
			.start()) {
			gum.log(LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "gelf", "hello", KeyValues.of(), null));
		}
		assertEquals(1, output.events().size());
		assertEquals("""
				{"host":"localhost","short_message":"hello","timestamp":0.0,"level":6,"_time":"1970-01-01T00:00:00.000Z","_level":"INFO","_logger":"gelf","_thread_name":"main","_thread_id":"1","version":"1.1"}
				""", output.events().getFirst().getValue());
	}

	private static String write(double value) {
		var writer = new RawJsonWriter(1);
		writer.writeDouble(value);
		return new String(writer.toByteArray(), StandardCharsets.UTF_8);
	}

}
