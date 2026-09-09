package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * End to end golden string tests: each case is a full properties file that should make
 * RainbowGum fail to start, and the exact exception message it should fail with.
 * {@link FakeEncoderConfigurator} (host/port/endpoint/tags/headers, i.e.
 * String/Integer/URI/List/Map) is registered for the cases that need a component with a
 * spread of property types to fail conversion/validation on - the built-in scenarios
 * (unregistered scheme, bad level) need no such fixture.
 */
class ConfigFailureTest {

	@ParameterizedTest
	@MethodSource("cases")
	void test(Case c) {
		var props = LogProperties.builder().fromProperties(c.properties()).build();
		var config = LogConfig.builder().properties(props).configurator(new FakeEncoderConfigurator()).build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		assertEquals(c.expectedMessage(), e.getMessage());
	}

	static Stream<Case> cases() {
		return CASES.stream();
	}

	/*
	 * The "unusual error" Adam flagged for logging.file.name: pointing it at a path that
	 * exists but is a directory, not a file, throws a genuine
	 * java.io.FileNotFoundException - a name that's actively misleading here, since the
	 * path is very much found, just not openable as a file. Not part of the parameterized
	 * CASES above since the expected message embeds an absolute path that has to be
	 * computed at test time, not hardcoded.
	 */
	@Test
	void testFileNameThatIsActuallyADirectoryThrowsMisleadingFileNotFoundException() throws IOException {
		Path dir = Path.of("target/ConfigFailureTest-directory-not-a-file");
		Files.createDirectories(dir);
		var props = LogProperties.builder().fromProperties("""
				logging.file.name=%s
				""".formatted(dir)).build();
		var config = LogConfig.builder().properties(props).build();
		var e = assertThrows(RuntimeException.class, () -> RainbowGum.builder(config).build().start());
		String absolutePath = dir.toAbsolutePath().toString();
		String expected = """
				Failure providing Appenders for route: 'default'. cause:
				Failure providing Appender: 'file' from property: Fallback[logging.route.default.appenders]=[file, console]. cause:
				Error for property. key: 'logging.file.name' from PROPERTIES_STRING[logging.file.name], java.io.UncheckedIOException java.io.FileNotFoundException: %s (Is a directory)
				Tried: 'logging.file.name' from PROPERTIES_STRING[logging.file.name]""" //
			.formatted(absolutePath);
		assertEquals(expected, e.getMessage());
	}

	record Case(String name, String properties, String expectedMessage) {
		@Override
		public String toString() {
			return name;
		}
	}

	static final List<Case> CASES = List.of( //
			unregisteredOutputScheme(), //
			badLevelValue(), //
			unregisteredPublisherScheme(), //
			encoderMissingRequiredStringProperty(), //
			encoderMalformedIntProperty(), //
			encoderMalformedUriProperty(), //
			encoderCustomStringValidationFailure(), //
			encoderCustomListValidationFailure(), //
			encoderCustomMapValidationFailure() //
	);

	/*
	 * Hits Result.map() at the end of the fluent chain (FakeEncoderBuilder's "label"
	 * field: properties.forKey(...).ofString().map(...)) instead of
	 * LogProperty.ofXxx()/Result.convert() - see the comment on FakeEncoderBuilder's
	 * _label. The terser "Error for property. key: <plain key>, <message>" (no "from
	 * ..."/no "Tried:" line) is exactly the message shape Success.map()'s Error.of(key,
	 * e) produces, distinct from convert()'s richError().
	 */
	private static Case encoderCustomStringValidationFailure() {
		return new Case("encoderCustomStringValidationFailure", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

	private static Case unregisteredOutputScheme() {
		return new Case("unregisteredOutputScheme", """
				logging.appenders=myapp
				logging.appender.myapp.output=bogus:///
				""",
				"""
						Failure providing Appenders for route: 'default'. cause:
						Failure providing Appender: 'myapp' from property: Property[logging.appenders]=[myapp]. cause:
						Error for property. key: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output], io.jstach.rainbowgum.LogProviderRef$NotFoundException No output found. Scheme not registered. scheme: 'bogus',  URI: 'bogus:///'
						Tried: 'logging.appender.myapp.output' from PROPERTIES_STRING[logging.appender.myapp.output]""");
	}

	private static Case badLevelValue() {
		return new Case("badLevelValue", """
				logging.level=NOTALEVEL
				""",
				"""
						Error for property. key: 'logging.level' from PROPERTIES_STRING[logging.level], java.lang.IllegalArgumentException Cannot parse Level from input. input='NOTALEVEL'
						Tried: 'logging.level' from PROPERTIES_STRING[logging.level]""");
	}

	/*
	 * The NoSuchElementException leaking through here (instead of a "no publisher found"
	 * style message like the unregistered-output-scheme case above) looks like a real,
	 * pre-existing rough edge in route publisher resolution - captured as-is rather than
	 * "fixed" here, since this test's job is pinning current behavior, not changing it.
	 */
	private static Case unregisteredPublisherScheme() {
		return new Case("unregisteredPublisherScheme", """
				logging.route.default.publisher=bogus:///
				""",
				"""
						Error for property. key: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher], java.util.NoSuchElementException No value present
						Tried: 'logging.route.default.publisher' from PROPERTIES_STRING[logging.route.default.publisher]""");
	}

	private static Case encoderMissingRequiredStringProperty() {
		return new Case("encoderMissingRequiredStringProperty", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

	private static Case encoderMalformedIntProperty() {
		return new Case("encoderMalformedIntProperty", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

	private static Case encoderMalformedUriProperty() {
		return new Case("encoderMalformedUriProperty", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

	private static Case encoderCustomListValidationFailure() {
		return new Case("encoderCustomListValidationFailure", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

	private static Case encoderCustomMapValidationFailure() {
		return new Case("encoderCustomMapValidationFailure", """
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
						Tried: 'logging.appender.myapp.encoder' from PROPERTIES_STRING[logging.appender.myapp.encoder]""");
	}

}
