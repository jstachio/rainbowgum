package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.simple.props.SimpleLogProperties.PropertyEntry;

class SimpleLogPropertiesTest {

	private static PropertyEntry entryOf(Map<String, PropertyEntry> entries, String key) {
		var e = entries.get(key);
		assertNotNull(e, () -> "expected an entry for key: " + key);
		return e;
	}

	@Test
	void testKeyOnFirstLineIsLineOne() {
		var entries = SimpleLogProperties.readProperties("""
				logging.level=DEBUG
				""");
		assertEquals(1, entryOf(entries, "logging.level").line());
	}

	/*
	 * Comments and blank lines must not throw off the count: each key's line number is
	 * its own physical line in the file, not its ordinal position among real entries.
	 */
	@Test
	void testEachKeyGetsItsOwnLineNumberAcrossCommentsAndBlankLines() {
		var entries = SimpleLogProperties.readProperties("""
				logging.first=1

				# a comment
				logging.second=2
				# another comment
				logging.third=3
				""");
		assertEquals(1, entryOf(entries, "logging.first").line());
		assertEquals(4, entryOf(entries, "logging.second").line());
		assertEquals(6, entryOf(entries, "logging.third").line());
	}

	/*
	 * Standard java.util.Properties semantics: a key repeated later in the file overrides
	 * the earlier one. The line number should follow the value that actually wins, not
	 * the first occurrence.
	 */
	@Test
	void testDuplicateKeyLastOccurrenceWins() {
		var entries = SimpleLogProperties.readProperties("""
				logging.level=DEBUG
				logging.level=INFO
				""");
		var e = entryOf(entries, "logging.level");
		assertEquals("INFO", e.value());
		assertEquals(2, e.line());
	}

	@Test
	void testDescriptionIncludesLineNumberForAPresentKey() throws Exception {
		var props = SimpleLogProperties.read(new StringReader("""
				logging.first=1
				logging.second=2
				"""), "classpath:/example.properties");
		assertEquals("SIMPLE_PROPS[classpath:/example.properties:2][logging.second]",
				props.description("logging.second"));
	}

	/*
	 * A key that was never in the file has no line to report, so the description falls
	 * back to the no-index form instead of a made-up or stale line number.
	 */
	@Test
	void testDescriptionHasNoLineNumberForAMissingKey() throws Exception {
		var props = SimpleLogProperties.read(new StringReader("""
				logging.first=1
				"""), "classpath:/example.properties");
		assertEquals("SIMPLE_PROPS[classpath:/example.properties][logging.missing]",
				props.description("logging.missing"));
	}

	@Test
	void testValueOrNull() throws Exception {
		var props = SimpleLogProperties.read(new StringReader("""
				logging.first=1
				"""), "classpath:/example.properties");
		assertEquals("1", props.valueOrNull("logging.first"));
		assertNull(props.valueOrNull("logging.missing"));
	}

	/*
	 * Pinned so a future refactor doesn't silently drop this override, the way the
	 * original LogProperties.builder().order(100) call was dropped when this class
	 * replaced it. order() must stay below SYSTEM_PROPERTIES (400) and
	 * ENVIRONMENT_VARIABLES (300), or the classpath file would start winning over both
	 * when coalesced (see SimpleProperties's resolution order).
	 */
	@Test
	void testOrderIsLowerThanSystemPropertiesAndEnvironmentVariables() throws Exception {
		var props = SimpleLogProperties.read(new StringReader("logging.a=1"), "classpath:/example.properties");
		assertEquals(100, props.order());
		assertTrue(props.order() < LogProperties.StandardProperties.ENVIRONMENT_VARIABLES.order());
		assertTrue(props.order() < LogProperties.StandardProperties.SYSTEM_PROPERTIES.order());
	}

}
