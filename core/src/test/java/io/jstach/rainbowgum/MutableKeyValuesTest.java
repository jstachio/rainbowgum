package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class MutableKeyValuesTest {

	@Test
	void testCopy() {
		var expected = Map.of("A", "a", "B", "b");
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
		assertEquals("a", kvs.getValue("A"));
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
		assertNull(kvs.getValue("A"));
		kvs.putKeyValue("A", "added after remove");
		assertEquals(2, kvs.size());
		assertEquals("added after remove", kvs.getValue("A"));
	}

	@Test
	void testPutAll() {
		var kvs = create();
		kvs.putAll(Map.of("A", "a"));
		assertEquals(1, kvs.size());
		assertEquals("a", kvs.getValue("A"));
	}

	MutableKeyValues create() {
		return MutableKeyValues.of();
	}

}
