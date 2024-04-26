package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;

class LogPropertiesTest {

	@Test
	void testStaticPropertiesDescription() {
		var properties = LogProperties.of(List.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES,
				LogProperties.StandardProperties.ENVIRONMENT_VARIABLES));
		try {
			Property.builder().build("logging.some.ignoreMe").get(properties).value();
			fail("expected exception");
		}
		catch (PropertyMissingException e) {
			String expected = """
					Property missing. keys: ['logging.some.ignoreMe' from SYSTEM_PROPERTIES[logging.some.ignoreMe], ENVIRONMENT_VARIABLES[logging_some_ignoreMe]]""";
			assertEquals(expected, e.getMessage());
		}
	}

	@Test
	void testLogPropertiesBuilderMissingDescription() {
		var props = LogProperties.builder()
			.fromURIQuery(URI.create("stuff:///?blah=hello"))
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.build();
		try {
			Property.builder().build("logging.some.ignoreMe").get(props).value();
			fail("expected exception");
		}
		catch (PropertyMissingException e) {
			assertEquals(
					"Property missing. keys: ['logging.some.ignoreMe' from URI_QUERY(stuff:///?blah=hello)[some.ignoreMe]]",
					e.getMessage());
		}
	}

	@Test
	void testLogPropertiesRenameKey() {
		var props = LogProperties.builder()
			.renameKey(k -> LogProperties.removeKeyPrefix(k, LogProperties.ROOT_PREFIX))
			.fromURIQuery(URI.create("stuff:///?blah=hello"))
			.build();
		String actual = Property.builder().build("logging.blah").get(props).value();
		assertEquals("hello", actual);

	}

	@ParameterizedTest
	@EnumSource(PropsTest.class)
	void testParseProperties(PropsTest test) {
		String input = test.input;
		var props = LogProperties.builder().fromProperties(input).build();
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		for (var k : test.expected.keySet()) {
			var v = props.valueOrNull(k);
			if (v == null) {
				throw new AssertionError();
			}
			values.put(k, v);
		}
		assertEquals(test.expected, values);
	}

	@ParameterizedTest
	@EnumSource(PropsTest.class)
	void testWriteProperties(PropsTest test) {
		String input = test.input;
		var props = LogProperties.builder().fromProperties(input).build();
		LinkedHashMap<String, String> values = new LinkedHashMap<>();
		for (var k : test.expected.keySet()) {
			var v = props.valueOrNull(k);
			if (v == null) {
				throw new AssertionError();
			}
			values.put(k, v);
		}
		String actual = PropertiesParser.writeProperties(values);
		String expected = test.input; // properties always have a new line on the end.
		assertEquals(expected, actual);
	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum PropsTest {

		SINGLE_EMPTY("""
				a=
				""", "a", ""), SINGLE_VALUE("""
				a=v1
				""", "a", "v1"), TWO_EMPTY("""
				a=
				b=
				""", "a", "", "b", ""), TWO_VALUE("""
				a=v1
				b=v2
				""", "a", "v1", "b", "v2"),

		;

		private final String input;

		private final SequencedMap<String, String> expected;

		private PropsTest(String input, String... kvs) {
			this.input = input;
			this.expected = createLinkedHashMap(kvs);
		}

	}

	private static SequencedMap<String, String> createLinkedHashMap(String[] array) {
		if (array.length % 2 != 0) {
			throw new IllegalArgumentException("Array must have an even number of elements");
		}
		LinkedHashMap<String, String> map = new LinkedHashMap<>();
		for (int i = 0; i < array.length; i += 2) {
			map.put(array[i], array[i + 1]);
		}

		return map;
	}

	@ParameterizedTest
	@EnumSource(ParseListTest.class)
	void testListOrNull(ParseListTest test) {
		String propertiesString = """
				list=%s
				""".formatted(test.input);
		var props = LogProperties.builder().fromProperties(propertiesString).build();
		var actual = props.listOrNull("list");
		var expected = test.output;
		assertEquals(expected, actual);
	}

	@ParameterizedTest
	@EnumSource(ParseListTest.class)
	void testParseList(ParseListTest test) {
		var actual = LogProperties.parseList(test.input);
		var expected = test.output;
		assertEquals(expected, actual);
	}

	@ParameterizedTest
	@EnumSource(ParseListTest.class)
	void testEncode(ParseListTest test) {

		var encoded = test.output.stream()
			.map(k -> PercentCodec.encode(k, StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		var actual = LogProperties.parseList(encoded);
		assertEquals(test.output, actual);
	}

	@ParameterizedTest
	@EnumSource(ParseListTest.class)
	void testParseMultiMapEmptyList(ParseListTest test) {
		var actual = LogProperties.parseMultiMap(test.input).keySet().stream().toList();
		var expected = test.output.stream().distinct().toList();
		assertEquals(expected, actual);
	}

	@Test
	void testMutableLogProperties() {
		ConcurrentHashMap<String, String> m = new ConcurrentHashMap<>();
		var badProps = MutableLogProperties.builder()
			.description("hello")
			.order(2)
			.with(m)
			.build()
			.put("greet", "hello");
		assertThrows(IllegalArgumentException.class, () -> {
			/*
			 * Bad because we do not have the logging prefix
			 */
			LogProperty.builder().build("greet").get(badProps).value();
		});
		m.clear();
		var props = MutableLogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX)
			.with(LogProperties.StandardProperties.SYSTEM_PROPERTIES)
			.copyProperties("")
			.order(2)
			.with(m)
			.build()
			.put("greet", "hello");

		String actual = LogProperty.builder().build("logging.greet").get(props).value();
		String expected = "hello";
		assertEquals(expected, actual);
	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum ParseListTest {

		SINGLE("a", "a"), //
		TWO_COMMA("a,b", "a", "b"), //
		THREE_COMMA("a,b,c", "a", "b", "c"), //
		TWO_AMP("a&b", "a", "b"), //
		THREE_AMP("a&b&c", "a", "b", "c"), //
		MIXED("a&b,c", "a", "b", "c"), //
		TRAILING_COMMA("a,", "a"), //
		TRAILING_AMP("a&", "a"), //
		STARTING_COMMA(",a", "a"), //
		STARTING_AMP("&a", "a"), //
		STARTING_DOUBLE_COMMA(",,a", "a"), // TODO this is probably bad
		STARTING_DOUBLE_AMP("&&a", "a"), // TODO this is probably bad
		TRAILING_DOUBLE_COMMA("a,,", "a"), // TODO this is probably bad
		TRAILING_DOUBLE_AMP("a&&", "a"), // TODO this is probably bad
		EQUAL_INGORED("a=&b=&c=", "a", "b", "c"), PERCENT_ESCAPING("a%20,b%20", "a ", "b "),
		CHINESE_UNICODE("%E7%94%B0%E9%97%BB,%E7%94%B0%E9%97%BB", "\u7530\u95fb", "\u7530\u95fb");

		private final String input;

		private final List<String> output;

		private ParseListTest(String input, String... output) {
			this.input = input;
			this.output = Stream.of(output).toList();
		}

	}

	@ParameterizedTest
	@EnumSource(ParseMultiTest.class)
	void testParseMultiMap(ParseMultiTest test) {
		var actual = LogProperties.parseMultiMap(test.input);
		var expected = test.expected;
		assertEquals(expected, actual);
	}

	@Test
	void testMapOrNullUri() {
		URI uri = URI.create("stuff:///?" + "a.a1=v1,a.a2=v2");
		var props = LogProperties.builder()
			.removeKeyPrefix(LogProperties.ROOT_PREFIX) //
			.fromURIQuery(uri)
			.build();
		Map<String, String> actual = props.mapOrNull("a");
		Map<String, String> expected = Map.of("a1", "v1", "a2", "v2");
		assertEquals(expected, actual);
		actual = LogProperty.builder()
			.withPrefix(LogProperties.ROOT_PREFIX) //
			.toMap()
			.build("a")
			.get(props)
			.value();
		assertEquals(expected, actual);
	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum ParseMultiTest {

		SIMPLE("a=v1&a=v2", List.of("v1", "v2")),;

		private final String input;

		private final Map<String, List<String>> expected;

		private ParseMultiTest(String input, List<String> values) {
			this.input = input;
			this.expected = Map.of("a", values);
		}

	}

}
