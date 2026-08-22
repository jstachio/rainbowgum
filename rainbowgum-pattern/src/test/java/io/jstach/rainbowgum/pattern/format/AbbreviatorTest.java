package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/*
 * Abbreviator (and its TargetLengthBasedClassNameAbbreviator/StandardAbbreviator
 * nested types) is a line-for-line port of Logback's
 * ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator and
 * ClassNameOnlyAbbreviator. The tests below in the "ported from Logback" section
 * are taken verbatim (same inputs/expected outputs) from Logback's own
 * TargetLengthBasedClassNameAbbreviatorTest to confirm this port behaves
 * identically.
 *
 * LogbackCache is likewise a port of the private cache (NameCache /
 * CacheMissCalculator) embedded in Logback's NamedConverter, generalized into a
 * standalone Cache<K, V>. Logback has no dedicated unit test for that internal
 * cache, so the tests in the "cache" section below are original, written
 * directly against the ported algorithm.
 */
class AbbreviatorTest {

	// --- ported from Logback's TargetLengthBasedClassNameAbbreviatorTest ---

	@Test
	void testShortName() {
		var abbreviator = new Abbreviator.TargetLengthBasedClassNameAbbreviator(100);
		assertEquals("hello", abbreviator.abbreviate("hello"));
		assertEquals("hello.world", abbreviator.abbreviate("hello.world"));
	}

	@Test
	void testNoDot() {
		var abbreviator = new Abbreviator.TargetLengthBasedClassNameAbbreviator(1);
		assertEquals("hello", abbreviator.abbreviate("hello"));
	}

	@Test
	void testOneDot() {
		var abbreviator = new Abbreviator.TargetLengthBasedClassNameAbbreviator(1);
		assertEquals("h.world", abbreviator.abbreviate("hello.world"));
		assertEquals("h.world", abbreviator.abbreviate("h.world"));
		assertEquals(".world", abbreviator.abbreviate(".world"));
	}

	@Test
	void testTwoDot() {
		var abbreviator = new Abbreviator.TargetLengthBasedClassNameAbbreviator(1);
		assertEquals("c.l.Foobar", abbreviator.abbreviate("com.logback.Foobar"));
		assertEquals("c.l.Foobar", abbreviator.abbreviate("c.logback.Foobar"));
		assertEquals("c..Foobar", abbreviator.abbreviate("c..Foobar"));
		assertEquals("..Foobar", abbreviator.abbreviate("..Foobar"));
	}

	@Test
	void test3Dot() {
		assertEquals("c.l.x.Foobar",
				new Abbreviator.TargetLengthBasedClassNameAbbreviator(1).abbreviate("com.logback.xyz.Foobar"));
		assertEquals("c.l.x.Foobar",
				new Abbreviator.TargetLengthBasedClassNameAbbreviator(13).abbreviate("com.logback.xyz.Foobar"));
		assertEquals("c.l.xyz.Foobar",
				new Abbreviator.TargetLengthBasedClassNameAbbreviator(14).abbreviate("com.logback.xyz.Foobar"));
		assertEquals("c.l.a.Foobar",
				new Abbreviator.TargetLengthBasedClassNameAbbreviator(15).abbreviate("com.logback.alligator.Foobar"));
	}

	@Test
	void testXDot() {
		assertEquals("c.l.w.a.Foobar", new Abbreviator.TargetLengthBasedClassNameAbbreviator(21)
			.abbreviate("com.logback.wombat.alligator.Foobar"));
		assertEquals("c.l.w.alligator.Foobar", new Abbreviator.TargetLengthBasedClassNameAbbreviator(22)
			.abbreviate("com.logback.wombat.alligator.Foobar"));
		assertEquals("c.l.w.a.t.Foobar", new Abbreviator.TargetLengthBasedClassNameAbbreviator(1)
			.abbreviate("com.logback.wombat.alligator.tomato.Foobar"));
		assertEquals("c.l.w.a.tomato.Foobar", new Abbreviator.TargetLengthBasedClassNameAbbreviator(21)
			.abbreviate("com.logback.wombat.alligator.tomato.Foobar"));
		assertEquals("c.l.w.alligator.tomato.Foobar", new Abbreviator.TargetLengthBasedClassNameAbbreviator(29)
			.abbreviate("com.logback.wombat.alligator.tomato.Foobar"));
	}

	// --- StandardAbbreviator.CLASS_NAME_ONLY (RainbowGum's ClassNameOnlyAbbreviator
	// equivalent) ---

	@Test
	void classNameOnlyStripsPackage() {
		assertEquals("MyLogger",
				Abbreviator.StandardAbbreviator.CLASS_NAME_ONLY.abbreviate("io.jstach.logger.MyLogger"));
	}

	@Test
	void classNameOnlyReturnsInputWhenNoDot() {
		assertEquals("MyLogger", Abbreviator.StandardAbbreviator.CLASS_NAME_ONLY.abbreviate("MyLogger"));
	}

	// --- Abbreviator.of(int) / Abbreviator.cache(...) factories ---

	@Test
	void ofNonPositiveLengthUsesClassNameOnly() {
		assertEquals("MyLogger", Abbreviator.of(0).abbreviate("io.jstach.logger.MyLogger"));
		assertEquals("MyLogger", Abbreviator.of(-5).abbreviate("io.jstach.logger.MyLogger"));
	}

	@Test
	void ofPositiveLengthUsesTargetLengthAbbreviator() {
		assertEquals("i.j.l.MyLogger", Abbreviator.of(10).abbreviate("io.jstach.logger.MyLogger"));
	}

	@Test
	void ofWrapsResultInCache() {
		assertInstanceOf(Abbreviator.CacheAbbreviator.class, Abbreviator.of(10));
	}

	@Test
	void cachedAbbreviatorReturnsSameStringInstanceOnRepeatCalls() {
		// confirms actual caching (not merely recomputing an equal String) by
		// checking object identity of the result on a cache hit.
		var abbreviator = Abbreviator.of(10);
		String first = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		String second = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		assertSame(first, second);
	}

	@Test
	void cacheIsBypassedWhenDisabledViaSystemProperty() {
		System.setProperty(Abbreviator.DISABLE_CACHE_SYSTEM_PROPERTY, "true");
		try {
			Abbreviator delegate = Abbreviator.StandardAbbreviator.CLASS_NAME_ONLY;
			assertSame(delegate, Abbreviator.cache(delegate));
		}
		finally {
			System.clearProperty(Abbreviator.DISABLE_CACHE_SYSTEM_PROPERTY);
		}
	}

	// --- LogbackCache (generic caching layer backing Abbreviator.cache) ---

	@Test
	void cacheHitAvoidsRecomputation() {
		AtomicInteger calls = new AtomicInteger();
		var cache = new LogbackCache<String, String>(k -> {
			calls.incrementAndGet();
			return k + "!";
		});
		assertEquals("a!", cache.value("a"));
		assertEquals("a!", cache.value("a"));
		assertEquals(1, calls.get());
	}

	@Test
	void disableCacheBypassesCachingGoingForward() {
		AtomicInteger calls = new AtomicInteger();
		var cache = new LogbackCache<String, String>(k -> {
			calls.incrementAndGet();
			return k + "!";
		});
		cache.value("a");
		cache.disableCache();
		cache.disableCache(); // idempotent - must not throw or double-clear
		cache.value("a");
		cache.value("a");
		assertEquals(3, calls.get());
	}

	@Test
	void sustainedLowMissRateDoesNotDoubleRemovalThreshold() {
		// A miss (new distinct key) happens on every 10th call - spread across
		// the whole run rather than front-loaded - keeping the miss rate around
		// 10%, well under the 30% trigger. Spreading the misses out matters:
		// shouldDoubleRemovalThreshold is only re-checked on a put (i.e. a
		// miss), so if all misses happened before the 1024-call sample window
		// even filled, the check would never run with a computable (non
		// negative) rate at all.
		var cache = new LogbackCache<String, String>(k -> k);
		int uniqueCounter = 0;
		for (int i = 0; i < 2000; i++) {
			String key = (i % 10 == 0) ? "unique-" + (uniqueCounter++) : "shared-" + (i % 3);
			cache.value(key);
		}
		assertTrue(cache.getCacheMissRate() < 0.3d);
		assertEquals(384, cache.removalThreshold);
	}

	@Test
	void sustainedHighMissRateDoublesThresholdThenDisablesCacheAtMax() {
		// Every key is unique, so the miss rate is a constant 100% - each time a
		// 1024-call sample window fills, shouldDoubleRemovalThreshold doubles
		// the removal threshold: 384 -> 768 (at call 1024) -> 1536 (at call
		// 2048). At call 3072 the threshold is already at the 1536 max, so
		// instead of doubling again the cache disables itself outright. Once
		// disabled, totalCalls/cacheMisses stop being tracked, so
		// getCacheMisses() freezes at 3072 even though 3200 calls are made.
		var cache = new LogbackCache<String, String>(k -> k);
		for (int i = 0; i < 3200; i++) {
			cache.value("key-" + i);
		}
		assertEquals(3072, cache.getCacheMisses());
		assertEquals(1536, cache.removalThreshold);
	}

}
