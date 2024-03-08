package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * 
 */
public class KeyValuesTest {

	@Test
	void testGetValue() throws Exception {
		var kvs = create(Map.of("A", "a", "B", "b"));
		assertEquals(2, kvs.size());
		assertEquals("a", kvs.getValueOrNull("A"));
		assertEquals("b", kvs.getValueOrNull("B"));
	}

	@Test
	void testForEach() throws Exception {
		var m = Map.of("A", "a", "B", "b");
		var kvs = create(m);
		LinkedHashMap<String, @Nullable String> copy = new LinkedHashMap<>();
		kvs.forEach(copy::put);
		assertEquals(m, copy);
	}

	@Test
	void testLooping() throws Exception {
		var m = new LinkedHashMap<String, String>();
		for (int i = 1; i < 200; i++) {
			m.put(String.valueOf(i), i + "value");
		}
		// Map.of("1", "1value", "2", "2value", "3", "3value");
		var kvs = create(m);
		for (int i = kvs.start(), j = 0; i > -1; i = kvs.next(i), j++) {
			String k = kvs.key(i);
			String v = kvs.valueOrNull(i);
			String ek = String.valueOf(j + 1);
			String ev = (j + 1) + "value";
			assertEquals(ek, k, "key");
			assertEquals(ev, v, "value");
		}
	}

	@Test
	void testToString() throws Exception {
		var kvs = create("A", "a", "B", "b");
		String actual = kvs.toString();
		assertEquals("{\"A\":\"a\", \"B\":\"b\"}", actual);
	}

	@Test
	void testEquals() throws Exception {
		var a = create(Map.of("A", "a"));
		var b = create(Map.of("A", "a"));
		var c = create(Map.of("C", "c"));
		assertEquals(a, b);
		assertEquals(b, a);
		assertNotEquals(c, a);
		assertNotEquals(c, b);

	}

	KeyValues create(String k1, String v1, String k2, String v2) {
		var m = new LinkedHashMap<String, String>();
		m.put(k1, v1);
		m.put(k2, v2);
		return create(m);
	}

	KeyValues create(Map<String, String> m) {
		return MutableKeyValues.of(m);
	}

}
