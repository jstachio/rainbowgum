package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class KeyValuesMergeTest {

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

	/*
	 * Regression test: with the old lazy, bit-packed CompositeKeyValues, nesting three or
	 * more merges deep with no key shared/shadowed across layers caused start()/next() to
	 * cycle forever (a corrupted index alias, not just a wrong answer). Eager merge()
	 * flattens into a plain array-backed KeyValues at merge time, so there is no
	 * traversal-order state left to corrupt regardless of nesting depth.
	 */
	@Test
	void deeplyNestedMergeWithNoSharedKeysAcrossLayersTerminatesAndYieldsEveryEntry() throws Exception {
		var a = KeyValues.of(Map.of("a0", "va0", "a1", "va1"));
		var b = KeyValues.of(Map.of("b0", "vb0", "b1", "vb1"));
		var c = KeyValues.of(Map.of("c0", "vc0", "c1", "vc1"));
		var kvs = KeyValues.merge(KeyValues.merge(a, b), c);

		LinkedHashMap<String, @Nullable String> collected = new LinkedHashMap<>();
		kvs.forEach(collected::put);

		assertEquals(6, kvs.size());
		assertEquals(Map.of("a0", "va0", "a1", "va1", "b0", "vb0", "b1", "vb1", "c0", "vc0", "c1", "vc1"), collected);
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
	void equalsMatchesEquivalentKeyValues() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var merged = KeyValues.merge(low, high);
		var flat = KeyValues.of(Map.of("A", "a", "B", "b"));
		assertEquals(merged, flat);
		assertEquals(flat, merged);
		assertEquals(merged.hashCode(), flat.hashCode());
	}

	@Test
	void copyToMapReflectsPrecedence() throws Exception {
		var low = KeyValues.of(Map.of("a", "1"));
		var high = KeyValues.of(Map.of("a", "2"));
		var kvs = KeyValues.merge(low, high);
		assertEquals(Map.of("a", "2"), kvs.copyToMap());
	}

	@Test
	void mergeIsEagerAndDoesNotReflectLaterMutationOfAMutablePart() throws Exception {
		/*
		 * mutable must be non-empty at merge time, otherwise KeyValues.merge's own
		 * trivial-collapse optimization (high.isEmpty() -> return low unchanged) fires
		 * and kvs never copies mutable's contents at all.
		 */
		var mutable = MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		var kvs = KeyValues.merge(KeyValues.of(Map.of("k", "v")), mutable);
		assertEquals("1", kvs.getValueOrNull("a"));

		mutable.putKeyValue("a", "2");
		assertEquals("1", kvs.getValueOrNull("a"), "merge() copies eagerly, later mutation must not be visible");
	}

	@Test
	void mergeResultIsAlreadyFrozenEvenWhenBuiltFromAMutablePart() throws Exception {
		var mutable = MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		var kvs = KeyValues.merge(KeyValues.of(Map.of("k", "v")), mutable);
		assertSame(kvs, kvs.freeze());
	}

	@Test
	void freezeIsNoOpWhenBothSidesAlreadyImmutable() throws Exception {
		var low = KeyValues.of(Map.of("A", "a"));
		var high = KeyValues.of(Map.of("B", "b"));
		var kvs = KeyValues.merge(low, high);
		assertSame(kvs, kvs.freeze());
	}

	@Test
	void freezeIsNoOpWhenNestedMergeIsAlreadyFullyImmutable() throws Exception {
		var a = KeyValues.of(Map.of("A", "a"));
		var b = KeyValues.of(Map.of("B", "b"));
		var c = KeyValues.of(Map.of("C", "c"));
		var kvs = KeyValues.merge(KeyValues.merge(a, b), c);
		assertSame(kvs, kvs.freeze());
	}

}
