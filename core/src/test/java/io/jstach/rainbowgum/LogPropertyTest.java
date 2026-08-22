package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.ValidationException;

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

}
