package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.EnumCombinations;
import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.FileOutputTest.Events;

class FileOutputPropertiesTest {

	/*
	 * This is the exact scenario Spring Boot users hit: logging.file.name set to a bare
	 * relative filename with no "./" prefix and no scheme. Before the fix RainbowGum
	 * would misinterpret "some.log" as a URI scheme (like "stdout") and fail with "No
	 * output found. Scheme not registered."
	 */
	@Test
	void testBareFilenameWithNoPrefixIsTreatedAsAFilePath() throws IOException {
		Path file = Path.of("rainbowgum-bare-filename-test.log");
		Files.deleteIfExists(file);
		try {
			var properties = LogProperties.builder().fromProperties("""
					logging.file.name=%s
					""".formatted(file)).build();
			var config = LogConfig.builder().properties(properties).build();
			var gum = RainbowGum.builder(config).build();
			try (var rg = gum.start()) {
				rg.log(LogEvent.of(Level.INFO, "test", "hello", KeyValues.of(), null));
			}
			assertTrue(Files.exists(file), "expected " + file + " to have been created");
			String content = Files.readString(file);
			assertTrue(content.contains("hello"), "expected file content to contain the logged message: " + content);
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	/*
	 * ValidationException.validate() only ever kept the *first* Error's cause in every
	 * other test in this file - each only has one malformed property. Here both uri and
	 * bufferSize are malformed at once, producing two Result.Error entries in the same
	 * Validator: uri (added first, via addIfError) becomes the reported cause, and
	 * bufferSize's error (added last, via add) still shows up in the message but its
	 * cause is discarded in favor of the first. Unlike the message text (which the
	 * enum-based test() below checks and includes every error), the *cause* field is only
	 * ever the first one - a distinction the message alone can't prove, so this checks
	 * getCause() directly instead of going through the shared enum harness.
	 */
	@Test
	void testValidationExceptionKeepsOnlyFirstErrorsCauseWhenMultiplePropertiesAreMalformed() throws IOException {
		String fileName = FILE_PATH;
		try {
			String properties = """
					logging.appenders=file
					logging.file.name=%s
					logging.output.file.uri=not a uri with spaces
					logging.output.file.bufferSize=blah
					""".formatted(fileName);
			var config = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties(properties).build())
				.build();
			var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
			assertEquals(
					"""
							Failure providing Appenders for route: 'default'. cause:
							Failure providing Appender: 'file' from property: Property[logging.appenders]=[file]. cause:
							Error converting property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], value: './target/FileOutputPropertiesTest/file.log' cause:
							Validation failed for io.jstach.rainbowgum.output.FileOutputBuilder:
							Error for property. key: 'logging.output.file.uri' from PROPERTIES_STRING[logging.output.file.uri], java.net.URISyntaxException Illegal character in path at index 3: not a uri with spaces
							Tried: 'logging.output.file.uri' from PROPERTIES_STRING[logging.output.file.uri]
							Error for property. key: 'logging.output.file.bufferSize' from PROPERTIES_STRING[logging.output.file.bufferSize], java.lang.NumberFormatException For input string: "blah"
							Tried: 'logging.output.file.bufferSize' from PROPERTIES_STRING[logging.output.file.bufferSize]
							Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name]""",
					e.getMessage());
			Throwable cause = e;
			while (cause.getCause() != null && cause.getCause() != cause) {
				cause = cause.getCause();
			}
			assertInstanceOf(java.net.URISyntaxException.class, cause,
					"the ValidationException's cause should be the *first* malformed property's (uri) exception, not bufferSize's, since ValidationException.validate() only keeps the first");
		}
		finally {
			Files.deleteIfExists(Path.of(fileName));
		}
	}

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
		catch (LogProperty.PropertyConvertException | LogProperty.PropertyMissingException
				| LogProvider.ProvisionException e) {
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
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'file' from property: Property[logging.appenders]=[file]. cause:
						Error converting property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], value: 'file:///./target/FileOutputPropertiesTest/file.log?bufferSize=blah' cause:
						Validation failed for io.jstach.rainbowgum.output.FileOutputBuilder:
						Error for property. key: 'logging.output.file.bufferSize' from [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize], java.lang.NumberFormatException For input string: "blah"
						Tried: 'logging.output.file.bufferSize' from PROPERTIES_STRING[logging.output.file.bufferSize], ENVIRONMENT_VARIABLES[logging_output_file_bufferSize], [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize]
						Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], ENVIRONMENT_VARIABLES[logging_file_name]""" //
					.formatted(uri, uri);
				return message;
			}
		},
		/*
		 * Unlike URI_WITH_BAD_BUFFER_SIZE (bufferSize is a required property, so a bad
		 * value goes through Validator.add()), FileOutputBuilder's own "uri" property is
		 * optional and goes through Validator.addIfError() - a branch with no test
		 * coverage anywhere before this. logging.file.name supplies a valid uri that
		 * FileOutputBuilder.uri(...) is pre-set with; logging.output.file.uri then
		 * overrides it with a malformed value, which addIfError() must still catch and
		 * report even though a fallback uri was already present.
		 */
		BAD_OUTPUT_URI("""
				logging.appenders=file
				logging.file.name=%s
				logging.output.file.uri=not a uri with spaces
				""") {
			@Override
			@Nullable
			String exceptionMessage() {
				return """
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'file' from property: Property[logging.appenders]=[file]. cause:
						Error converting property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], value: './target/FileOutputPropertiesTest/file.log' cause:
						Validation failed for io.jstach.rainbowgum.output.FileOutputBuilder:
						Error for property. key: 'logging.output.file.uri' from PROPERTIES_STRING[logging.output.file.uri], java.net.URISyntaxException Illegal character in path at index 3: not a uri with spaces
						Tried: 'logging.output.file.uri' from PROPERTIES_STRING[logging.output.file.uri], ENVIRONMENT_VARIABLES[logging_output_file_uri]
						Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], ENVIRONMENT_VARIABLES[logging_file_name]""";
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
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'file' from property: Property[logging.appenders]=[file]. cause:
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
