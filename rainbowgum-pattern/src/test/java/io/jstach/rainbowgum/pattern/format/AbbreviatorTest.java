package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * The cache is not Logback's: it is a plain ConcurrentHashMap keyed by logger name,
 * since logger names are as bounded as the loggers that are kept anyway.
 *
 * Caching used to be an all-or-nothing JVM-wide system property
 * (Abbreviator.DISABLE_CACHE_SYSTEM_PROPERTY); Abbreviator.of(int) now always returns an
 * uncached abbreviator and callers opt into caching explicitly via
 * Abbreviator.cache(Abbreviator) - PatternConfig#abbreviatorCache() (see
 * PatternConfiguratorTest) is what actually decides that per pattern-config name.
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
	void ofReturnsUncachedAbbreviator() {
		// caching is opt-in via cache(...) now, driven by
		// PatternConfig#abbreviatorCache()
		// (see PatternFormatterFactory's LOGGER keyword) - of(...) by itself never wraps.
		assertTrue(Abbreviator.of(10) instanceof Abbreviator.TargetLengthBasedClassNameAbbreviator);
		assertSame(Abbreviator.StandardAbbreviator.CLASS_NAME_ONLY, Abbreviator.of(0));
	}

	@Test
	void cacheWrapsResultInCacheAbbreviator() {
		assertInstanceOf(Abbreviator.CacheAbbreviator.class, Abbreviator.cache(Abbreviator.of(10)));
	}

	@Test
	void cachedAbbreviatorReturnsSameStringInstanceOnRepeatCalls() {
		// confirms actual caching (not merely recomputing an equal String) by
		// checking object identity of the result on a cache hit.
		var abbreviator = Abbreviator.cache(Abbreviator.of(10));
		String first = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		String second = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		assertSame(first, second);
	}

	@Test
	void uncachedAbbreviatorRecomputesEveryCall() {
		var abbreviator = Abbreviator.of(10);
		String first = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		String second = abbreviator.abbreviate("io.jstach.logger.MyLogger");
		assertEquals(first, second);
		// a fresh String each time, not the same cached instance.
		assertNotSame(first, second);
	}

	// --- PatternConfig.CacheType.parse(String) - same true/false plus
	// default/basic/disabled aliasing convention as LogEvent.Caller.CallerType.parse ---

	@Test
	void cacheTypeParseAliasesTrueDefaultAndBasicToBasic() {
		assertEquals(PatternConfig.CacheType.BASIC, PatternConfig.CacheType.parse("true"));
		assertEquals(PatternConfig.CacheType.BASIC, PatternConfig.CacheType.parse("TRUE"));
		assertEquals(PatternConfig.CacheType.BASIC, PatternConfig.CacheType.parse("default"));
		assertEquals(PatternConfig.CacheType.BASIC, PatternConfig.CacheType.parse("basic"));
	}

	@Test
	void cacheTypeParseAliasesFalseAndDisabledToDisabled() {
		assertEquals(PatternConfig.CacheType.OFF, PatternConfig.CacheType.parse("false"));
		assertEquals(PatternConfig.CacheType.OFF, PatternConfig.CacheType.parse("off"));
	}

	@Test
	void cacheTypeParseRejectsUnrecognizedValue() {
		var e = assertThrows(IllegalArgumentException.class, () -> PatternConfig.CacheType.parse("nonsense"));
		assertEquals("'nonsense' is not a valid value for io.jstach.rainbowgum.pattern.format.PatternConfig.CacheType. "
				+ "Valid values: 'off', 'basic', 'true', 'default', 'false'", e.getMessage());
	}

	// --- Abbreviator.cache(...) ---

	@Test
	void cacheHitAvoidsRecomputation() {
		AtomicInteger calls = new AtomicInteger();
		var abbreviator = Abbreviator.cache(in -> {
			calls.incrementAndGet();
			return in + "!";
		});
		assertEquals("a!", abbreviator.abbreviate("a"));
		assertEquals("a!", abbreviator.abbreviate("a"));
		assertEquals("b!", abbreviator.abbreviate("b"));
		assertEquals(2, calls.get());
	}

	@Test
	void cacheIsSafeAcrossThreads() throws Exception {
		AtomicInteger calls = new AtomicInteger();
		var abbreviator = Abbreviator.cache(in -> {
			calls.incrementAndGet();
			return in.toUpperCase(java.util.Locale.ROOT);
		});
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(8)) {
			var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			for (int t = 0; t < 8; t++) {
				futures.add(executor.submit(() -> {
					for (int i = 0; i < 10_000; i++) {
						assertEquals("LOGGER." + (i % 50), abbreviator.abbreviate("logger." + (i % 50)));
					}
				}));
			}
			for (var f : futures) {
				f.get();
			}
		}
		// computeIfAbsent computes each key at most once.
		assertEquals(50, calls.get());
	}

}
