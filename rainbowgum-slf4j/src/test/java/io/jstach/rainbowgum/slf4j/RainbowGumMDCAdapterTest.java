package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
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
		KeyValues kvs = mdc.mutableKeyValuesOrNull();
		if (kvs == null) {
			kvs = KeyValues.of();
		}
		LogEvent event = LogEvent.of(System.Logger.Level.INFO, "test", "test", kvs, null);
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	enum MdcTest {

		put("k1=v1&k2=v2", a -> a.put("k2", "v2")), remove("", a -> a.remove("k1")), clear("", a -> a.clear()),
		get("k1=v1", a -> {
			String v = a.get("k1");
			assertEquals("v1", v);
		}), pushByKey("k1=v1", a -> a.pushByKey("k1", "v2")), popByKey("k1=v1", a -> {
			assertNull(a.popByKey("k1"));
		}), getCopyOfDequeByKey("k1=v1", a -> {
			assertNull(a.getCopyOfDequeByKey("k1"));
		}), clearDequeByKey("k1=v1", a -> {
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
