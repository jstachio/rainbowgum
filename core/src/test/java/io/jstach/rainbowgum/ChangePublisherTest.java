package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;

class ChangePublisherTest {

	LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
			logging.change=true
			""").build()).build();

	ChangePublisher changePublisher = new AbstractChangePublisher() {

		@Override
		protected LogConfig reload() {
			return config;
		}

		@Override
		protected LogConfig config() {
			return config;
		}

	};

	@Test
	void test() {
		Set<ChangeType> changes = changePublisher.allowedChanges("anything");
		assertEquals(EnumSet.of(ChangeType.LEVEL, ChangeType.CALLER), changes);
	}

	@Test
	void testCallerInfoEnabledAgreesWithAllowedChanges() {
		assertTrue(changePublisher.callerInfoEnabled("anything"));

		var levelOnlyConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change.only=level
				""").build()).build();
		ChangePublisher levelOnly = changePublisher(levelOnlyConfig);
		assertFalse(levelOnly.callerInfoEnabled("only"));
		assertTrue(levelOnly.allowedChanges("only").contains(ChangeType.LEVEL));
	}

	@Test
	void testAllowedChangesIsCachedPerLoggerNameAndNotReparsed() {
		var real = LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change.foo=level
				""").build();
		AtomicInteger reads = new AtomicInteger();
		LogProperties counting = key -> {
			reads.incrementAndGet();
			return real.valueOrNull(key);
		};
		var countingConfig = LogConfig.builder().properties(counting).build();
		var cp = changePublisher(countingConfig);

		var first = cp.allowedChanges("foo");
		int afterFirst = reads.get();
		assertTrue(afterFirst > 0, "first call must actually read the property");

		var second = cp.allowedChanges("foo");
		assertEquals(first, second);
		assertEquals(afterFirst, reads.get(), "second call for the same name must not re-read the property");
	}

	@Test
	void testMalformedChangeValueAlertsOnceAndCachesTheFallback() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		assertEquals(Set.of(), cp.allowedChanges("bad"));
		assertEquals(1, badConfig.alerts().dump().size());

		assertEquals(Set.of(), cp.allowedChanges("bad"));
		assertEquals(1, badConfig.alerts().dump().size(), "the fallback must be cached, not re-parsed and re-alerted");
	}

	@Test
	void testPublishClearsTheCacheForNamesRequestedAfterward() {
		MutableLogProperties props = MutableLogProperties.builder().copyProperties("""
				logging.global.change=true
				logging.change.foo=level
				""").build();
		var mutableConfig = LogConfig.builder().properties(props).build();
		var cp = changePublisher(mutableConfig);

		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("foo"));

		props.put("logging.change.foo", "caller");
		cp.publish();

		assertEquals(EnumSet.of(ChangeType.CALLER), cp.allowedChanges("foo"),
				"a name requested after publish() must see the updated property, not the cached pre-publish value");
	}

	private static ChangePublisher changePublisher(LogConfig config) {
		return new AbstractChangePublisher() {

			@Override
			protected LogConfig reload() {
				return config;
			}

			@Override
			protected LogConfig config() {
				return config;
			}

		};
	}

}
