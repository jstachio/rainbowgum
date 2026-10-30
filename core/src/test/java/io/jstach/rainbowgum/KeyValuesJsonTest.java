package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.KeyValues.JsonSerializer;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogConfig.Globals.KeyValuesJson;

class KeyValuesJsonTest {

	private static MutableKeyValues withUser() {
		var kvs = MutableKeyValues.of();
		kvs.putKeyValue("requestId", "42");
		kvs.putKeyValue("user", "{id=1}", "{\"id\":1}");
		return kvs;
	}

	private static String entries(KeyValues kvs) {
		var sb = new StringBuilder();
		kvs.forEach((k, v) -> sb.append(k).append('=').append(v).append(';'));
		return sb.toString();
	}

	@Test
	void jsonFormsAreNotKeyValues() {
		var kvs = withUser();
		assertEquals(2, kvs.size());
		assertEquals("requestId=42;user={id=1};", entries(kvs));
		assertEquals("{id=1}", kvs.getValueOrNull("user"));
		assertEquals("{\"id\":1}", kvs.getJsonOrNull("user"));
		assertNull(kvs.getJsonOrNull("requestId"));
		assertEquals("{\"requestId\":\"42\", \"user\":\"{id=1}\"}", kvs.toString());
		assertEquals(Map.of("requestId", "42", "user", "{id=1}"), kvs.copyToMap());
	}

	@Test
	void putWithoutJsonOrRemoveDropsTheJsonForm() {
		var kvs = withUser();
		kvs.putKeyValue("user", "ada");
		assertNull(kvs.getJsonOrNull("user"));
		assertEquals(2, kvs.size());

		kvs = withUser();
		kvs.putKeyValue("user", "{id=2}", null);
		assertNull(kvs.getJsonOrNull("user"));

		kvs = withUser();
		kvs.remove("user");
		assertNull(kvs.getJsonOrNull("user"));
		assertEquals(1, kvs.size());
		assertEquals("requestId=42;", entries(kvs));

		kvs = withUser();
		kvs.putKeyValue("user", "{id=2}", "{\"id\":2}");
		assertEquals("{\"id\":2}", kvs.getJsonOrNull("user"));
		assertEquals(2, kvs.size());
	}

	@Test
	void copyAndFreezeKeepJsonForms() {
		var kvs = withUser();
		assertEquals("{\"id\":1}", kvs.copy().getJsonOrNull("user"));
		var frozen = kvs.freeze();
		assertEquals("{\"id\":1}", frozen.getJsonOrNull("user"));
		assertEquals(2, frozen.size());
		assertEquals(kvs, frozen);
	}

	@Test
	void equalsComparesJsonForms() {
		var plain = MutableKeyValues.of();
		plain.putKeyValue("requestId", "42");
		plain.putKeyValue("user", "{id=1}");
		assertNotEquals(withUser(), plain);
		assertNotEquals(plain, withUser());
		assertEquals(withUser(), withUser());
	}

	@Test
	void mergeKeepsTheJsonFormsOfTheWinningSide() {
		var low = KeyValues.of(Map.of("user", "low", "env", "prod"));
		var merged = KeyValues.merge(low, withUser().freeze());
		assertEquals("{\"id\":1}", merged.getJsonOrNull("user"));
		assertEquals(3, merged.size());

		var high = MutableKeyValues.of();
		high.putKeyValue("user", "plain");
		merged = KeyValues.merge(withUser().freeze(), high);
		assertEquals("plain", merged.getValueOrNull("user"));
		assertNull(merged.getJsonOrNull("user"), "a plain value that wins must not keep the loser's JSON form");
		assertEquals(2, merged.size());

		var other = MutableKeyValues.of();
		other.putKeyValue("env", "prod");
		merged = KeyValues.merge(withUser().freeze(), other);
		assertEquals("{\"id\":1}", merged.getJsonOrNull("user"));
		assertEquals(3, merged.size());
	}

	@Test
	void mergeWithALowSideThatHasGaps() {
		var low = withUser();
		low.putKeyValue("gone", "x");
		low.remove("gone");
		var high = MutableKeyValues.of();
		high.putKeyValue("env", "prod");
		var merged = KeyValues.merge(low, high);
		assertEquals("{\"id\":1}", merged.getJsonOrNull("user"));
		assertEquals("requestId=42;user={id=1};env=prod;", entries(merged));
	}

	// The StringBuilder case is a CharSequence that is not a String.
	@SuppressWarnings("UnnecessaryStringBuilder")
	static Stream<Arguments> serializerCases() {
		var map = new LinkedHashMap<String, @Nullable Object>();
		map.put("id", 1);
		map.put("name", "Ada \"the\" first");
		map.put("tags", List.of("a", "b"));
		map.put("nested", Map.of("ok", true));
		map.put("none", null);
		List<Object> cycle = new ArrayList<>();
		cycle.add(cycle);
		return Stream.of(Arguments.of("int", 3, "3"), Arguments.of("long", 3L, "3"),
				Arguments.of("double", 1.5d, "1.5"), Arguments.of("boolean", true, "true"),
				Arguments.of("big decimal", new BigDecimal("1.10"), "1.10"),
				Arguments.of("map", map,
						"{\"id\":1,\"name\":\"Ada \\\"the\\\" first\",\"tags\":[\"a\",\"b\"],\"nested\":{\"ok\":true},\"none\":null}"),
				Arguments.of("int array", new int[] { 1, 2 }, "[1,2]"),
				Arguments.of("object array", new Object[] { "x", 1, null }, "[\"x\",1,null]"),
				Arguments.of("non finite double in a list", List.of(Double.NaN, Double.POSITIVE_INFINITY),
						"[\"NaN\",\"Infinity\"]"),
				Arguments.of("map key not a string", Map.of(1, "one"), "{\"1\":\"one\"}"),
				Arguments.of("control characters", List.of("a\nb\u0001"), "[\"a\\nb\\u0001\"]"),
				Arguments.of("cycle stops at max depth", cycle,
						"[".repeat(32) + "\"" + "[".repeat(1) + "(this Collection)]\"" + "]".repeat(32)),
				Arguments.of("enum is plain text only", KeyValuesJson.ON, null),
				Arguments.of("char sequence is plain text only", new StringBuilder("sb"), null),
				Arguments.of("path is plain text only", Path.of("a/b"), null),
				Arguments.of("other object is plain text only", new Object() {
					@Override
					public String toString() {
						return "custom";
					}
				}, null));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("serializerCases")
	void builtinSerializer(String name, Object value, @Nullable String expected) {
		assertEquals(expected, JsonSerializer.of().toJson(value));
	}

	@Test
	void serializerIsOffByDefault() {
		var config = LogConfig.builder().build();
		assertEquals(KeyValuesJson.OFF, config.globals().keyValuesJson());
		assertNull(JsonSerializer.of(config));
	}

	@ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "on", "ON", "true", " on " })
	void propertyTurnsOnTheBuiltinSerializer(String value) {
		var properties = LogProperties.builder().fromProperties("logging.keyvalues.json=" + value).build();
		var config = LogConfig.builder().properties(properties).build();
		assertEquals(KeyValuesJson.ON, config.globals().keyValuesJson());
		assertSame(JsonSerializer.of(), java.util.Optional.ofNullable(JsonSerializer.of(config)).orElseThrow());
	}

	@Test
	void registeredSerializerIsUsedWhenOn() {
		JsonSerializer custom = v -> "\"custom\"";
		var config = LogConfig.builder().keyValuesJson(KeyValuesJson.ON).build();
		config.serviceRegistry().put(JsonSerializer.class, custom);
		assertSame(custom, java.util.Optional.ofNullable(JsonSerializer.of(config)).orElseThrow());
	}

	@Test
	void registeredSerializerIsNotUsedWhenOff() {
		var config = LogConfig.builder().build();
		config.serviceRegistry().put(JsonSerializer.class, v -> "\"custom\"");
		assertNull(JsonSerializer.of(config));
	}

	@Test
	void badPropertyValueFailsTheConfig() {
		var properties = LogProperties.builder().fromProperties("logging.keyvalues.json=yes").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder().properties(properties).build());
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogConfig:
				Error for property. key: 'logging.keyvalues.json' from PROPERTIES_STRING[logging.keyvalues.json], \
				'yes' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.KeyValuesJson. \
				Valid values: 'off', 'on', 'true', 'false'""";
		assertEquals(expected, e.getMessage());
	}

}
