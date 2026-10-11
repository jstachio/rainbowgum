package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

class CollidingKeyValuesTest {

	enum EncoderCase {

		LOGSTASH(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","@version":"1","message":"real","logger_name":"app","thread_name":"main","level":"INFO","level_value":20000,"log.level":"LL","log":"LG","id":"I","ok":"1","keyValues":{"message":"M","level":"L","@timestamp":"T","keyValues":"K"}}
						""") {
			@Override
			LogEncoder encoder() {
				return new LogstashEncoderBuilder("logstash").zoneId(ZoneOffset.UTC).build();
			}
		},
		GELF("""
				{"host":"h","short_message":"real","timestamp":0.001,"level":6,"_time":"1970-01-01T00:00:00.001Z","_level":"INFO","_logger":"app","_thread_name":"main","_thread_id":"1","_message":"M","_@timestamp":"T","_log.level":"LL","_log":"LG","_ok":"1","_keyValues.level":"L","_keyValues.id":"I","_keyValues.keyValues":"K","version":"1.1"}
				""") {
			@Override
			LogEncoder encoder() {
				return new GelfEncoderBuilder("gelf").host("h").build();
			}
		},
		ECS_FLATTENED(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","log.level":"INFO","message":"real","ecs.version":"1.2.0","log.logger":"app","process.thread.name":"main","level":"L","log":"LG","id":"I","ok":"1","keyValues.message":"M","keyValues.@timestamp":"T","keyValues.log.level":"LL","keyValues.keyValues":"K"}
						""") {
			@Override
			LogEncoder encoder() {
				return new EcsEncoderBuilder("ecs").build();
			}
		},
		ECS_STRUCTURED(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","log":{"level":"INFO","logger":"app"},"message":"real","ecs":{"version":"1.2.0"},"process":{"thread":{"name":"main"}},"level":"L","log.level":"LL","id":"I","ok":"1","keyValues":{"message":"M","@timestamp":"T","log":"LG","keyValues":"K"}}
						""") {
			@Override
			LogEncoder encoder() {
				return new EcsEncoderBuilder("ecs").structured(true).build();
			}
		};

		final String expected;

		EncoderCase(String expected) {
			this.expected = expected;
		}

		abstract LogEncoder encoder();

	}

	@ParameterizedTest
	@EnumSource(EncoderCase.class)
	void keyValuesNamedLikeEncoderFieldsAreNotWrittenTwice(EncoderCase test) {
		var map = new LinkedHashMap<String, String>();
		map.put("message", "M");
		map.put("level", "L");
		map.put("@timestamp", "T");
		map.put("log.level", "LL");
		map.put("log", "LG");
		map.put("id", "I");
		map.put("keyValues", "K");
		map.put("ok", "1");
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent event = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "app", "real", KeyValues.of(map), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);
		var encoder = test.encoder();
		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(event, buffer);
		var out = new ListLogOutput();
		buffer.drain(out, event);
		assertEquals(test.expected, out.events().get(0).getValue());
	}

}
