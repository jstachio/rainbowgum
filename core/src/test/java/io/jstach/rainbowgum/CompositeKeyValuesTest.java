package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class CompositeKeyValuesTest {

	@Test
	void emptyListIsEmptyKeyValues() throws Exception {
		var kvs = KeyValues.of(List.<KeyValues>of());
		assertSame(KeyValues.of(), kvs);
	}

	@Test
	void singlePartIsReturnedUnwrapped() throws Exception {
		var only = KeyValues.of(Map.of("A", "a"));
		var kvs = KeyValues.of(List.of(only));
		assertSame(only, kvs);
	}

	@Test
	void laterPartWinsOnCollision() throws Exception {
		var low = KeyValues.of(Map.of("env", "low", "onlyLow", "l"));
		var high = KeyValues.of(Map.of("env", "high", "onlyHigh", "h"));
		var kvs = KeyValues.of(List.of(low, high));
		assertEquals("high", kvs.getValueOrNull("env"));
		assertEquals("l", kvs.getValueOrNull("onlyLow"));
		assertEquals("h", kvs.getValueOrNull("onlyHigh"));
		assertNull(kvs.getValueOrNull("missing"));
	}

	@Test
	void iterationYieldsEachKeyExactlyOnceWithWinningValue() throws Exception {
		var low = KeyValues.of(Map.of("env", "low", "onlyLow", "l"));
		var high = KeyValues.of(Map.of("env", "high", "onlyHigh", "h"));
		var kvs = KeyValues.of(List.of(low, high));

		LinkedHashMap<String, @Nullable String> collected = new LinkedHashMap<>();
		kvs.forEach(collected::put);

		assertEquals(3, collected.size());
		assertEquals("high", collected.get("env"));
		assertEquals("l", collected.get("onlyLow"));
		assertEquals("h", collected.get("onlyHigh"));
		assertEquals(3, kvs.size());
	}

	@Test
	void indexProtocolNeverRevisitsShadowedEntry() throws Exception {
		var low = KeyValues.of(Map.of("env", "low"));
		var high = KeyValues.of(Map.of("env", "high"));
		var kvs = KeyValues.of(List.of(low, high));

		int count = 0;
		for (int i = kvs.start(); i > -1; i = kvs.next(i)) {
			assertEquals("env", kvs.key(i));
			assertEquals("high", kvs.valueOrNull(i));
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	void middlePartFullyShadowedIsSkippedDuringIteration() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var shadowed = KeyValues.of(Map.of("a", "2"));
		var high = KeyValues.of(Map.of("a", "3", "b", "4"));
		var kvs = KeyValues.of(List.of(low, shadowed, high));

		LinkedHashMap<String, @Nullable String> collected = new LinkedHashMap<>();
		kvs.forEach(collected::put);
		assertEquals(Map.of("a", "3", "b", "4"), collected);
	}

	@Test
	void emptyPartsAmongNonEmptyPartsAreSkipped() throws Exception {
		var kvs = KeyValues.of(List.of(KeyValues.of(), KeyValues.of(Map.of("a", "1")), KeyValues.of()));
		assertEquals(1, kvs.size());
		assertEquals("1", kvs.getValueOrNull("a"));
	}

	@Test
	void explicitNullValueInLaterPartWinsOverEarlierNonNull() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var highWithNull = MutableKeyValues.of();
		highWithNull.putKeyValue("a", null);
		var kvs = KeyValues.of(List.of(low, highWithNull.freeze()));
		assertNull(kvs.getValueOrNull("a"));
		assertEquals(1, kvs.size());
	}

	@Test
	void toStringMatchesMergedContent() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var kvs = KeyValues.of(List.of(low, high));
		assertEquals("{\"A\":\"a\", \"B\":\"b\"}", kvs.toString());
	}

	@Test
	void equalsMatchesEquivalentNonCompositeKeyValues() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var composite = KeyValues.of(List.of(low, high));
		var flat = KeyValues.of(Map.of("A", "a", "B", "b"));
		assertEquals(composite, flat);
		assertEquals(flat, composite);
		assertEquals(composite.hashCode(), flat.hashCode());
	}

	@Test
	void copyToMapReflectsPrecedence() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var high = KeyValues.of(Map.of("a", "2"));
		var kvs = KeyValues.of(List.of(low, high));
		assertEquals(Map.of("a", "2"), kvs.copyToMap());
	}

	@Test
	void isLazyAndReflectsMutationOfUnderlyingMutablePart() throws Exception {
		var mutable = MutableKeyValues.of();
		var kvs = KeyValues.of(List.of(KeyValues.of(Map.of("a", "1")), mutable));
		assertEquals("1", kvs.getValueOrNull("a"));

		mutable.putKeyValue("a", "2");
		assertEquals("2", kvs.getValueOrNull("a"), "composite is a live view, not a snapshot");
	}

}
