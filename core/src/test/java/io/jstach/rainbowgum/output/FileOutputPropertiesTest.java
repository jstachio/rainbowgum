package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.EnumCombinations;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.FileOutputTest.Events;

class FileOutputPropertiesTest {

	@ParameterizedTest
	@MethodSource("provideArgs")
	void test(FileProperties properties, Events events) throws IOException {
		String fileName = FILE_PATH;
		try {
			var config = properties.config();
			var gum = RainbowGum.builder(config).build();
			try (var rg = gum.start()) {
				for (var e : events.events()) {
					rg.log(e);
				}
			}
			if (properties.exceptionMessage() != null) {
				fail("Expected an exception");
			}
			String actual = Files.readString(Path.of(fileName));
			String expected = events.expected;
			assertEquals(expected, actual);

		}
		catch (LogProperty.PropertyConvertException | LogProperty.PropertyMissingException e) {
			e.printStackTrace();
			String expected = properties.exceptionMessage();
			String actual = e.getMessage();
			if (expected == null) {
				throw e;
			}
			assertEquals(expected, actual);

		}
		finally {
			Files.deleteIfExists(Path.of(fileName));
		}
	}

	static final String FILE_PATH = "./target/FileOutputPropertiesTest/file.log";

	enum FileProperties {

		SPRING_FILE_NAME("""
				logging.file.name=%s
				"""), //
		JUST_FILE("""
				logging.appenders=file
				logging.file.name=%s
				"""), //
		URI_WITH_BUFFER_SIZE("""
				logging.appenders=file
				logging.file.name=file:///%s?bufferSize=800
				"""), //
		URI_WITH_BAD_BUFFER_SIZE("""
				logging.appenders=file
				logging.file.name=file:///%s?bufferSize=blah
				""") {
			@Override
			@Nullable
			String exceptionMessage() {
				String uri = Paths.get(FILE_PATH).toUri().toString();
				String message = """
						Error converting property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], value: 'file:///./target/FileOutputPropertiesTest/file.log?bufferSize=blah' cause:
						Validation failed for io.jstach.rainbowgum.output.FileOutputBuilder:
						Error for property. key: 'logging.output.file.bufferSize' from [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize], java.lang.NumberFormatException For input string: "blah"
						Tried: 'logging.output.file.bufferSize' from PROPERTIES_STRING[logging.output.file.bufferSize], ENVIRONMENT_VARIABLES[logging_output_file_bufferSize], [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize]
						Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], ENVIRONMENT_VARIABLES[logging_file_name]""" //
					.formatted(uri, uri);
				return message;
			}
		},
		BAD_URI("""
				logging.file.name=:://
				""") {
			@Override
			@Nullable
			String exceptionMessage() {
				String message = """
						Error for property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], java.net.URISyntaxException Expected scheme name at index 0: :://
						Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], ENVIRONMENT_VARIABLES[logging_file_name]""";
				return message;
			}
		},
		MISSING("""
				logging.appenders=file
				""") {
			@Override
			@Nullable
			String exceptionMessage() {
				return """
						Property missing. keys: ['logging.file.name' from PROPERTIES_STRING[logging.file.name], ENVIRONMENT_VARIABLES[logging_file_name], 'logging.appender.file.output' from PROPERTIES_STRING[logging.appender.file.output], ENVIRONMENT_VARIABLES[logging_appender_file_output]]""";
			}
		}

		,;

		private final String properties;

		private FileProperties(String properties) {
			this.properties = properties;
		}

		LogConfig config() {
			var fallback = LogProperties.StandardProperties.ENVIRONMENT_VARIABLES;
			var props = LogProperties.builder().fromProperties(properties.formatted(fileUri())).with(fallback).build();
			return LogConfig.builder().properties(props).build();
		}

		String fileUri() {
			return FILE_PATH;
		}

		@Nullable
		String exceptionMessage() {
			return null;
		}

	}

	static RainbowGum makeGum() {
		var config = LogConfig.builder() //
			.build();
		var gum = RainbowGum.builder(config).build();
		return gum;
	}

	private static Stream<Arguments> provideArgs() {
		return EnumCombinations.args(FileProperties.class, Events.class);
	}

}
