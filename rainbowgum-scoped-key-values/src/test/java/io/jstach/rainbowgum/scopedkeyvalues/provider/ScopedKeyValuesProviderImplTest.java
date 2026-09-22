package io.jstach.rainbowgum.scopedkeyvalues.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/*
 * Exercises ScopedKeyValuesProviderImpl directly, bypassing the ScopedKeyValues facade
 * (which lives in a different module and is covered by its own module's tests) - proves
 * the actual ScopedValue-backed push/pop and precedence semantics still hold after the
 * facade/provider split.
 */
class ScopedKeyValuesProviderImplTest {

	private final ScopedKeyValuesProviderImpl provider = new ScopedKeyValuesProviderImpl();

	@Test
	void currentMergedIsEmptyOutsideAnyPush() {
		assertTrue(provider.currentMerged().isEmpty());
	}

	@Test
	void pushedValuesAreVisibleForTheDurationOfTheBody() {
		provider.builder().add("requestId", "abc123").run(() -> {
			assertEquals("abc123", provider.currentMerged().get("requestId"));
		});
	}

	@Test
	void pushedValuesAreGoneAfterRunReturns() {
		provider.builder().add("requestId", "abc123").run(() -> {
		});
		assertTrue(provider.currentMerged().isEmpty());
	}

	@Test
	void nestedPushesAccumulateWithoutCollision() {
		provider.builder().add("outer", "o").run(() -> provider.builder().add("inner", "i").run(() -> {
			var merged = provider.currentMerged();
			assertEquals("o", merged.get("outer"));
			assertEquals("i", merged.get("inner"));
		}));
	}

	@Test
	void laterNestedPushWinsOnCollision() {
		provider.builder().add("k", "outer").run(() -> provider.builder().add("k", "inner").run(() -> {
			assertEquals("inner", provider.currentMerged().get("k"));
		}));
	}

	@Test
	void callReturnsTheBodysResult() throws Exception {
		String result = provider.builder().add("k", "v").call(() -> "hello");
		assertEquals("hello", result);
	}

	@Test
	void aSingleBuilderCanBePushedMultipleTimesIndependently() throws Exception {
		var builder = provider.builder().add("k", "v1");
		builder.run(() -> assertEquals("v1", provider.currentMerged().get("k")));

		builder.add("k", "v2");
		builder.run(() -> assertEquals("v2", provider.currentMerged().get("k")));
	}

}
