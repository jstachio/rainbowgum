package io.jstach.rainbowgum.json.encoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Key values with a JSON form (KeyValues#getJsonOrNull) are written as JSON, except by
 * GELF, whose additional fields are only strings or numbers, and the Logback compatible
 * encoder, whose mdc is strings.
 */
class JsonFormKeyValuesTest {

	enum EncoderCase {

		LOGSTASH(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","@version":"1","message":"m","logger_name":"app","thread_name":"main","level":"INFO","level_value":20000,"requestId":"42","attempt":3,"user":{"id":1,"name":"Ada é"},"keyValues":{"message":{"a":1}}}
						""") {
			@Override
			LogEncoder encoder() {
				return new LogstashEncoderBuilder("logstash").zoneId(ZoneOffset.UTC).build();
			}
		},
		GELF("""
				{"host":"h","short_message":"m","timestamp":0.001,"level":6,"_time":"1970-01-01T00:00:00.001Z","_level":"INFO","_logger":"app","_thread_name":"main","_thread_id":"1","_requestId":"42","_attempt":3,"_user":"{id=1, name=Ada é}","_message":"{a=1}","version":"1.1"}
				""") {
			@Override
			LogEncoder encoder() {
				return new GelfEncoderBuilder("gelf").host("h").build();
			}
		},
		ECS_FLATTENED(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","log.level":"INFO","message":"m","ecs.version":"1.2.0","log.logger":"app","process.thread.name":"main","requestId":"42","attempt":3,"user":{"id":1,"name":"Ada é"},"keyValues.message":{"a":1}}
						""") {
			@Override
			LogEncoder encoder() {
				return new EcsEncoderBuilder("ecs").build();
			}
		},
		ECS_STRUCTURED(
				"""
						{"@timestamp":"1970-01-01T00:00:00.001Z","log":{"level":"INFO","logger":"app"},"message":"m","ecs":{"version":"1.2.0"},"process":{"thread":{"name":"main"}},"requestId":"42","attempt":3,"user":{"id":1,"name":"Ada é"},"keyValues":{"message":{"a":1}}}
						""") {
			@Override
			LogEncoder encoder() {
				return new EcsEncoderBuilder("ecs").structured(true).build();
			}
		},
		LOGBACK_JSON(
				"""
						{"timestamp":1,"nanoseconds":1000000,"level":"INFO","threadName":"main","loggerName":"app","mdc":{"requestId":"42","attempt":"3","user":"{id=1, name=Ada é}","message":"{a=1}"},"message":"m"}
						""") {
			@Override
			LogEncoder encoder() {
				return new LogbackJsonEncoderBuilder("logback").build();
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
	void jsonFormsAreWrittenAsJson(EncoderCase test) {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("attempt", "3", "3");
		kvs.putKeyValue("user", "{id=1, name=Ada é}", "{\"id\":1,\"name\":\"Ada é\"}");
		kvs.putKeyValue("message", "{a=1}", "{\"a\":1}");
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent event = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "app", "m", kvs, null, StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);
		var encoder = test.encoder();
		var buffer = encoder.buffer(WriteMethod.STRING);
		encoder.encode(event, buffer);
		var out = new ListLogOutput();
		buffer.drain(out, event);
		assertEquals(test.expected, out.events().get(0).getValue());
	}

}
