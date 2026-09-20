package io.jstach.rainbowgum.scopedkeyvalues.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/*
 * Exercises ScopedKeyValuesProviderImpl directly, bypassing the ScopedKeyValues facade
 * (which lives in a different module and is covered by its own module's tests) - proves
 * the actual ScopedValue-backed push/pop and precedence semantics still hold after the
 * facade/provider split.
 */
class ScopedKeyValuesProviderImplTest {

	private final ScopedKeyValuesProviderImpl provider = new ScopedKeyValuesProviderImpl();

	/*
	 * Map.of(...) cannot be used here: its value type parameter is bound to non-null
	 * Object, which is incompatible with push's nullable-valued Map parameter even though
	 * these tests never actually pass a null value.
	 */
	private static Map<String, @Nullable String> layer(String key, String value) {
		Map<String, @Nullable String> m = new LinkedHashMap<>();
		m.put(key, value);
		return m;
	}

	@Test
	void currentMergedIsEmptyOutsideAnyPush() {
		assertTrue(provider.currentMerged().isEmpty());
	}

	@Test
	void pushedValuesAreVisibleForTheDurationOfTheBody() {
		provider.push(layer("requestId", "abc123"), () -> {
			assertEquals("abc123", provider.currentMerged().get("requestId"));
		});
	}

	@Test
	void pushedValuesAreGoneAfterRunReturns() {
		provider.push(layer("requestId", "abc123"), () -> {
		});
		assertTrue(provider.currentMerged().isEmpty());
	}

	@Test
	void nestedPushesAccumulateWithoutCollision() {
		provider.push(layer("outer", "o"), () -> provider.push(layer("inner", "i"), () -> {
			var merged = provider.currentMerged();
			assertEquals("o", merged.get("outer"));
			assertEquals("i", merged.get("inner"));
		}));
	}

	@Test
	void laterNestedPushWinsOnCollision() {
		provider.push(layer("k", "outer"), () -> provider.push(layer("k", "inner"), () -> {
			assertEquals("inner", provider.currentMerged().get("k"));
		}));
	}

	@Test
	void callReturnsTheBodysResult() throws Exception {
		String result = provider.push(layer("k", "v"), () -> "hello");
		assertEquals("hello", result);
	}

}
