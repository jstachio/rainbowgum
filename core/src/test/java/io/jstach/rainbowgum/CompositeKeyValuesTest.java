package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class CompositeKeyValuesTest {

	@Test
	void emptyLowReturnsHighUnwrapped() throws Exception {
		var high = KeyValues.of(Map.of("A", "a"));
		var kvs = KeyValues.merge(KeyValues.of(), high);
		assertSame(high, kvs);
	}

	@Test
	void emptyHighReturnsLowUnwrapped() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var kvs = KeyValues.merge(low, KeyValues.of());
		assertSame(low, kvs);
	}

	@Test
	void bothEmptyReturnsEmpty() throws Exception {
		var kvs = KeyValues.merge(KeyValues.of(), KeyValues.of());
		assertSame(KeyValues.of(), kvs);
	}

	@Test
	void highWinsOnCollision() throws Exception {
		var low = KeyValues.of(Map.of("env", "low", "onlyLow", "l"));
		var high = KeyValues.of(Map.of("env", "high", "onlyHigh", "h"));
		var kvs = KeyValues.merge(low, high);
		assertEquals("high", kvs.getValueOrNull("env"));
		assertEquals("l", kvs.getValueOrNull("onlyLow"));
		assertEquals("h", kvs.getValueOrNull("onlyHigh"));
		assertNull(kvs.getValueOrNull("missing"));
	}

	@Test
	void iterationYieldsEachKeyExactlyOnceWithWinningValue() throws Exception {
		var low = KeyValues.of(Map.of("env", "low", "onlyLow", "l"));
		var high = KeyValues.of(Map.of("env", "high", "onlyHigh", "h"));
		var kvs = KeyValues.merge(low, high);

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
		var kvs = KeyValues.merge(low, high);

		int count = 0;
		for (int i = kvs.start(); i > -1; i = kvs.next(i)) {
			assertEquals("env", kvs.key(i));
			assertEquals("high", kvs.valueOrNull(i));
			count++;
		}
		assertEquals(1, count);
	}

	@Test
	void nestedMergeThreeLevelsDeepLaterAlwaysWins() throws Exception {
		var a = KeyValues.of(Map.of("k", "a", "onlyA", "1"));
		var b = KeyValues.of(Map.of("k", "b"));
		var c = KeyValues.of(Map.of("k", "c", "onlyC", "3"));
		var kvs = KeyValues.merge(KeyValues.merge(a, b), c);

		LinkedHashMap<String, @Nullable String> collected = new LinkedHashMap<>();
		kvs.forEach(collected::put);
		assertEquals(Map.of("k", "c", "onlyA", "1", "onlyC", "3"), collected);
		assertEquals(3, kvs.size());
	}

	@Test
	void nestedMergeMiddleFullyShadowedIsSkippedDuringIteration() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var shadowed = KeyValues.of(Map.of("a", "2"));
		var high = KeyValues.of(Map.of("a", "3", "b", "4"));
		var kvs = KeyValues.merge(KeyValues.merge(low, shadowed), high);

		LinkedHashMap<String, @Nullable String> collected = new LinkedHashMap<>();
		kvs.forEach(collected::put);
		assertEquals(Map.of("a", "3", "b", "4"), collected);
	}

	@Test
	void explicitNullValueInHighWinsOverLowNonNull() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var highWithNull = MutableKeyValues.of();
		highWithNull.putKeyValue("a", null);
		var kvs = KeyValues.merge(low, highWithNull.freeze());
		assertNull(kvs.getValueOrNull("a"));
		assertEquals(1, kvs.size());
	}

	@Test
	void toStringMatchesMergedContent() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var kvs = KeyValues.merge(low, high);
		assertEquals("{\"A\":\"a\", \"B\":\"b\"}", kvs.toString());
	}

	@Test
	void equalsMatchesEquivalentNonCompositeKeyValues() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var composite = KeyValues.merge(low, high);
		var flat = KeyValues.of(Map.of("A", "a", "B", "b"));
		assertEquals(composite, flat);
		assertEquals(flat, composite);
		assertEquals(composite.hashCode(), flat.hashCode());
	}

	@Test
	void copyToMapReflectsPrecedence() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var high = KeyValues.of(Map.of("a", "2"));
		var kvs = KeyValues.merge(low, high);
		assertEquals(Map.of("a", "2"), kvs.copyToMap());
	}

	@Test
	void isLazyAndReflectsMutationOfUnderlyingMutablePart() throws Exception {
		/*
		 * mutable must be non-empty at merge time, otherwise KeyValues.merge's own
		 * trivial-collapse optimization (high.isEmpty() -> return low unchanged) fires
		 * and kvs never becomes a composite wrapping mutable at all.
		 */
		var mutable = MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		var kvs = KeyValues.merge(KeyValues.of(Map.of("k", "v")), mutable);
		assertEquals("1", kvs.getValueOrNull("a"));

		mutable.putKeyValue("a", "2");
		assertEquals("2", kvs.getValueOrNull("a"), "composite is a live view, not a snapshot");
	}

	@Test
	void freezeIsNoOpWhenBothSidesAlreadyImmutable() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var kvs = KeyValues.merge(low, high);
		assertSame(kvs, kvs.freeze());
	}

	@Test
	void freezeCopiesAwayFromALiveMutablePart() throws Exception {
		var mutable = MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		var kvs = KeyValues.merge(KeyValues.of(Map.of("k", "v")), mutable);

		var frozen = kvs.freeze();
		mutable.putKeyValue("a", "2");

		assertEquals("1", frozen.getValueOrNull("a"), "frozen snapshot must not see later mutation");
		assertEquals("2", kvs.getValueOrNull("a"), "the original live composite still tracks mutation");
	}

	@Test
	void freezeIsNoOpWhenNestedCompositeIsAlreadyFullyImmutable() throws Exception {
		var a = KeyValues.of(Map.of("A", "a"));
		var b = KeyValues.of(Map.of("B", "b"));
		var c = KeyValues.of(Map.of("C", "c"));
		var kvs = KeyValues.merge(KeyValues.merge(a, b), c);
		assertSame(kvs, kvs.freeze());
	}

	@Test
	void freezeCopiesAwayFromAMutableBuriedTwoLevelsDeep() throws Exception {
		var mutable = MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		// mutable is buried inside the *inner* merge, not a direct field of the outer
		// composite - the outer composite's own low/high fields are themselves
		// composites, not MutableKeyValues, so detecting this requires recursing into
		// the nested sides rather than only checking the outer pair directly.
		var inner = KeyValues.merge(KeyValues.of(Map.of("k", "v")), mutable);
		var kvs = KeyValues.merge(inner, KeyValues.of(Map.of("other", "x")));

		var frozen = kvs.freeze();
		mutable.putKeyValue("a", "2");

		assertEquals("1", frozen.getValueOrNull("a"), "frozen snapshot must not see later mutation");
		assertEquals("2", kvs.getValueOrNull("a"), "the original live composite still tracks mutation");
	}

}
