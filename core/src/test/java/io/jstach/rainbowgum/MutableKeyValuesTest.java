package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class MutableKeyValuesTest {

	@Test
	void testCopy() {
		Map<String, String> expected = Map.of("A", "a", "B", "b");
		var kvs = create();
		kvs.putAll(expected);
		var actual = kvs.copyToMap();
		assertEquals(expected, actual);
	}

	@Test
	void testPutKeyValueDuplicate() {
		var kvs = create();
		kvs.putKeyValue("A", "should replace");
		kvs.putKeyValue("B", "b");
		kvs.putKeyValue("A", "a");
		assertEquals(2, kvs.size());
		assertEquals("a", kvs.getValueOrNull("A"));
	}

	@Test
	void testRemove() {
		var kvs = create();
		kvs.putKeyValue("A", "should replace");
		kvs.putKeyValue("B", "b");
		kvs.putKeyValue("A", "a");
		assertEquals(2, kvs.size());
		kvs.remove("A");
		assertEquals(1, kvs.size());
		assertNull(kvs.getValueOrNull("A"));
		kvs.putKeyValue("A", "added after remove");
		assertEquals(2, kvs.size());
		assertEquals("added after remove", kvs.getValueOrNull("A"));
	}

	@ParameterizedTest
	@MethodSource("removeArgs")
	void testRemoveThen(RemoveSetup setup, AfterRemove afterRemove) {
		var kvs = setup.kvs();
		afterRemove.test(kvs);
	}

	private static Stream<Arguments> removeArgs() {
		return EnumCombinations.args(RemoveSetup.class, AfterRemove.class);
	}

	enum RemoveSetup {

		MIDDLE {
			@Override
			MutableKeyValues kvs() {
				var kvs = MutableKeyValues.of();
				kvs.putKeyValue("A", "a");
				kvs.putKeyValue("B", "b");
				kvs.putKeyValue("C", "c");
				kvs.remove("B");
				return kvs;
			}
		},
		FIRST {
			@Override
			MutableKeyValues kvs() {
				var kvs = MutableKeyValues.of();
				kvs.putKeyValue("B", "b");
				kvs.putKeyValue("A", "a");
				kvs.putKeyValue("C", "c");
				kvs.remove("B");
				return kvs;
			}
		},
		LAST {
			@Override
			MutableKeyValues kvs() {
				var kvs = MutableKeyValues.of();
				kvs.putKeyValue("A", "a");
				kvs.putKeyValue("C", "c");
				kvs.putKeyValue("B", "b");
				kvs.remove("B");
				return kvs;
			}
		},
		REMOVE_ALL_RE_ADD {
			@Override
			MutableKeyValues kvs() {
				var kvs = MutableKeyValues.of();
				char[] alphabet = new char[26];
				char letter = 'a';

				for (int i = 0; i < 26; i++) {
					alphabet[i] = letter;
					letter++;
				}
				for (char c : alphabet) {
					String k = ("" + c).toUpperCase(Locale.ROOT);
					String v = "" + c;
					kvs.putKeyValue(k, v);
				}

				for (char c : alphabet) {
					String k = ("" + c).toUpperCase(Locale.ROOT);
					if (k.equals("A") || k.equals("C")) {
						continue;
					}
					kvs.remove(k);
				}
				return kvs;
			}
		},;

		abstract MutableKeyValues kvs();

	}

	enum AfterRemove {

		FOR_EACH_KEY_VALUES_CONSUMER() {
			@Override
			void test(MutableKeyValues kvs) {
				StringBuilder sb = new StringBuilder();
				var r = kvs.forEach((_kvs, k, v, index, storage) -> {
					sb.append(k).append("=").append(v).append(",");
					return index + 1;
				}, 0, sb);
				assertEquals(2, r);
				assertEquals("A=a,C=c,", sb.toString());

			}
		},
		FOR_LOOP() {
			@Override
			void test(MutableKeyValues kvs) {
				StringBuilder sb = new StringBuilder();
				for (int i = kvs.start(); i >= 0; i = kvs.next(i)) {
					var k = kvs.key(i);
					var v = kvs.valueOrNull(i);
					sb.append(k).append("=").append(v).append(",");
				}
				assertEquals("A=a,C=c,", sb.toString());
			}
		},
		GET_VALUE() {
			@Override
			void test(MutableKeyValues kvs) {
				assertEquals("a", kvs.getValueOrNull("A"));
				assertEquals("c", kvs.getValueOrNull("C"));
				assertNull(kvs.getValueOrNull("B"));

			}
		},
		TO_STRING() {
			@Override
			void test(MutableKeyValues kvs) {
				String actual = kvs.toString();
				assertEquals("""
						{"A":"a", "C":"c"}
						""".trim(), actual);
			}
		},
		COPY_TO_MAP() {
			@Override
			void test(MutableKeyValues kvs) {
				assertEquals(Map.of("A", "a", "C", "c"), kvs.copyToMap());
			}
		},;

		abstract void test(MutableKeyValues kvs);

	}

	@Test
	void testPutAll() {
		var kvs = create();
		kvs.putAll(Map.of("A", "a"));
		assertEquals(1, kvs.size());
		assertEquals("a", kvs.getValueOrNull("A"));
	}

	@Test
	void testRemoveOnEmpty() {
		var kvs = create();
		kvs.remove("k1");
		assertEquals(KeyValues.of(), kvs);
	}

	@Test
	void testAcceptIgnoresNullKeys() {
		var kvs = create();
		kvs.accept(null, "a");
	}

	@Test
	void testEmptyOnFreezeIsEmpty() {
		var kvs = create();
		var actual = kvs.freeze();
		assertInstanceOf(EmptyKeyValues.class, actual);
	}

	@Test
	void testFreezeEqualsFreeze() {
		var kvs = create();
		kvs.add("k1", "v1");
		var expected = kvs.freeze();
		var actual = expected.freeze();
		assertTrue(actual == expected);
		assertEquals(expected, actual);
		assertInstanceOf(ImmutableArrayKeyValues.class, actual);
	}

	@Test
	void testNotEqualsToNull() {
		var kvs = create();
		assertFalse(kvs.equals(null));
		assertFalse(kvs.freeze().equals(null));
		kvs.putKeyValue("k1", "v1");
		assertFalse(kvs.equals(null));
		assertFalse(kvs.freeze().equals(null));
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	void testNotEqualsWrongType() {
		var kvs = create();
		assertFalse(kvs.equals("BAD"));
		assertFalse(kvs.freeze().equals("BAD"));
		kvs.putKeyValue("k1", "v1");
		assertFalse(kvs.equals("BAD"));
		assertFalse(kvs.freeze().equals("BAD"));
	}

	@Test
	void testEqualsEmptyWithImmutableEmpty() {
		var kvs = create();
		var empty = KeyValues.of();
		assertEquals(empty, kvs);
		assertEquals(kvs, empty);
		kvs.putKeyValue("k1", "v1");
		assertNotEquals(empty, kvs);
	}

	@Test
	void testHashCodeOnEmpty() {
		var kvs = create();
		assertEquals(kvs.hashCode(), kvs.freeze().hashCode());
	}

	@Test
	void testEqualsMutableWithImmutable() {
		int count = 2;
		var first = create();
		var other = create();
		for (int i = 0; i < count; i++) {
			first.putKeyValue("k" + i, "v" + i);
			other.putKeyValue("k" + i, "v" + i);
		}
		assertEquals(other, first);
		assertEquals(first, other);
		assertEquals(other.hashCode(), first.hashCode());
		KeyValues actual = other.freeze();
		assertEquals(other, actual);
		assertEquals(first, actual);
		assertEquals(actual, other);
		assertEquals(actual, first);
		assertEquals(other.hashCode(), actual.hashCode());
	}

	@Test
	void testEqualsOrderDoesNotMatter() {
		int count = 2;
		var first = create();
		var other = create();
		for (int i = 0, j = count - 1; i < count; i++, j--) {
			first.putKeyValue("k" + i, "v" + i);
			other.putKeyValue("k" + j, "v" + j);
		}
		/*
		 * Test we have all the same keys
		 */
		Map<String, @Nullable String> o = other.copyToMap();
		Map<String, @Nullable String> f = other.copyToMap();
		assertEquals(o, f);
		assertEquals(other, first);

		assertEquals(other.copy(), first);
		assertEquals(other, first.copy());

		assertEquals(other.hashCode(), first.hashCode());

		assertEquals(other.copy().hashCode(), first.hashCode());
		assertEquals(other.hashCode(), first.copy().hashCode());

		other.putKeyValue("a1", "b1");
		assertNotEquals(other, first);
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testValueOrNullBadIndex(KeyValues kvs) {
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.valueOrNull(1));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.valueOrNull(0));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.valueOrNull(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.valueOrNull(Integer.MAX_VALUE));
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testKeyBadIndex(KeyValues kvs) {
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.key(1));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.key(0));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.key(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> kvs.key(Integer.MAX_VALUE));
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testStartEmpty(KeyValues kvs) {
		assertEquals(-1, kvs.start());
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testNextEmpty(KeyValues kvs) {
		assertEquals(-1, kvs.next(1));
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testFreezeEmpty(KeyValues kvs) {
		assertTrue(kvs.freeze().isEmpty());
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testEmptyEqualToSelf(KeyValues kvs) {
		assertEquals(kvs, kvs);
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testEmptyGetValueOrNull(KeyValues kvs) {
		assertNull(kvs.getValueOrNull("IGNORE"));
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testEmptyMapCopy(KeyValues kvs) {
		var copy = kvs.copyToMap();
		assertEquals(copy, kvs.copyToMap());
		assertTrue(copy.isEmpty());
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testEmptyForEachBiShouldDoNothing(KeyValues kvs) {
		AtomicInteger i = new AtomicInteger();
		kvs.forEach((k, v) -> i.incrementAndGet());
		assertEquals(0, i.get());
	}

	@ParameterizedTest
	@MethodSource("kvsEmpties")
	void testEmptyForEachShouldDoNothing(KeyValues kvs) {
		StringBuilder sb = new StringBuilder();
		var r = kvs.forEach((_kvs, k, v, index, storage) -> {
			sb.append(k).append(v);
			return 1;
		}, 1, sb);
		assertEquals("", sb.toString());
		assertEquals(1, r);
	}

	@SuppressWarnings("null")
	private static Stream<Arguments> kvsEmpties() {
		return Stream.of(Arguments.of(KeyValues.of()), Arguments.of(MutableKeyValues.of()));
	}

	@Test
	void testBadInitialCapacity() {
		assertThrows(IllegalArgumentException.class, () -> new ArrayKeyValues(-1));
	}

	MutableKeyValues create() {
		return MutableKeyValues.of();
	}

}
