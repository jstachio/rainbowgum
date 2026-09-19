package io.jstach.rainbowgum.scopedkeyvalues;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/*
 * No ScopedKeyValuesProvider is on this module's own test classpath, so these prove the
 * NOP fallback: pushing still runs/calls the body normally, it just never records or
 * reads back anything - the "safe to depend on with nothing bound" contract.
 */
class ScopedKeyValuesTest {

	@Test
	void currentMergedIsEmptyWithNoProviderBound() {
		assertTrue(ScopedKeyValues.currentMerged().isEmpty());
	}

	@Test
	void runStillExecutesTheBody() {
		boolean[] ran = new boolean[1];
		ScopedKeyValues.builder().add("requestId", "abc123").run(() -> ran[0] = true);
		assertTrue(ran[0]);
	}

	@Test
	void pushedValuesAreNotReadableBackWithNoProviderBound() {
		ScopedKeyValues.builder().add("requestId", "abc123").run(() -> {
			assertTrue(ScopedKeyValues.currentMerged().isEmpty());
		});
	}

	@Test
	void callReturnsTheBodysResult() throws Exception {
		String result = ScopedKeyValues.builder().add("k", "v").call(() -> "hello");
		assertEquals("hello", result);
	}

	@Test
	void callPropagatesTheBodysException() {
		var builder = ScopedKeyValues.builder().add("k", "v");
		org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> builder.call(() -> {
			throw new IllegalStateException("boom");
		}));
	}

	@Test
	void addAllowsANullValue() {
		boolean[] ran = new boolean[1];
		ScopedKeyValues.builder().add("k", null).run(() -> ran[0] = true);
		assertTrue(ran[0]);
	}

}
