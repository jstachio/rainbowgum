package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.Result.Error;
import io.jstach.rainbowgum.LogProperty.Result.Missing;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProperty.Validator;

class LogPropertyTest {

	@ParameterizedTest
	@ValueSource(strings = { "https://alice:fake-uri-secret@localhost/path?token=fake-query-secret",
			"../logs/a%20b?literal=a+b#fragment", "mailto:user@example.com", "" })
	void standaloneUriParserPreservesInput(String input) {
		assertEquals(input, LogProperty.parseURI(input).toString());
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://alice:fake-uri-secret@localhost/path with-space?token=fake-query-secret",
			"//alice:fake-uri-secret@localhost/path with-space?token=fake-query-secret",
			"https://alice:fake-uri-secret@localhost/%ZZ?token=fake-query-secret",
			"path with-space?token=fake-query-secret" })
	void standaloneUriParserOmitsInputAndUnsafeCause(String input) {
		var failure = assertThrows(IllegalArgumentException.class, () -> LogProperty.parseURI(input));
		var trace = new StringWriter();
		failure.printStackTrace(new PrintWriter(trace));
		assertFalse(trace.toString().contains("fake-uri-secret"), trace.toString());
		assertFalse(trace.toString().contains("fake-query-secret"), trace.toString());
		assertTrue(String.valueOf(failure.getMessage()).startsWith("Invalid URI:"));
		assertNull(failure.getCause());
		assertEquals(0, failure.getSuppressed().length);
		assertTrue(failure.getStackTrace().length > 0);
	}

	@Test
	void standaloneUriParserKeepsReasonAndPosition() {
		var failure = assertThrows(IllegalArgumentException.class, () -> LogProperty.parseURI("not a uri"));
		assertEquals("Invalid URI: Illegal character in path at index 3", failure.getMessage());
	}

	@Test
	void safeUriParserUsedInCustomMappingAlsoProtectsReports() {
		var check = new UnusedKeyCheck(List.of());
		var properties = check.wrap(LogProperties.builder()
			.fromProperties(
					"logging.endpoint=https://alice:fake-uri-secret@localhost/invalid path?token=fake-query-secret")
			.build());
		var result = properties.forKey("logging.endpoint").ofString().map(LogProperty::parseURI);
		var failure = assertThrows(ValidationException.class, () -> result.validateNow(getClass()));
		var trace = new StringWriter();
		failure.printStackTrace(new PrintWriter(trace));
		assertFalse(trace.toString().contains("fake-uri-secret"), trace.toString());
		assertFalse(trace.toString().contains("fake-query-secret"), trace.toString());
		assertTrue(String.valueOf(failure.getMessage()).contains("'logging.endpoint' from PROPERTIES_STRING"));
		assertEquals("<REDACTED>", check.read(properties).getFirst().value());
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://alice:fake-uri-secret@localhost/path with-space?token=fake-query-secret",
			"//alice:fake-uri-secret@localhost/path with-space?token=fake-query-secret",
			"https://alice:fake-uri-secret@localhost/%ZZ?token=fake-query-secret",
			"path with-space?token=fake-query-secret" })
	void uriSyntaxFailureOmitsInputAndOriginalCause(String value) {
		var properties = LogProperties.builder().fromFunction(key -> value).build();
		var failure = assertThrows(ValidationException.class,
				() -> properties.forKey("logging.endpoint").ofURI().validateNow(getClass()));
		var trace = new StringWriter();
		failure.printStackTrace(new PrintWriter(trace));
		assertFalse(trace.toString().contains("fake-uri-secret"), trace.toString());
		assertFalse(trace.toString().contains("fake-query-secret"), trace.toString());
		assertTrue(String.valueOf(failure.getMessage()).contains("Invalid URI:"));
		var cause = failure.getCause();
		assertNotNull(cause);
		assertNull(cause.getCause());
		assertEquals(0, cause.getSuppressed().length);
		assertTrue(cause.getStackTrace().length > 0);
	}

	@Test
	void rejectedUriValueIsRedactedWhenReadAgainForReports() {
		var check = new UnusedKeyCheck(List.of());
		var properties = check.wrap(LogProperties.builder()
			.fromProperties("logging.endpoint=path with-space?token=fake-query-secret")
			.build());
		assertThrows(ValidationException.class,
				() -> properties.forKey("logging.endpoint").ofURI().validateNow(getClass()));
		assertEquals("<REDACTED>", check.read(properties).getFirst().value());
	}

	@Test
	void validUriIsNotChangedBySafeConversion() {
		String value = "https://alice:fake-uri-secret@localhost/path?token=fake-query-secret";
		var properties = LogProperties.builder().fromFunction(key -> value).build();
		assertEquals(URI.create(value), properties.forKey("logging.endpoint").ofURI().validateNow(getClass()));
	}

	@Test
	void booleanAcceptsOnlyTrueOrFalse() {
		var properties = LogProperties.builder().description("TEST").fromProperties("""
				logging.a=TRUE
				logging.b=false
				logging.c=yes
				logging.d=
				""").build();
		assertEquals(true, properties.forKey("logging.a").ofBoolean().validateNow(LogPropertyTest.class));
		assertEquals(false, properties.forKey("logging.b").ofBoolean().validateNow(LogPropertyTest.class));
		var e = assertThrows(ValidationException.class,
				() -> properties.forKey("logging.c").ofBoolean().validateNow(LogPropertyTest.class));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogPropertyTest:
						Error for property. key: 'logging.c' from TEST[logging.c], 'yes' is not a valid value for java.lang.Boolean. Valid values: 'true', 'false'""",
				e.getMessage());
		var empty = assertThrows(ValidationException.class,
				() -> properties.forKey("logging.d").ofBoolean().validateNow(LogPropertyTest.class));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogPropertyTest:
						Error for property. key: 'logging.d' from TEST[logging.d], '' is not a valid value for java.lang.Boolean. Valid values: 'true', 'false'""",
				empty.getMessage());
	}

	@Test
	void chainedConversionErrorRedactsSensitivePropertyValue() {
		var properties = LogProperties.builder().description("TEST").fromProperties("""
				logging.password=abc123
				logging.count=bad
				""").build();
		var result = properties.forKey("logging.password")
			.ofString()
			.map(value -> properties.forKey("logging.count").ofInt().validateNow(LogPropertyTest.class));
		var error = assertThrows(ValidationException.class, () -> result.validateNow(LogPropertyTest.class));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogPropertyTest:
						Validation failed for io.jstach.rainbowgum.LogPropertyTest:
						Error for property. key: 'logging.count' from TEST[logging.count], java.lang.NumberFormatException For input string: "bad"
						  ↳ Error converting property. key: 'logging.password' from TEST[logging.password], value: '<REDACTED>'""",
				error.getMessage());
	}

	@Test
	void conversionErrorListsNestedSourcesWithoutSplittingDescriptionCommas() {
		var first = LogProperties.builder()
			.description("FIRST, SECOND")
			.fromProperties("logging.encoder.console.pattern=bad")
			.build();
		var third = LogProperties.builder().description("THIRD").fromProperties("").build();
		var fourth = LogProperties.builder().description("FOURTH").fromProperties("").build();
		var properties = LogProperties.of(List.of(first, LogProperties.of(List.of(third, fourth))));
		var error = assertThrows(PropertyConvertException.class,
				() -> ((Error<Integer>) properties.forKey("logging.encoder.console.pattern").ofInt()).value());
		assertEquals(
				"""
						Error for property. key: 'logging.encoder.console.pattern' from FIRST, SECOND[logging.encoder.console.pattern], java.lang.NumberFormatException For input string: "bad"
						Tried:
						    'logging.encoder.console.pattern' from:
						        FIRST, SECOND[logging.encoder.console.pattern],
						        THIRD[logging.encoder.console.pattern],
						        FOURTH[logging.encoder.console.pattern]""",
				error.getMessage());
	}

	@Test
	void enumValueReportsLowercaseOptionsAndAliases() {
		var thrown = assertThrows(IllegalArgumentException.class,
				() -> LogProperty.enumValue(LogAlerts.UnobservedErrorsAction.class, "BOGUS", "true", "false"));
		assertEquals("'BOGUS' is not a valid value for io.jstach.rainbowgum.LogAlerts.UnobservedErrorsAction. "
				+ "Valid values: 'none', 'dump', 'fail', 'true', 'false'", thrown.getMessage());
		assertEquals(LogAlerts.UnobservedErrorsAction.DUMP,
				LogProperty.enumValue(LogAlerts.UnobservedErrorsAction.class, "dUmP"));
	}

	@Test
	void testValidatorAddIfErrorIgnoresMissingButKeepsError() {
		Missing<Integer> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		Error<Integer> error = new Error<>("key", "bad value", new NumberFormatException("nope"));
		var validator = Validator.of(LogPropertyTest.class);
		validator.addIfError(missing);
		validator.addIfError(error);
		assertThrows(ValidationException.class, validator::validate);

		var validator2 = Validator.of(LogPropertyTest.class);
		validator2.addIfError(missing);
		validator2.validate();
	}

	@Test
	void testValidationExceptionOfUsesSameMessagePrefixAsValidate() {
		var cause = new IllegalArgumentException("capacity should be greater than 0");
		var e = ValidationException.of(LogPropertyTest.class, cause);
		assertEquals("Validation failed for io.jstach.rainbowgum.LogPropertyTest: capacity should be greater than 0",
				e.getMessage());
		var actualCause = e.getCause();
		assertNotNull(actualCause);
		assertSame(cause, actualCause);
	}

	@Test
	void testValidationExceptionOfReturnsRatherThanThrows() {
		// of(...) must not itself throw - the caller decides whether/when to.
		assertNotNull(ValidationException.of(LogPropertyTest.class, new IllegalArgumentException("boom")));
	}

	@Test
	void testValidateNowReturnsValueOnSuccess() {
		var success = new PropertySuccess<>(LogProperties.StandardProperties.EMPTY,
				LogProperties.StandardProperties.EMPTY, "key", "actual", PropertySuccess.Kind.VALUE, "actual");
		assertEquals("actual", success.validateNow(LogPropertyTest.class));
	}

	@Test
	void testValidateNowTreatsStillMissingAsFailure() {
		Missing<String> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		assertThrows(ValidationException.class, () -> missing.validateNow(LogPropertyTest.class));
	}

	@Test
	void testValidateNowWithFallbackAppliedFirstDoesNotTreatItAsMissing() {
		Missing<String> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		assertEquals("fallback", missing.or("fallback").validateNow(LogPropertyTest.class));
	}

	@Test
	void testValidateNowThrowsOnError() {
		Error<String> error = new Error<>("key", "bad value", new NumberFormatException("nope"));
		assertThrows(ValidationException.class, () -> error.validateNow(LogPropertyTest.class));
	}

	@Test
	void testPropertyFunctionSneakyThrowsCheckedException() {
		PropertyFunction<String, String, IOException> f = new PropertyFunction<>() {
			@Override
			public String _apply(String t) throws IOException {
				throw new IOException("boom");
			}
		};
		var e = assertThrows(IOException.class, () -> f.apply("x"));
		assertEquals("boom", e.getMessage());
	}

	@Test
	void testPropertyConvertExceptionKey() {
		var e = new PropertyConvertException("logging.p1", "bad conversion", null);
		assertEquals("logging.p1", e.key());
		assertEquals("bad conversion", e.getMessage());
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testValueKindPropertySuccessRejectsNullValueAndMap() {
		var properties = LogProperties.StandardProperties.EMPTY;
		assertThrows(NullPointerException.class, () -> new PropertySuccess<String>(properties, properties, "key", "5",
				PropertySuccess.Kind.VALUE, null));
		var success = new PropertySuccess<>(properties, properties, "key", "5", PropertySuccess.Kind.VALUE, "5");
		assertEquals("key", success.key());
		assertEquals("Fallback[key]=5", success.describe());
		Result<Integer> mapped = success.map(Integer::parseInt);
		assertEquals(5, mapped.validateNow(LogPropertyTest.class));

		Result<Integer> errored = success.map(s -> {
			throw new NumberFormatException("nope");
		});
		assertTrue(errored instanceof Error<?>);
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testPropertySuccessRejectsNullValueAndMap() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "5");
		assertThrows(NullPointerException.class, () -> new PropertySuccess<String>(properties, properties, "logging.p1",
				"5", PropertySuccess.Kind.STRING, null));
		var success = new PropertySuccess<>(properties, properties, "logging.p1", "5", PropertySuccess.Kind.STRING,
				"5");
		assertEquals("logging.p1", success.key());
		assertEquals("Property[logging.p1]=5", success.describe());
		Result<Integer> mapped = success.map(Integer::parseInt);
		assertEquals(5, mapped.validateNow(LogPropertyTest.class));

		Result<Integer> errored = success.map(s -> {
			throw new NumberFormatException("nope");
		});
		assertTrue(errored instanceof Error<?>);
	}

	@Test
	void testMissingRejectsEmptyKeys() {
		assertThrows(IllegalArgumentException.class,
				() -> new Missing<String>(LogProperties.StandardProperties.EMPTY, List.of(), "message"));
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testMissingOrWithFallbackSupplier() {
		Missing<String> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"),
				"Property missing. key: [key]");
		assertEquals("fallback", missing.or(() -> "fallback").validateNow(LogPropertyTest.class));
		assertThrows(PropertyMissingException.class, () -> ((Missing<String>) missing.or(() -> null)).value());
	}

	@Test
	void testMissingConvertAndDescribe() {
		Missing<String> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"),
				"Property missing. key: [key]");
		Missing<Integer> converted = missing.convert();
		assertEquals("Missing[[key]]", converted.describe());
		assertEquals(missing, missing.map(Integer::parseInt));
	}

	@Test
	void testErrorAlwaysThrowsOnValueAccessAndOrReturnsThis() {
		var cause = new NumberFormatException("nope");
		Error<String> error = new Error<>("key", "bad value", cause);
		assertThrows(PropertyConvertException.class, () -> error.valueOrNull());
		assertThrows(PropertyConvertException.class, error::value);
		assertThrows(PropertyConvertException.class, () -> error.or(() -> "fallback").value());
		assertEquals(error, error.or("fallback"));
		assertEquals(error, error.or(() -> "fallback"));
		assertEquals("Error[key](bad value)", error.describe());
		Error<Integer> converted = error.convert();
		assertEquals(error.key(), converted.key());
		assertEquals(error, error.map(Integer::parseInt));
	}

	@Test
	void testResultValueOrNullWithFallback() {
		var success = new PropertySuccess<>(LogProperties.StandardProperties.EMPTY,
				LogProperties.StandardProperties.EMPTY, "key", "actual", PropertySuccess.Kind.VALUE, "actual");
		assertEquals("actual", success.valueOrNull("fallback"));
		var missing = new Missing<String>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		assertEquals("fallback", missing.valueOrNull("fallback"));
	}

	@Test
	void testResultOrWithFallbackObject() {
		Missing<String> missing = new Missing<>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		assertEquals("fallback", missing.or("fallback").validateNow(LogPropertyTest.class));
		assertThrows(PropertyMissingException.class, () -> ((Missing<String>) missing.or((String) null)).value());
	}

	@Test
	void testResultOptional() {
		var success = new PropertySuccess<>(LogProperties.StandardProperties.EMPTY,
				LogProperties.StandardProperties.EMPTY, "key", "actual", PropertySuccess.Kind.VALUE, "actual");
		assertEquals(Optional.of("actual"), success.optional());
		var missing = new Missing<String>(LogProperties.StandardProperties.EMPTY, List.of("key"), "missing");
		assertEquals(Optional.empty(), missing.optional());
	}

	@Test
	void testEmptyStandardPropertiesHasNegativeOrder() {
		assertEquals(-1, LogProperties.StandardProperties.EMPTY.order());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"logging.appender.a.output.otlp.headers|Authorization=Bearer abc,X-Team=core|Authorization=<REDACTED>,X-Team=core",
			"logging.appender.a.output.otlp.apiKey|abc|<REDACTED>",
			"logging.appender.a.output.uri|otlp://user:pw@host/?token=abc&gzip=true|otlp://<REDACTED>@host/?token=<REDACTED>&gzip=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?headers=Authorization%3DBearer%20abc&gzip=true|otlp://host/?headers=<REDACTED>&gzip=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?headers=Authorization=Bearer%20abc|otlp://host/?headers=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?headers=X-Team%3Dcore&gzip=true|otlp://host/?headers=<REDACTED>&gzip=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=http%3A%2F%2Falice%3Apw%40host%2Flogs&gzip=true|otlp://host/?endpoint=<REDACTED>&gzip=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=http://alice:pw@host/logs|otlp://host/?endpoint=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=http%3A%2F%2Falice%3Apw%40host%2Flogs%3Fmode%3Dappend|otlp://host/?endpoint=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=%2F%2Falice%3Apw%40my_host%2Flogs|otlp://host/?endpoint=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=http%3A%2F%2Falice%40host%2Flogs|otlp://host/?endpoint=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?headers=proxy%3Dhttp%253A%252F%252Falice%253Apw%2540host|otlp://host/?headers=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?endpoint=http%3A%2F%2Fhost%2Fa%40b%3Fmode%3Dappend|otlp://host/?endpoint=<REDACTED>",
			"logging.appender.a.output.uri|otlp://host/?address=mailto%3Aalice%40example.com|otlp://host/?address=<REDACTED>",
			"logging.appender.a.encoder.pattern.pattern|%d user=%X{user} %msg%n|%d user=%X{user} %msg%n" })
	void redactValueRedactsSensitivePairs(String key, String value, String expected) {
		assertEquals(expected, LogRedactor.redactValue(key, value));
	}

}
