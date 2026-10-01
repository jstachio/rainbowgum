package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProvider.ProvisionException;

class LogProviderTest {

	private static LogConfig config() {
		return LogConfig.builder().build();
	}

	/*
	 * describe()'s own message building always copies the wrapped exception's full
	 * getMessage() as a prefix (see LogProvider#describe), which made that wrapped
	 * exception pure duplication once it was also set as the cause: printStackTrace()
	 * would print its "Caused by:" block too, repeating everything the outer message
	 * already said. describe() must skip straight past it to its own cause instead.
	 */
	@Test
	void testDescribeCauseSkipsTheWrappedExceptionWhenItHasItsOwnCause() {
		RuntimeException leaf = new RuntimeException("leaf failure");
		RuntimeException middle = new RuntimeException("middle failure", leaf);
		LogProvider<String> provider = (n, c) -> {
			throw middle;
		};

		var thrown = assertThrows(ProvisionException.class, () -> provider.describe("thing").provide("n", config()));
		var cause = thrown.getCause();
		assertNotNull(cause);
		assertSame(leaf, cause, "cause must skip the fully redundant middle exception");
		assertEquals("middle failure\n  ↳ Failure providing thing.", thrown.getMessage());
	}

	/*
	 * A leaf exception (no cause of its own) IS the one non-redundant piece of
	 * information: there is nothing beneath it to skip to, so it must be kept, not
	 * dropped.
	 */
	@Test
	void testDescribeCauseKeepsTheWrappedExceptionWhenItHasNoCauseOfItsOwn() {
		RuntimeException leaf = new RuntimeException("leaf failure");
		LogProvider<String> provider = (n, c) -> {
			throw leaf;
		};

		var thrown = assertThrows(ProvisionException.class, () -> provider.describe("thing").provide("n", config()));
		var cause = thrown.getCause();
		assertNotNull(cause);
		assertSame(leaf, cause);
	}

	/*
	 * Chaining multiple describe() calls (one per layer of appender/route provisioning in
	 * real usage) must still collapse all the way down to the one real leaf cause, not
	 * just skip one layer: each wrap() call only has its own immediate e to look at, so
	 * this only works if the previous wrap() call already did its own skipping.
	 */
	@Test
	void testChainedDescribeCallsStillCollapseToTheOriginalLeafCause() {
		RuntimeException leaf = new RuntimeException("leaf failure");
		LogProvider<String> provider = (n, c) -> {
			throw leaf;
		};

		var thrown = assertThrows(ProvisionException.class,
				() -> provider.describe("inner").describe("outer").provide("n", config()));
		var cause = thrown.getCause();
		assertNotNull(cause);
		assertSame(leaf, cause);
		assertNull(leaf.getCause());
		assertEquals("leaf failure\n  ↳ Failure providing inner.\n  ↳ Failure providing outer.", thrown.getMessage());
	}

}
