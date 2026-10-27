package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;

class FileOutputPropertiesTest {

	@Test
	void bufferSizeAcceptsDataSizeFormat() {
		var properties = LogProperties.builder()
			.fromProperties(
					"logging.appender.file.output.file.fileName=%s\nlogging.appender.file.output.file.bufferSize=8KB"
						.formatted(FILE_PATH))
			.build();
		new FileOutputBuilder("file").fromProperties(properties).build();
	}

	@Test
	void bufferSizeLargerThanIntFailsFromProperty() {
		var properties = LogProperties.builder()
			.fromProperties(
					"logging.appender.file.output.file.fileName=%s\nlogging.appender.file.output.file.bufferSize=3GB"
						.formatted(FILE_PATH))
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new FileOutputBuilder("file").fromProperties(properties).build());
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.file.FileOutputBuilder: "
						+ "bufferSize is too large: '3GB' (3221225472 bytes). Maximum is 2147483647 bytes.",
				e.getMessage());
	}

	@Test
	void bufferSizeLimitIsIntMaxFromBuilder() {
		new FileOutputBuilder("file").fileName(FILE_PATH).bufferSize(DataSize.ofBytes(Integer.MAX_VALUE)).build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new FileOutputBuilder("file").fileName(FILE_PATH)
					.bufferSize(DataSize.ofBytes(Integer.MAX_VALUE + 1L))
					.build());
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.file.FileOutputBuilder: "
						+ "bufferSize is too large: '2GB' (2147483648 bytes). Maximum is 2147483647 bytes.",
				e.getMessage());
	}

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
					""".formatted(PropertiesPaths.escape(file))).build();
			var config = LogConfig.builder().properties(properties).serviceLoader().build();
			var gum = RainbowGum.builder(config).build();
			try (var rg = gum.start()) {
				rg.log(TestLogEventFactory.of("test")
					.eventNoArg(Level.INFO, "hello", KeyValues.of(), (Throwable) null));
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
	 * Moved here from test/rainbowgum-test-core's ConfigFailureTest when FileOutput moved
	 * out of core - the "unusual error" Adam flagged for logging.file.name: pointing it
	 * at a path that exists but is a directory, not a file, throws a genuine
	 * java.io.FileNotFoundException - a name that's actively misleading here, since the
	 * path is very much found, just not openable as a file.
	 */
	@Test
	void testFileNameThatIsActuallyADirectory() throws IOException {
		Path dir = Path.of("target/FileOutputPropertiesTest-directory-not-a-file");
		Files.createDirectories(dir);
		var properties = LogProperties.builder().fromProperties("""
				logging.file.name=%s
				""".formatted(PropertiesPaths.escape(dir))).build();
		var config = LogConfig.builder().properties(properties).serviceLoader().build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		String absolutePath = dir.toAbsolutePath().toString();
		assertEquals(
				"""
						Error for property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], java.io.UncheckedIOException java.io.FileNotFoundException: %s (%s)
						  ↳ Failure providing Appender: 'file' from property: Fallback[logging.route.default.appenders]=[file, console].
						  ↳ Failure providing Appenders for route: 'default'."""
					.formatted(absolutePath, OS.WINDOWS.isCurrentOs() ? "Access is denied" : "Is a directory"),
				e.getMessage());
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
			@Nullable String exceptionMessage() {
				String uri = Paths.get(FILE_PATH).toUri().toString();
				String message = """
						Validation failed for io.jstach.rainbowgum.file.FileOutputBuilder:
						Error for property. key: 'logging.appender.file.output.file.bufferSize' from [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize], Invalid data size: 'blah'. Expected a number with an optional unit of b, k/kb/kib, m/mb/mib, g/gb/gib, or t/tb/tib (case insensitive, powers of 1024). Examples: '10MB', '512k', '1.5 GiB', '1048576'.
						Tried:
						    'logging.appender.file.output.file.bufferSize' from:
						        ENVIRONMENT_VARIABLES[logging_appender_file_output_file_bufferSize],
						        PROPERTIES_STRING[logging.appender.file.output.file.bufferSize],
						        [logging.file.name]->URI(%s?bufferSize=blah)[bufferSize]
						  ↳ Failure providing from property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], value: 'file:///./target/FileOutputPropertiesTest/file.log?bufferSize=blah'
						    Tried: 'logging.file.name' from ENVIRONMENT_VARIABLES[logging_file_name], PROPERTIES_STRING[logging.file.name]
						  ↳ Failure providing Appender: 'file' from property: Property[logging.appenders]=[file].
						  ↳ Failure providing Appenders for route: 'default'.""" //
					.formatted(uri, uri);
				return message;
			}
		},
		BAD_URI("""
				logging.file.name=:://
				""") {
			@Override
			@Nullable String exceptionMessage() {
				String message = """
						Error for property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], java.net.URISyntaxException Expected scheme name at index 0: :://
						Tried: 'logging.file.name' from ENVIRONMENT_VARIABLES[logging_file_name], PROPERTIES_STRING[logging.file.name]""";
				return message;
			}
		},
		MISSING("""
				logging.appenders=file
				""") {
			@Override
			@Nullable String exceptionMessage() {
				return """
						Property missing. keys:
						    'logging.file.name' from:
						        ENVIRONMENT_VARIABLES[logging_file_name],
						        PROPERTIES_STRING[logging.file.name],
						    'logging.appender.file.output.uri' from:
						        ENVIRONMENT_VARIABLES[logging_appender_file_output_uri],
						        PROPERTIES_STRING[logging.appender.file.output.uri]
						  ↳ Failure providing Appender: 'file' from property: Property[logging.appenders]=[file].
						  ↳ Failure providing Appenders for route: 'default'.""";
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
			return LogConfig.builder().properties(props).serviceLoader().build();
		}

		String fileUri() {
			return FILE_PATH;
		}

		@Nullable String exceptionMessage() {
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
