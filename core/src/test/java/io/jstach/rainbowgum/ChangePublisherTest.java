package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.CallerType;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;

class ChangePublisherTest {

	LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
			logging.change=true
			logging.caller=true
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
		assertEquals(EnumSet.of(ChangeType.LEVEL), changes);
	}

	@Test
	void testCallerTypeTrueAliasesToBasic() {
		assertEquals(CallerType.BASIC, changePublisher.callerType("anything"));
		assertTrue(changePublisher.callerInfoEnabled("anything"));
	}

	/*
	 * logging.change and logging.caller are resolved completely independently now -
	 * neither implies nor overrides the other, unlike when CALLER lived inside
	 * ChangeType.
	 */
	@Test
	void testCallerTypeIsIndependentOfAllowedChanges() {
		var levelOnlyConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change.only=level
				""").build()).build();
		ChangePublisher levelOnly = changePublisher(levelOnlyConfig);
		assertFalse(levelOnly.callerInfoEnabled("only"));
		assertEquals(CallerType.NONE, levelOnly.callerType("only"));
		assertTrue(levelOnly.allowedChanges("only").contains(ChangeType.LEVEL));

		var callerOnlyConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.caller.only=true
				""").build()).build();
		ChangePublisher callerOnly = changePublisher(callerOnlyConfig);
		assertTrue(callerOnly.callerInfoEnabled("only"));
		assertEquals(Set.of(), callerOnly.allowedChanges("only"));
	}

	/*
	 * Reproduces and proves the fix for the bug found while testing the caching branch:
	 * logging.caller.com.mycompany=true + logging.change.com.mycompany.app=level used to
	 * leave com.mycompany.app with no caller info, because both were resolved out of one
	 * combined property/Set<ChangeType> and findOrNull's hierarchical walk is closest-
	 * match-wins with no merging - the child's own logging.change entry silently replaced
	 * the parent's inherited caller value instead of the two coexisting. Now that each
	 * property walks its own hierarchy independently, com.mycompany.app correctly
	 * inherits caller info from its ancestor while also having its own level-changeable
	 * setting.
	 */
	@Test
	void testCallerInheritanceIsNotClobberedByAMoreSpecificChangeEntry() {
		var nestedConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.caller.com.mycompany=true
				logging.change.com.mycompany.app=level
				""").build()).build();
		var cp = changePublisher(nestedConfig);

		assertEquals(CallerType.BASIC, cp.callerType("com.mycompany.app"));
		assertTrue(cp.callerInfoEnabled("com.mycompany.app"));
		assertTrue(cp.allowedChanges("com.mycompany.app").contains(ChangeType.LEVEL));
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
	void testCallerTypeIsCachedPerLoggerNameAndNotReparsed() {
		var real = LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.caller.foo=true
				""").build();
		AtomicInteger reads = new AtomicInteger();
		LogProperties counting = key -> {
			reads.incrementAndGet();
			return real.valueOrNull(key);
		};
		var countingConfig = LogConfig.builder().properties(counting).build();
		var cp = changePublisher(countingConfig);

		var first = cp.callerType("foo");
		int afterFirst = reads.get();
		assertTrue(afterFirst > 0, "first call must actually read the property");

		var second = cp.callerType("foo");
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

	/*
	 * Also covers the migration path for anyone still setting the old, pre-split
	 * logging.change.<name>=caller value under the new logging.caller.<name> property -
	 * "caller" isn't a valid CallerType token either, so it alerts and falls back to NONE
	 * the same way any other malformed value does.
	 */
	@Test
	void testMalformedCallerValueAlertsOnceAndCachesTheFallback() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.caller.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		assertEquals(CallerType.NONE, cp.callerType("bad"));
		assertEquals(1, badConfig.alerts().dump().size());

		assertEquals(CallerType.NONE, cp.callerType("bad"));
		assertEquals(1, badConfig.alerts().dump().size(), "the fallback must be cached, not re-parsed and re-alerted");
	}

	/*
	 * Golden-master pin of the exact alert produced for a malformed logging.change.<name>
	 * value. See git history for two earlier, plainer messages this evolved from: first a
	 * hand-rolled string around a raw try/catch, then Result#map's own richer message
	 * once allowedChanges() switched to LogProperty.Result - now pinned to
	 * Result#validateNow(Class)'s ValidationException wrapping that same message (with a
	 * "Validation failed for <component>:" header), so any further behavior change here
	 * shows up as an explicit, deliberate diff instead of silently drifting.
	 */
	@Test
	void testMalformedChangeValueGoldenAlert() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.change.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		cp.allowedChanges("bad");

		var event = badConfig.alerts().dump().get(0);
		assertEquals(ChangePublisher.class.getName(), event.loggerName());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$ChangePublisher:
						Error for property. key: 'logging.change.bad' from PROPERTIES_STRING[logging.change.bad], \
						java.lang.IllegalArgumentException No enum constant io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType.NONSENSE
						Tried: 'logging.change.bad' from PROPERTIES_STRING[logging.change.bad]""",
				event.message());
		var throwable = event.throwableOrNull();
		assertNotNull(throwable);
		assertEquals(LogProperty.ValidationException.class, throwable.getClass());
		var cause = throwable.getCause();
		assertNotNull(cause);
		assertEquals(IllegalArgumentException.class, cause.getClass());
		assertEquals("No enum constant io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType.NONSENSE",
				cause.getMessage());
	}

	/*
	 * Sibling of testMalformedChangeValueGoldenAlert, same reasoning - pins CallerType's
	 * exact alert message the same way.
	 */
	@Test
	void testMalformedCallerValueGoldenAlert() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.global.change=true
				logging.caller.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		cp.callerType("bad");

		var event = badConfig.alerts().dump().get(0);
		assertEquals(ChangePublisher.class.getName(), event.loggerName());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$ChangePublisher:
						Error for property. key: 'logging.caller.bad' from PROPERTIES_STRING[logging.caller.bad], \
						java.lang.IllegalArgumentException No enum constant io.jstach.rainbowgum.LogConfig.ChangePublisher.CallerType.NONSENSE
						Tried: 'logging.caller.bad' from PROPERTIES_STRING[logging.caller.bad]""",
				event.message());
		var throwable = event.throwableOrNull();
		assertNotNull(throwable);
		assertEquals(LogProperty.ValidationException.class, throwable.getClass());
		var cause = throwable.getCause();
		assertNotNull(cause);
		assertEquals(IllegalArgumentException.class, cause.getClass());
		assertEquals("No enum constant io.jstach.rainbowgum.LogConfig.ChangePublisher.CallerType.NONSENSE",
				cause.getMessage());
	}

	@Test
	void testPublishClearsTheCacheForNamesRequestedAfterward() {
		MutableLogProperties props = MutableLogProperties.builder().copyProperties("""
				logging.global.change=true
				logging.change.foo=level
				logging.caller.foo=false
				""").build();
		var mutableConfig = LogConfig.builder().properties(props).build();
		var cp = changePublisher(mutableConfig);

		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("foo"));
		assertEquals(CallerType.NONE, cp.callerType("foo"));

		props.put("logging.change.foo", "false");
		props.put("logging.caller.foo", "true");
		cp.publish();

		assertEquals(Set.of(), cp.allowedChanges("foo"),
				"a name requested after publish() must see the updated property, not the cached pre-publish value");
		assertEquals(CallerType.BASIC, cp.callerType("foo"),
				"callerType's own cache must also be cleared on publish(), independently of allowedChanges()'s");
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
