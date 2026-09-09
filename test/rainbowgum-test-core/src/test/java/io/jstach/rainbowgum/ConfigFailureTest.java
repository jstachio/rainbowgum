package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * End to end golden string tests: each {@link ConfigFailure} constant is a full
 * properties file that should make RainbowGum fail to start, and the exact exception
 * message it should fail with - being an enum, a new case just needs to be added as a new
 * constant and it is automatically picked up by {@link #test(ConfigFailure)}.
 * {@link FakeEncoderConfigurator} (host/label/port/endpoint/tags/headers, i.e.
 * String/Integer/URI/List/Map) is registered for the cases that need a component with a
 * spread of property types to fail conversion/validation on - the built-in scenarios
 * (unregistered scheme, bad level) need no such fixture.
 */
class ConfigFailureTest {

	@ParameterizedTest
	@EnumSource(ConfigFailure.class)
	void test(ConfigFailure c) {
		var config = LogConfig.builder().properties(c.properties()).configurator(new FakeEncoderConfigurator()).build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		assertEquals(c.expectedMessage(), e.getMessage());
	}

	enum ConfigFailure {

		unregisteredOutputScheme("""
				logging.appenders=myapp
				logging.appender.myapp.output=bogus:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], io.jstach.rainbowgum.LogProviderRef$NotFoundException No output found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output]"""),

		badLevelValue("""
				logging.level=NOTALEVEL
				""",
				"""
						Error for property. key: 'logging.level' from PROPERTIES_STRING[logging.level], java.lang.IllegalArgumentException Cannot parse Level from input. input='NOTALEVEL'
						Tried: 'logging.level' from PROPERTIES_STRING[logging.level]"""),

		unregisteredPublisherScheme("""
				logging.route.default.publisher=bogus:///
				""",
				"""
						Error for property. key: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher], io.jstach.rainbowgum.LogProviderRef$NotFoundException No publisher found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher]"""),

		unregisteredEncoderScheme("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=bogus:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], io.jstach.rainbowgum.LogProviderRef$NotFoundException No encoder found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMissingRequiredStringProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Property missing. keys: ['logging.encoder.myapp.host' from PROPERTIES_STRING[logging.encoder.myapp.host], [logging.appender.myapp.encoder]->URI(fake:///)[host]]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMalformedIntProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.port=notanumber
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.port' from PROPERTIES_STRING[logging.encoder.myapp.port], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.encoder.myapp.port' from PROPERTIES_STRING[logging.encoder.myapp.port], [logging.appender.myapp.encoder]->URI(fake:///)[port]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		encoderMalformedUriProperty("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.endpoint=not a uri with spaces
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: 'logging.encoder.myapp.endpoint' from PROPERTIES_STRING[logging.encoder.myapp.endpoint], java.net.URISyntaxException Illegal character in path at index 3: not a uri with spaces
						Tried: 'logging.encoder.myapp.endpoint' from PROPERTIES_STRING[logging.encoder.myapp.endpoint], [logging.appender.myapp.encoder]->URI(fake:///)[endpoint]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		/*
		 * Hits Result.map() at the end of the fluent chain (FakeEncoderBuilder's "label"
		 * field: properties.forKey(...).ofString().map(...)) instead of
		 * LogProperty.ofXxx()/Result.convert() - see the comment on FakeEncoderBuilder's
		 * _label. The terser "Error for property. key: <plain key>, <message>" (no "from
		 * ..."/no "Tried:" line) is exactly the message shape Success.map()'s
		 * Error.of(key, e) produces, distinct from convert()'s richError().
		 */
		encoderCustomStringValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.label=bad
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: logging.encoder.myapp.label, label must not be 'bad'
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		// same Result.map() (not convert()) path as encoderCustomStringValidationFailure
		// above, but on a List.
		encoderCustomListValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.tags=good,bad
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: logging.encoder.myapp.tags, tags must not contain 'bad'
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		// same Result.map() (not convert()) path as encoderCustomStringValidationFailure
		// above, but on a Map.
		encoderCustomMapValidationFailure("""
				logging.appenders=myapp
				logging.appender.myapp.output=list:///
				logging.appender.myapp.encoder=fake:///
				logging.encoder.myapp.host=h
				logging.encoder.myapp.headers=bad=1
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Error for property. key: logging.encoder.myapp.headers, headers must not contain key 'bad'
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]"""),

		/*
		 * Chained-source case: exercises ListLogProperties/CompositeLogProperties by
		 * overriding properties() to combine two separately-built LogProperties via
		 * LogProperties.of(List.of(...)) instead of parsing one string.
		 * ListLogProperties.description(key) joins every member's own description for the
		 * key regardless of which member actually had it, so the "Tried:" line below
		 * lists PROPERTIES_STRING twice - once per chained member - proving the composite
		 * is really being walked, not just one of its members.
		 */
		unregisteredOutputSchemeAcrossChainedProperties("",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], io.jstach.rainbowgum.LogProviderRef$NotFoundException No output found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'
						Tried: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], PROPERTIES_STRING[logging.appender.myapp.output]""") {
			@Override
			LogProperties properties() {
				var appenders = LogProperties.builder().fromProperties("""
						logging.appenders=myapp
						""").build();
				var output = LogProperties.builder().fromProperties("""
						logging.appender.myapp.output=bogus:///
						""").build();
				return LogProperties.of(List.of(appenders, output));
			}
		},

		// same chained-composite idea as unregisteredOutputSchemeAcrossChainedProperties
		// above, but for a missing (rather than found-but-invalid) property - the
		// "keys: [...]" list below shows the missing key's chained description too.
		encoderMissingRequiredStringPropertyAcrossChainedProperties("",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error converting property. key: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], value: 'fake:///' cause:
						Validation failed for io.jstach.rainbowgum.FakeEncoderBuilder:
						Property missing. keys: ['logging.encoder.myapp.host' from PROPERTIES_STRING[logging.encoder.myapp.host], PROPERTIES_STRING[logging.encoder.myapp.host], [logging.appender.myapp.encoder]->URI(fake:///)[host]]
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder], PROPERTIES_STRING[logging.appender.myapp.encoder]""") {
			@Override
			LogProperties properties() {
				var appenders = LogProperties.builder().fromProperties("""
						logging.appenders=myapp
						logging.appender.myapp.output=list:///
						""").build();
				var encoder = LogProperties.builder().fromProperties("""
						logging.appender.myapp.encoder=fake:///
						""").build();
				return LogProperties.of(List.of(appenders, encoder));
			}
		},

		/*
		 * The "unusual error" Adam flagged for logging.file.name: pointing it at a path
		 * that exists but is a directory, not a file, throws a genuine
		 * java.io.FileNotFoundException - a name that's actively misleading here, since
		 * the path is very much found, just not openable as a file. Overrides both
		 * properties() (to create the directory as a side effect) and expectedMessage()
		 * (the message embeds an absolute path that has to be computed at test time, not
		 * hardcoded).
		 */
		fileNameThatIsActuallyADirectory {

			private final Path dir = Path.of("target/ConfigFailureTest-directory-not-a-file");

			@Override
			LogProperties properties() {
				try {
					Files.createDirectories(dir);
				}
				catch (IOException e) {
					throw new UncheckedIOException(e);
				}
				return LogProperties.builder().fromProperties("""
						logging.file.name=%s
						""".formatted(dir)).build();
			}

			@Override
			String expectedMessage() {
				String absolutePath = dir.toAbsolutePath().toString();
				return """
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'file' from property: Fallback[logging.route.default.appenders]=[file, console]. cause:
						Error for property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], java.io.UncheckedIOException java.io.FileNotFoundException: %s (Is a directory)
						Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name]""" //
					.formatted(absolutePath);
			}

		};

		private final String propertiesString;

		private final String expectedMessage;

		ConfigFailure(String propertiesString, String expectedMessage) {
			this.propertiesString = propertiesString;
			this.expectedMessage = expectedMessage;
		}

		ConfigFailure() {
			this("", "");
		}

		LogProperties properties() {
			return LogProperties.builder().fromProperties(propertiesString).build();
		}

		String expectedMessage() {
			return expectedMessage;
		}

	}

}
