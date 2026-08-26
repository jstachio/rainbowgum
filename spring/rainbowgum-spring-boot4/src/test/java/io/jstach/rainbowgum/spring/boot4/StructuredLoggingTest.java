package io.jstach.rainbowgum.spring.boot4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.json.encoder.EcsEncoder;
import io.jstach.rainbowgum.json.encoder.GelfEncoder;
import io.jstach.rainbowgum.json.encoder.LogstashEncoder;
import io.jstach.rainbowgum.output.ListLogOutput;

class StructuredLoggingTest {

	private static StandardEnvironment environment(Map<String, Object> properties) {
		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
		return environment;
	}

	/*
	 * ListLogOutput's own OutputType is always MEMORY, so encoderForOutputType's
	 * auto-resolution-by-output-type wouldn't pick up what StructuredLogging.apply
	 * installed for FILE/CONSOLE_OUT - fetch that encoder explicitly and wire it onto the
	 * appender directly instead, matching how EcsEncoderTest/GelfEncoderTest exercise
	 * encoders elsewhere in the codebase.
	 */
	private static String encodedMessage(LogConfig config, OutputType outputType) {
		var output = new ListLogOutput();
		var encoder = config.encoderRegistry().encoderForOutputType(outputType);
		try (var g = RainbowGum.builder(config)
			.route(rb -> rb.appender("test", a -> a.output(output).encoder(encoder)))
			.build()
			.start()) {
			LogEvent e = LogEvent
				.of(System.Logger.Level.INFO, "io.jstach.logger", "hello", MutableKeyValues.of().freeze(), null)
				.freeze(Instant.EPOCH);
			g.log(e);
		}
		return output.events().get(0).getValue();
	}

	@Test
	void gelfOnConsoleOnlyLeavesFileAlone() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_CONSOLE, "gelf",
				"spring.application.name", "my-app"));
		StructuredLogging.apply(config, environment);

		var consoleEncoder = config.encoderRegistry().encoderForOutputType(OutputType.CONSOLE_OUT).provide("c", config);
		assertInstanceOf(GelfEncoder.class, consoleEncoder);

		var fileEncoder = config.encoderRegistry().encoderForOutputType(OutputType.FILE).provide("f", config);
		assertEquals(false, fileEncoder instanceof GelfEncoder, "file output type must be untouched");
	}

	@Test
	void gelfHostDefaultsToApplicationName() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_FILE, "gelf",
				"spring.application.name", "fallback-app"));
		StructuredLogging.apply(config, environment);
		String json = encodedMessage(config, OutputType.FILE);
		assertTrue(json.contains("\"host\":\"fallback-app\""), json);
	}

	@Test
	void gelfServiceVersionBecomesUnderscoreHeader() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_FILE, "gelf",
				SpringBootSupportedProperties.STRUCTURED_GELF_HOST, "h",
				SpringBootSupportedProperties.STRUCTURED_GELF_SERVICE_VERSION, "1.2.3"));
		StructuredLogging.apply(config, environment);
		String json = encodedMessage(config, OutputType.FILE);
		assertTrue(json.contains("\"_service_version\":\"1.2.3\""), json);
	}

	@Test
	void ecsFieldsAllThreadThrough() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_FILE, "ecs",
				SpringBootSupportedProperties.STRUCTURED_ECS_SERVICE_NAME, "svc",
				SpringBootSupportedProperties.STRUCTURED_ECS_SERVICE_VERSION, "2.0",
				SpringBootSupportedProperties.STRUCTURED_ECS_SERVICE_ENVIRONMENT, "prod",
				SpringBootSupportedProperties.STRUCTURED_ECS_SERVICE_NODE_NAME, "node-1"));
		StructuredLogging.apply(config, environment);
		String json = encodedMessage(config, OutputType.FILE);
		assertTrue(json.contains("\"service.name\":\"svc\""), json);
		assertTrue(json.contains("\"service.version\":\"2.0\""), json);
		assertTrue(json.contains("\"service.environment\":\"prod\""), json);
	}

	@Test
	void logstashFormatSelectsLogstashEncoder() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_CONSOLE, "logstash"));
		StructuredLogging.apply(config, environment);
		var encoder = config.encoderRegistry().encoderForOutputType(OutputType.CONSOLE_OUT).provide("c", config);
		assertInstanceOf(LogstashEncoder.class, encoder);
	}

	@Test
	void unrecognizedFormatIsIgnored() {
		var config = LogConfig.builder().build();
		var environment = environment(
				Map.of(SpringBootSupportedProperties.STRUCTURED_FORMAT_CONSOLE, "some.custom.FormatterClass"));
		StructuredLogging.apply(config, environment);
		var encoder = config.encoderRegistry().encoderForOutputType(OutputType.CONSOLE_OUT).provide("c", config);
		assertEquals(false,
				encoder instanceof GelfEncoder || encoder instanceof EcsEncoder || encoder instanceof LogstashEncoder);
	}

	@Test
	void noFormatPropertiesLeavesBothOutputTypesUntouched() {
		var config = LogConfig.builder().build();
		var environment = environment(Map.of());
		StructuredLogging.apply(config, environment);
		var console = config.encoderRegistry().encoderForOutputType(OutputType.CONSOLE_OUT).provide("c", config);
		var file = config.encoderRegistry().encoderForOutputType(OutputType.FILE).provide("f", config);
		assertEquals(false,
				console instanceof GelfEncoder || console instanceof EcsEncoder || console instanceof LogstashEncoder);
		assertEquals(false,
				file instanceof GelfEncoder || file instanceof EcsEncoder || file instanceof LogstashEncoder);
	}

}
