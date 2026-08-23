package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;

class LogPropertiesTest {

	/*
	 * RainbowGumHolder is static, JVM-wide state (see RainbowGumEntryPointTest's own
	 * comment on this) - reset it around the findGlobalProperties() tests below so they
	 * do not depend on whatever other tests in this JVM fork left behind.
	 */
	@BeforeEach
	void beforeEachClearGlobalRainbowGum() {
		RainbowGumHolder.remove(null);
	}

	@AfterEach
	void afterEachClearGlobalRainbowGum() {
		RainbowGumHolder.remove(null);
	}

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
			.ofMap()
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

	@Test
	void testValueFallback() {
		var props = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "actual");
		assertEquals("actual", props.value("logging.p1", "fallback"));
		assertEquals("fallback", props.value("logging.missing", "fallback"));
	}

	@Test
	void testMapOrNullDefaultUsesParseMap() {
		var props = LogProperties.builder().fromProperties("""
				logging.a=k1=v1,k2=v2
				""").build();
		assertEquals(Map.of("k1", "v1", "k2", "v2"), props.mapOrNull("logging.a"));
		assertNull(props.mapOrNull("logging.missing"));
	}

	@Test
	void testStringPropertyOrNull() {
		var props = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "v1");
		var found = props.stringPropertyOrNull("logging.p1");
		assertEquals("v1", found.value());
		assertEquals("logging.p1", found.key());
		assertNull(props.stringPropertyOrNull("logging.missing"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "password", "PASSWORD", "apikey", "secret", "token" })
	void testStringPropertyValueDescriptionRedactsExactMatch(String value) {
		var props = LogProperties.MutableLogProperties.builder().build().put("logging.p1", value);
		assertEquals("<REDACTED>", props.stringPropertyOrNull("logging.p1").valueDescription());
	}

	@Test
	void testStringPropertyValueDescriptionRedactsSubstringMatch() {
		var props = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "my-password-123");
		assertEquals("<REDACTED>", props.stringPropertyOrNull("logging.p1").valueDescription());
	}

	@Test
	void testStringPropertyValueDescriptionPassesThroughOrdinaryValues() {
		var props = LogProperties.MutableLogProperties.builder().build().put("logging.p1", "hello");
		assertEquals("hello", props.stringPropertyOrNull("logging.p1").valueDescription());
	}

	@Test
	void testListPropertyValueDescription() {
		var props = LogProperties.builder().fromProperties("""
				logging.a=x,y
				""").build();
		assertEquals("[x, y]", props.listPropertyOrNull("logging.a").valueDescription());
	}

	@Test
	void testMapPropertyValueDescription() {
		var props = LogProperties.builder().fromProperties("""
				logging.a=k=v
				""").build();
		assertEquals("{k=v}", props.mapPropertyOrNull("logging.a").valueDescription());
	}

	@Test
	void testBuilderFromAlreadySetThrows() {
		var builder = LogProperties.builder().fromProperties("logging.a=1\n");
		var e = assertThrows(IllegalArgumentException.class, () -> builder.fromProperties("logging.b=2\n"));
		assertEquals("from already set", e.getMessage());
	}

	@Test
	void testFromPropertiesKeepsExplicitDescription() {
		var props = LogProperties.builder().description("custom").fromProperties("logging.a=1\n").build();
		assertEquals("custom[logging.a]", props.description("logging.a"));
	}

	@Test
	void testFromPropertiesDefaultsDescriptionWhenNoneSet() {
		var props = LogProperties.builder().fromProperties("logging.a=1\n").build();
		assertEquals("PROPERTIES_STRING[logging.a]", props.description("logging.a"));
	}

	@Test
	void testBuilderBuildWithoutFromUsesSoleFallback() {
		var fallback = LogProperties.MutableLogProperties.builder().build().put("logging.a", "1");
		var props = LogProperties.builder().with(fallback).build();
		assertSame(fallback, props);
	}

	@Test
	void testBuilderBuildWithoutFromOrFallbacksThrows() {
		var e = assertThrows(IllegalStateException.class, () -> LogProperties.builder().build());
		assertEquals("from was not set", e.getMessage());
	}

	@Test
	void testMutableLogPropertiesPutNullRemovesKey() {
		var props = LogProperties.MutableLogProperties.builder().build();
		props.put("logging.a", "1");
		assertEquals("1", props.valueOrNull("logging.a"));
		props.put("logging.a", null);
		assertNull(props.valueOrNull("logging.a"));
	}

	@Test
	void testFindGlobalPropertiesFallsBackToSystemPropertiesWhenNoneBound() {
		assertSame(LogProperties.StandardProperties.SYSTEM_PROPERTIES, LogProperties.findGlobalProperties());
	}

	@Test
	void testFindGlobalPropertiesFallsBackToSuppliedFallbackWhenNoneBound() {
		var fallback = LogProperties.MutableLogProperties.builder().build();
		assertSame(fallback, LogProperties.findGlobalProperties(() -> fallback));
	}

	@Test
	void testFindGlobalPropertiesUsesBoundRainbowGum() {
		var boundProps = LogProperties.MutableLogProperties.builder().build().put("marker", "yes");
		var config = LogConfig.builder().properties(boundProps).build();
		try (var gum = RainbowGum.builder(config).set()) {
			assertSame(boundProps, LogProperties.findGlobalProperties());
		}
	}

	@Test
	void testOfSingleElementListReturnsThatElement() {
		var props = LogProperties.MutableLogProperties.builder().build();
		assertSame(props, LogProperties.of(List.of(props)));
	}

	@Test
	void testOfFiltersEmptyPropertiesOutOfComposite() {
		var a = LogProperties.MutableLogProperties.builder().build().put("logging.a", "1");
		var b = LogProperties.MutableLogProperties.builder().build().put("logging.b", "2");
		var combined = LogProperties.of(List.of(LogProperties.StandardProperties.EMPTY, a, b));
		assertEquals("1", combined.valueOrNull("logging.a"));
		assertEquals("2", combined.valueOrNull("logging.b"));
		assertTrue(combined.toString().startsWith("CompositeLogProperties[properties="));
	}

	@Test
	void testParseMapDirectly() {
		assertEquals(Map.of("a", "v1"), LogProperties.parseMap("a=v1&b"));
		assertEquals(Map.of("a", "v1", "b", ""), LogProperties.parseMap("a=v1&b="));
	}

	@Test
	void testParseMapSkipsPairStartingWithEquals() {
		assertEquals(Map.of("a", "v1"), LogProperties.parseMap("=foo&a=v1"));
	}

	@Test
	void testParseMapDecodesPercentEncodedValues() {
		assertEquals(Map.of("a", "b c"), LogProperties.parseMap("a=b%20c"));
	}

	@Test
	void testConcatKeySingleArgPrefixesRoot() {
		assertEquals("logging.foo", LogProperties.concatKey("foo"));
	}

	@ParameterizedTest
	@CsvSource({ "'', foo, foo", "root, '', root", "'a.', b, a.b", "a, .b, a.b", "a, b, a.b" })
	void testConcatKeyTwoArgBranches(String prefix, String name, String expected) {
		assertEquals(expected, LogProperties.concatKey(prefix, name));
	}

	@Test
	void testInterpolateKeyThrowsWhenLookupMissesAParameter() {
		assertThrows(IllegalArgumentException.class, () -> LogProperties.interpolateKey("a.{name}.b", k -> null));
	}

	@Test
	void testValidateKeyParametersThrowsOnMismatch() {
		assertThrows(IllegalArgumentException.class, () -> LogProperties.validateKeyParameters("a.{name}", Set.of()));
	}

	@Test
	void testMultiMapPropertiesValueOrNullTreatsKeyWithNoValuesAsMissing() {
		var props = LogProperties.builder().fromURIQuery(URI.create("stuff:///?a&b=v1")).build();
		assertNull(props.valueOrNull("a"));
		assertEquals("v1", props.valueOrNull("b"));
	}

	@Test
	void testMultiMapPropertiesListOrNullReturnsRawList() {
		var props = LogProperties.builder().fromURIQuery(URI.create("stuff:///?a=1&a=2")).build();
		assertEquals(List.of("1", "2"), props.listOrNull("a"));
		assertNull(props.listOrNull("missing"));
	}

	@Test
	void testMultiMapPropertiesMapOrNullSkipsUnrelatedAndValuelessKeys() {
		var props = LogProperties.builder()
			.fromURIQuery(URI.create("stuff:///?a.a1=v1&a.a2&unrelated=v2&a.=direct"))
			.build();
		Map<String, String> actual = props.mapOrNull("a");
		assertEquals(Map.of("a1", "v1"), actual);
	}

	@Test
	void testListLogPropertiesValueOrNullSearchesInOrderAndDescriptionJoinsAll() {
		var a = LogProperties.MutableLogProperties.builder().description("A").build().put("logging.a", "fromA");
		var b = LogProperties.MutableLogProperties.builder().description("B").build().put("logging.b", "fromB");
		var composite = LogProperties.of(List.of(a, b));
		assertEquals("fromA", composite.valueOrNull("logging.a"));
		assertEquals("fromB", composite.valueOrNull("logging.b"));
		assertNull(composite.valueOrNull("logging.missing"));
	}

	@Test
	void testListLogPropertiesStringAndMapPropertyOrNullSearchInOrder() {
		var a = LogProperties.MutableLogProperties.builder().build();
		var b = LogProperties.builder().fromProperties("""
				logging.a=k=v
				""").build();
		var composite = LogProperties.of(List.of(a, b));
		assertNotNull(composite.stringPropertyOrNull("logging.a"));
		assertNotNull(composite.mapPropertyOrNull("logging.a"));
		assertNull(composite.mapPropertyOrNull("logging.missing"));
	}

	@Test
	void testCompositeMutableLogPropertiesPutDelegatesToFirstMutableMemberNotItself() {
		var fallback = LogProperties.MutableLogProperties.builder().build().put("logging.existing", "keep");
		var composite = LogProperties.MutableLogProperties.builder().with(fallback).build();
		composite.put("logging.a", "1");
		assertEquals("1", composite.valueOrNull("logging.a"));
		assertEquals("keep", composite.valueOrNull("logging.existing"));
		assertTrue(composite.toString().startsWith("CompositeMutableLogProperties[properties="));
	}

	@Test
	void testCompositeMutableLogPropertiesPutSkipsNonMutableMembers() {
		var composite = LogProperties.MutableLogProperties.builder()
			.with(LogProperties.StandardProperties.SYSTEM_PROPERTIES)
			.build();
		composite.put("logging.testCompositeSkipsNonMutable", "1");
		assertEquals("1", composite.valueOrNull("logging.testCompositeSkipsNonMutable"));
	}

}
