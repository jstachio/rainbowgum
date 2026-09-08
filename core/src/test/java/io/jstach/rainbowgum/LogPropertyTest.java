package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.FoundProperty.StringProperty;
import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.Result.Error;
import io.jstach.rainbowgum.LogProperty.Result.Missing;
import io.jstach.rainbowgum.LogProperty.Result.Success.PropertySuccess;
import io.jstach.rainbowgum.LogProperty.Result.Success.ValueSuccess;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProperty.Validator;

class LogPropertyTest {

	/*
	 * Regression test for a real bug found while investigating LogProperty coverage:
	 * ListGetter._propertyString (now LogProperty.propertyString's inlined equivalent)
	 * had "if (first) { first = true; }" instead of "first = false", so entries after the
	 * first were never comma-separated.
	 */
	@Test
	void testPropertyStringSeparatesMultipleListEntriesWithComma() {
		assertEquals("a,b,c", LogProperty.propertyString(List.of("a", "b", "c")));
	}

	@Test
	void testPropertyStringSeparatesMultipleMapEntriesWithAmpersand() {
		Map<String, String> map = new LinkedHashMap<>();
		map.put("a", "1");
		map.put("b", "2");
		assertEquals("a=1&b=2", LogProperty.propertyString(map));
	}

	@Test
	void testValidatorAddIfErrorIgnoresMissingButKeepsError() {
		Missing<Integer> missing = new Missing<>(List.of("key"), "missing");
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
	void testValueSuccessRejectsNullValueAndMap() {
		assertThrows(NullPointerException.class, () -> new ValueSuccess<String>("key", null));
		var success = new ValueSuccess<>("key", "5");
		assertEquals("key", success.key());
		assertEquals("Fallback[key]=5", success.describe());
		Result<Integer> mapped = success.map(Integer::parseInt);
		assertEquals(5, mapped.value());

		Result<Integer> errored = success.map(s -> {
			throw new NumberFormatException("nope");
		});
		assertTrue(errored instanceof Error<?>);
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testPropertySuccessRejectsNullValueAndMap() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "5");
		var found = new StringProperty(properties, "logging.p1", "5");
		assertThrows(NullPointerException.class, () -> new PropertySuccess<String>(found, null));
		var success = new PropertySuccess<>(found, "5");
		assertEquals("logging.p1", success.key());
		assertEquals("Property[logging.p1]=5", success.describe());
		Result<Integer> mapped = success.map(Integer::parseInt);
		assertEquals(5, mapped.value());

		Result<Integer> errored = success.map(s -> {
			throw new NumberFormatException("nope");
		});
		assertTrue(errored instanceof Error<?>);
	}

	@Test
	void testMissingRejectsEmptyKeys() {
		assertThrows(IllegalArgumentException.class, () -> new Missing<String>(List.of(), "message"));
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testMissingOrWithFallbackSupplier() {
		Missing<String> missing = new Missing<>(List.of("key"), "Property missing. keys: [key]");
		assertEquals("fallback", missing.or(() -> "fallback").value());
		assertThrows(PropertyMissingException.class, () -> missing.or(() -> null).value());
	}

	@Test
	void testMissingConvertAndDescribe() {
		Missing<String> missing = new Missing<>(List.of("key"), "Property missing. keys: [key]");
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
		Result<String> success = new ValueSuccess<>("key", "actual");
		assertEquals("actual", success.valueOrNull("fallback"));
		Result<String> missing = new Missing<>(List.of("key"), "missing");
		assertEquals("fallback", missing.valueOrNull("fallback"));
	}

	@Test
	void testResultOrWithFallbackObject() {
		Result<String> missing = new Missing<>(List.of("key"), "missing");
		assertEquals("fallback", missing.or("fallback").value());
		assertThrows(PropertyMissingException.class, () -> missing.or((String) null).value());
	}

	@Test
	void testResultOptional() {
		Result<String> success = new ValueSuccess<>("key", "actual");
		assertEquals(Optional.of("actual"), success.optional());
		Result<String> missing = new Missing<>(List.of("key"), "missing");
		assertEquals(Optional.empty(), missing.optional());
	}

	@Test
	void testResultGetReturnsItself() {
		Result<String> success = new ValueSuccess<>("key", "value");
		assertSame(success, success.get());
	}

	@Test
	void testEmptyStandardPropertiesHasNegativeOrder() {
		assertEquals(-1, LogProperties.StandardProperties.EMPTY.order());
	}

}
