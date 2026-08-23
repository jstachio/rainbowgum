package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties.FoundProperty.StringProperty;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;
import io.jstach.rainbowgum.LogProperty.PropertySupport;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.Result.Error;
import io.jstach.rainbowgum.LogProperty.Result.Missing;
import io.jstach.rainbowgum.LogProperty.Result.Success;
import io.jstach.rainbowgum.LogProperty.Result.Success.PropertySuccess;
import io.jstach.rainbowgum.LogProperty.Result.Success.ValueSuccess;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProperty.Validator;

class LogPropertyTest {

	/*
	 * Regression test for a real bug found while investigating LogProperty coverage:
	 * ListGetter._propertyString had "if (first) { first = true; }" instead of "first =
	 * false", so entries after the first were never comma-separated. No production
	 *
	 * @LogConfigurable builder currently has a real List<String> property (only the
	 * test/rainbowgum-test-config demo fixture does), so this calls the package-private
	 * static method directly rather than through a real caller.
	 */
	@Test
	void testListPropertyStringSeparatesMultipleEntriesWithComma() {
		assertEquals("a,b,c", ListGetter._propertyString(List.of("a", "b", "c")));
	}

	@Test
	void testValidation() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build()
			.put("p1", "v1");
		var b = LogProperty.builder().withPrefix(LogProperties.ROOT_PREFIX);
		var r1 = b.ofInt().build("p1").get(properties).or(1);
		var r2 = b.build("p2").get(properties);
		var r3 = b.build("p3").get(properties).or("fallback");
		@SuppressWarnings("null")
		String actual = assertThrows(LogProperty.ValidationException.class,
				() -> ValidationException.validate(LogPropertyTest.class, List.<Result<?>>of(r1, r2, r3)))
			.getMessage();
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogPropertyTest:
				Error for property. key: 'logging.p1' from custom mutable[p1], java.lang.NumberFormatException For input string: "v1"
				Tried: 'logging.p1' from custom mutable[p1]
				Property missing. keys: ['logging.p2' from custom mutable[p2]]""";
		assertEquals(expected, actual);
	}

	@Test
	void testValidatorAddIfErrorIgnoresMissingButKeepsError() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build()
			.put("p1", "notanumber");
		var b = LogProperty.builder().withPrefix(LogProperties.ROOT_PREFIX);
		Result<Integer> missing = b.ofInt().build("missingKey").get(properties);
		Result<Integer> error = b.ofInt().build("p1").get(properties);
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
	void testPropertySupportValue() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build()
			.put("p1", "v1");
		Property<String> property = Property.builder().withPrefix(LogProperties.ROOT_PREFIX).build("p1");
		PropertySupport support = () -> properties;
		assertEquals("v1", support.value(property));
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
	void testMissingValueWithFallbackSupplier() {
		Missing<String> missing = new Missing<>(List.of("key"), "Property missing. keys: [key]");
		assertEquals("fallback", missing.value(() -> "fallback"));
		assertThrows(PropertyMissingException.class, () -> missing.value(() -> null));
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
		assertThrows(PropertyConvertException.class, () -> error.value(() -> "fallback"));
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
	void testResultValueWithFallbackObject() {
		Result<String> missing = new Missing<>(List.of("key"), "missing");
		assertEquals("fallback", missing.value("fallback"));
		assertThrows(PropertyMissingException.class, () -> missing.value((String) null));
	}

	@Test
	void testResultOptional() {
		Result<String> success = new ValueSuccess<>("key", "actual");
		assertEquals(Optional.of("actual"), success.optional());
		Result<String> missing = new Missing<>(List.of("key"), "missing");
		assertEquals(Optional.empty(), missing.optional());
	}

	@Test
	void testPropertyRequire() {
		Property<String> property = Property.builder().build("logging.p1");
		assertEquals("x", property.require("x"));
		assertThrows(PropertyMissingException.class, () -> property.require(null));
	}

	@Test
	void testPropertyValueOverride() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build()
			.put("p1", "actual");
		Property<String> property = Property.builder().withPrefix(LogProperties.ROOT_PREFIX).build("p1");
		var bound = property.bind(properties);
		assertEquals("replacement", bound.override("replacement"));
		assertEquals("actual", bound.override(null));
	}

	@Test
	void testDefaultPropertyValueOrAndMapAndToString() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build();
		Property<String> property = Property.builder().withPrefix(LogProperties.ROOT_PREFIX).build("missing");
		var bound = property.bind(properties);
		assertTrue(bound.toString().startsWith("PropertyValue.memoize("));
		assertEquals("fallback", bound.or("fallback").value());
		assertEquals("fallback2", bound.or(() -> "fallback2").value());
		assertThrows(PropertyConvertException.class, () -> bound.or("notanumber").map(Integer::parseInt).value());
	}

	@Test
	void testRootPropertyGetterWithSearchAndWithPrefix() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.a.b.c", "deep");
		var search = LogProperty.builder().withSearch("");
		Property<String> property = search.build("logging.a.b.c.d.e");
		assertEquals("deep", property.get(properties).value());

		var prefixed = LogProperty.builder().withPrefix("logging.a.b");
		Property<String> direct = prefixed.build("c");
		assertEquals("deep", direct.get(properties).value());
	}

	@Test
	void testOfProviderRef() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.out", "console:///?stuff=1");
		var getter = LogProperty.builder().ofProviderRef();
		var ref = getter.build("logging.out").get(properties).value();
		assertEquals(URI.create("console:///?stuff=1"), ref.uri());
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testChildPropertyGetterDefaultPropertyStringBranches() {
		var root = LogProperty.builder();

		var stringGetter = root.<String>map(s -> s);
		assertEquals("hello", stringGetter.propertyString("hello"));

		var boolGetter = root.map(Boolean::parseBoolean);
		assertEquals("true", boolGetter.propertyString(true));

		var intGetter = root.map(Integer::parseInt);
		assertEquals("5", intGetter.propertyString(5));

		var uriGetter = root.map(URI::new);
		assertEquals("http://x", uriGetter.propertyString(URI.create("http://x")));

		var mapGetter = root.map(s -> Map.of("a", "b"));
		assertEquals("a=b", mapGetter.propertyString(Map.of("a", "b")));

		var listGetter = root.map(s -> List.of("a", "b"));
		assertEquals("a,b", listGetter.propertyString(List.of("a", "b")));

		var objectGetter = root.map(s -> new Object());
		assertThrows(RuntimeException.class, () -> objectGetter.propertyString(new Object()));
		assertThrows(NullPointerException.class, () -> objectGetter.propertyString(null));
	}

	@Test
	void testFallbackGetterFallbackReturningNullProducesError() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		var getter = LogProperty.builder().orElseGet(() -> null);
		Result<String> result = getter.build("logging.missing").get(properties);
		assertTrue(result instanceof Error<?>);
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testOrElseNullFallbackThrows() {
		var getter = LogProperty.builder();
		assertThrows(NullPointerException.class, () -> getter.orElse(null));
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testResultFuncGetterNullMapperResultBecomesMissing() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "value");
		var getter = LogProperty.builder().<String>map(s -> null);
		Result<String> result = getter.build("logging.p1").get(properties);
		assertFalse(result instanceof Success<?>);
	}

	@Test
	void testPropertyKeyBuilderProviderInterpolatesName() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.output.myname.value", "hello");
		var provider = LogProperty.builder().withKey("logging.output.{name}.value").provider(v -> (n, c) -> v);
		var config = LogConfig.builder().properties(properties).build();
		String actual = provider.provide("myname", config);
		assertEquals("hello", actual);
	}

	@Test
	void testFallbackGetterPassesThroughSuccessAndError() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "5");
		var intGetter = LogProperty.builder().ofInt();
		Result<Integer> success = intGetter.orElse(99).build("logging.p1").get(properties);
		assertEquals(5, success.value());

		var bad = LogProperties.MutableLogProperties.builder().build().put("logging.p2", "notanumber");
		Result<Integer> error = intGetter.orElse(99).build("logging.p2").get(bad);
		assertThrows(PropertyConvertException.class, error::value);
	}

	@Test
	void testFallbackGetterMissingUsesFallbackValueAndDelegatesPropertyString() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		var getter = LogProperty.builder().orElse("fallback");
		assertEquals("fallback", getter.build("logging.missing").get(properties).value());
		assertEquals("fallback", getter.propertyString("fallback"));
	}

	@Test
	void testMapGetterPropertyStringHandlesMultipleEntriesAndNullValues() {
		var map = new LinkedHashMap<String, String>();
		map.put("a", "1");
		map.put("b", null);
		map.put("c", "3");
		assertEquals("a=1&b&c=3", MapGetter._propertyString(map));
	}

	@Test
	void testMapGetterInstancePropertyStringDelegatesToStatic() {
		var getter = LogProperty.builder().ofMap();
		assertEquals("a=1", getter.propertyString(Map.of("a", "1")));
	}

	@Test
	void testListGetterInstancePropertyStringDelegatesToStatic() {
		var getter = LogProperty.builder().ofList();
		assertEquals("a,b", getter.propertyString(List.of("a", "b")));
	}

	@Test
	void testResultFuncGetterPassesThroughParentError() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "notanumber");
		var doubleMapped = LogProperty.builder().ofInt().map(i -> i * 2);
		Result<Integer> result = doubleMapped.build("logging.p1").get(properties);
		assertThrows(PropertyConvertException.class, result::value);
	}

	@Test
	void testResultGetReturnsItself() {
		Result<String> success = new ValueSuccess<>("key", "value");
		assertSame(success, success.get());
	}

	@Test
	void testValidateKeyRejectsTrailingDot() {
		assertThrows(IllegalArgumentException.class, () -> Property.builder().build("logging."));
	}

	@Test
	void testEmptyStandardPropertiesHasNegativeOrder() {
		assertEquals(-1, LogProperties.StandardProperties.EMPTY.order());
	}

	@Test
	void testSearchPropertyGetterWithPrefixCreatesANewSearchGetterWithThatPrefix() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.other.a.b", "deep");
		var search = LogProperty.builder().withSearch("");
		var reprefixed = search.withPrefix("");
		assertEquals("deep", reprefixed.build("logging.other.a.b.c.d").get(properties).value());
	}

	@Test
	void testConvertSuccessPreservesValueSuccessVariant() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		var getter = LogProperty.builder().orElse("5").map(Integer::parseInt);
		Result<Integer> result = getter.build("logging.missing").get(properties);
		assertEquals(5, result.value());
		assertTrue(result instanceof Success.ValueSuccess<?>);
	}

	@Test
	void testOfListMissingProducesMissingResult() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		Result<List<String>> result = LogProperty.builder().ofList().build("logging.missing").get(properties);
		assertTrue(result instanceof Missing<?>);
	}

	@Test
	void testOfMapMissingProducesMissingResult() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		Result<Map<String, String>> result = LogProperty.builder().ofMap().build("logging.missing").get(properties);
		assertTrue(result instanceof Missing<?>);
	}

	@Test
	void testMapperThrowingOnFallbackValueSuccessUsesTwoArgErrorResult() {
		LogProperties properties = LogProperties.MutableLogProperties.builder().build();
		var getter = LogProperty.builder().orElse("bad").<Integer>map(s -> {
			throw new NumberFormatException("nope");
		});
		Result<Integer> result = getter.build("logging.missing").get(properties);
		assertThrows(PropertyConvertException.class, result::value);
	}

	@Test
	void testMapperThrowingPropertyConvertExceptionOnFoundValueUsesSpecialMessage() {
		var properties = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "raw");
		var getter = LogProperty.builder().<String>map(s -> {
			throw new PropertyConvertException("logging.p1", "custom failure", null);
		});
		Result<String> result = getter.build("logging.p1").get(properties);
		var e = assertThrows(PropertyConvertException.class, result::value);
		assertTrue(Objects.requireNonNull(e.getMessage()).contains("Error converting property"));
	}

	@Test
	void testDefaultPropertyPropertyStringDelegatesToGetter() {
		var property = Property.builder().build("logging.p1");
		assertEquals("hello", property.propertyString("hello"));
	}

	@Test
	@SuppressWarnings({ "null", "nullness", "NullAway" })
	void testDefaultPropertySetOnlyCallsConsumerWhenValueNonNull() {
		var property = Property.builder().build("logging.p1");
		Map<String, String> captured = new LinkedHashMap<>();
		property.set("value", captured::put);
		assertEquals(Map.of("logging.p1", "value"), captured);

		captured.clear();
		property.set(null, captured::put);
		assertTrue(captured.isEmpty());
	}

	@Test
	void testDefaultPropertyRejectsEmptyKeys() {
		var getter = LogProperty.builder();
		assertThrows(IllegalArgumentException.class, () -> new DefaultProperty<>(getter, List.of()));
	}

}
