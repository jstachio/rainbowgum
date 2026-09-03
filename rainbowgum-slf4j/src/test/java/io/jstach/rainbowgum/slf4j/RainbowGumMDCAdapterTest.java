package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogFormatter;

class RainbowGumMDCAdapterTest {

	private static final LogFormatter formatter = LogFormatter.builder().keyValues().build();

	@ParameterizedTest
	@EnumSource(MdcTest.class)
	void test(MdcTest test) {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("k1", "v1");
		test.run(mdc);
		String expected = test.expected;
		KeyValues kvs = mdc.keyValues();
		LogEvent event = LogEventFactory.of("test").event(System.Logger.Level.INFO, "test", kvs, (Throwable) null);
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	@Test
	void testMDCEmpty() {
		var mdc = new RainbowGumMDCAdapter();
		assertNull(mdc.getCopyOfContextMap());
	}

	/*
	 * The parameterized test() above always seeds with put("k1", "v1") first, so
	 * copyOnThreadLocal is never null when the case under test runs, and the very first
	 * put's "create the map" branch is the only one of the copy-on-write pair
	 * (duplicateAndInsertNewMap vs mutate-in-place) it can exercise for most ops. These
	 * fill in the map-still-null and consecutive-op combinations that arrangement can't
	 * reach.
	 */

	@Test
	void testGetWithNullKeyReturnsNull() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("k1", "v1");
		assertNull(mdc.get(null));
	}

	@Test
	void testGetOnFreshAdapterWithNoMapYetReturnsNull() {
		var mdc = new RainbowGumMDCAdapter();
		assertNull(mdc.get("k1"));
	}

	@Test
	void testRemoveWithNullKeyIsNoop() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("k1", "v1");
		mdc.remove(null);
		assertEquals(Map.of("k1", "v1"), mdc.getCopyOfContextMap());
	}

	@Test
	void testRemoveOnFreshAdapterWithNoMapYetIsNoop() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.remove("k1");
		assertNull(mdc.getCopyOfContextMap());
	}

	@Test
	void testRemoveRightAfterAReadCopiesRatherThanMutatingTheSnapshot() {
		// keyValues(), not getCopyOfContextMap(), is what actually marks
		// lastOperation as a read (MAP_COPY_OPERATION) - getCopyOfContextMap()
		// doesn't touch lastOperation at all.
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("k1", "v1");
		var snapshot = mdc.keyValues();
		mdc.remove("k1");
		assertEquals(Map.of("k1", "v1"), snapshot.copyToMap(),
				"earlier snapshot must not be mutated by the later remove");
		assertEquals(Map.of(), mdc.getCopyOfContextMap());
	}

	@Test
	void testPutRightAfterClearCreatesAFreshMapRatherThanReusingTheClearedOne() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("k1", "v1");
		mdc.clear();
		mdc.put("k2", "v2");
		assertEquals(Map.of("k2", "v2"), mdc.getCopyOfContextMap());
	}

	@Test
	void testSetContextMapReplacesWhateverWasThereBefore() {
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("stale", "value");
		mdc.setContextMap(Map.of("k1", "v1", "k2", "v2"));
		assertEquals(Map.of("k1", "v1", "k2", "v2"), mdc.getCopyOfContextMap());
	}

	enum MdcTest {

		put("k1=v1, k2=v2", a -> a.put("k2", "v2")), //
		remove("", a -> a.remove("k1")), //
		clear("", a -> a.clear()), //
		get("k1=v1", a -> {
			String v = a.get("k1");
			assertEquals("v1", v);
		}), //
		getCopyOfContext("k1=v2", a -> {
			var map = a.getCopyOfContextMap();
			assertEquals(Map.of("k1", "v1"), map, "should be k1=v1");
			a.put("k1", "v2");
			assertEquals(Map.of("k1", "v1"), map);
			map = a.getCopyOfContextMap();
			assertEquals(Map.of("k1", "v2"), map, "should be k1=v2");
		}), //
		pushByKey("k1=v1", a -> a.pushByKey("k1", "v2")), //
		popByKey("k1=v1", a -> {
			assertNull(a.popByKey("k1"));
		}), //
		getCopyOfDequeByKey("k1=v1", a -> {
			assertNull(a.getCopyOfDequeByKey("k1"));
		}), //
		clearDequeByKey("k1=v1", a -> {
			a.clearDequeByKey("k1");
		}),;

		private final String expected;

		private final Consumer<MDCAdapter> consumer;

		private MdcTest(String expected, Consumer<MDCAdapter> consumer) {
			this.expected = expected;
			this.consumer = consumer;
		}

		void run(MDCAdapter mdc) {
			consumer.accept(mdc);
		}

	}

}
