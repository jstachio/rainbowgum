package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig.ChangePublisher;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeMode;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;

class ChangePublisherTest {

	LogConfig config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
			logging.change.logger=true
			logging.caller.logger=true
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
	 * logging.change.logger and logging.caller.logger are resolved completely
	 * independently now - neither implies nor overrides the other, unlike when CALLER
	 * lived inside ChangeType.
	 */
	@Test
	void testCallerTypeIsIndependentOfAllowedChanges() {
		var levelOnlyConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.change.logger.only=level
				""").build()).build();
		ChangePublisher levelOnly = changePublisher(levelOnlyConfig);
		assertFalse(levelOnly.callerInfoEnabled("only"));
		assertEquals(CallerType.NONE, levelOnly.callerType("only"));
		assertTrue(levelOnly.allowedChanges("only").contains(ChangeType.LEVEL));

		var callerOnlyConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.caller.logger.only=true
				""").build()).build();
		ChangePublisher callerOnly = changePublisher(callerOnlyConfig);
		assertTrue(callerOnly.callerInfoEnabled("only"));
		assertEquals(Set.of(), callerOnly.allowedChanges("only"));
	}

	/*
	 * Reproduces and proves the fix for the bug found while testing the caching branch:
	 * logging.caller.logger.com.mycompany=true +
	 * logging.change.logger.com.mycompany.app=level used to leave com.mycompany.app with
	 * no caller info, because both were resolved out of one combined
	 * property/Set<ChangeType> and findOrNull's hierarchical walk is closest- match-wins
	 * with no merging - the child's own logging.change.logger entry silently replaced the
	 * parent's inherited caller value instead of the two coexisting. Now that each
	 * property walks its own hierarchy independently, com.mycompany.app correctly
	 * inherits caller info from its ancestor while also having its own level-changeable
	 * setting.
	 */
	@Test
	void testCallerInheritanceIsNotClobberedByAMoreSpecificChangeEntry() {
		var nestedConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.caller.logger.com.mycompany=true
				logging.change.logger.com.mycompany.app=level
				""").build()).build();
		var cp = changePublisher(nestedConfig);

		assertEquals(CallerType.BASIC, cp.callerType("com.mycompany.app"));
		assertTrue(cp.callerInfoEnabled("com.mycompany.app"));
		assertTrue(cp.allowedChanges("com.mycompany.app").contains(ChangeType.LEVEL));
	}

	@Test
	void testAllowedChangesIsCachedPerLoggerNameAndNotReparsed() {
		var real = LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.change.logger.foo=level
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
				logging.change.mode=on
				logging.caller.logger.foo=true
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
				logging.change.mode=on
				logging.change.logger.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		assertEquals(Set.of(), cp.allowedChanges("bad"));
		assertEquals(1, badConfig.alerts().dump().size());

		assertEquals(Set.of(), cp.allowedChanges("bad"));
		assertEquals(1, badConfig.alerts().dump().size(), "the fallback must be cached, not re-parsed and re-alerted");
	}

	/*
	 * Also covers the migration path for anyone still setting the old, pre-split
	 * logging.change.logger.<name>=caller value under the new
	 * logging.caller.logger.<name> property - "caller" isn't a valid CallerType token
	 * either, so it alerts and falls back to NONE the same way any other malformed value
	 * does.
	 */
	@Test
	void testMalformedCallerValueAlertsOnceAndCachesTheFallback() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.caller.logger.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		assertEquals(CallerType.NONE, cp.callerType("bad"));
		assertEquals(1, badConfig.alerts().dump().size());

		assertEquals(CallerType.NONE, cp.callerType("bad"));
		assertEquals(1, badConfig.alerts().dump().size(), "the fallback must be cached, not re-parsed and re-alerted");
	}

	/*
	 * Golden-master pin of the exact alert produced for a malformed
	 * logging.change.logger.<name> value. See git history for two earlier, plainer
	 * messages this evolved from: first a hand-rolled string around a raw try/catch, then
	 * Result#map's own richer message once allowedChanges() switched to
	 * LogProperty.Result - now pinned to Result#validateNow(Class)'s ValidationException
	 * wrapping that same message (with a "Validation failed for <component>:" header), so
	 * any further behavior change here shows up as an explicit, deliberate diff instead
	 * of silently drifting.
	 */
	@Test
	void testMalformedChangeValueGoldenAlert() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.change.logger.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		cp.allowedChanges("bad");

		var event = badConfig.alerts().dump().get(0);
		assertEquals(ChangePublisher.class.getName(), event.loggerName());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$ChangePublisher:
						Error for property. key: 'logging.change.logger.bad' from PROPERTIES_STRING[logging.change.logger.bad], \
						'nonsense' is not a valid value for io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType. Valid values: 'none', 'level', 'true', 'false'""",
				event.message());
		var throwable = event.throwableOrNull();
		assertNotNull(throwable);
		assertEquals(LogProperty.ValidationException.class, throwable.getClass());
		var cause = throwable.getCause();
		assertNotNull(cause);
		assertEquals(IllegalArgumentException.class, cause.getClass());
		assertEquals("'nonsense' is not a valid value for io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType. "
				+ "Valid values: 'none', 'level', 'true', 'false'", cause.getMessage());
	}

	/*
	 * Sibling of testMalformedChangeValueGoldenAlert, same reasoning - pins CallerType's
	 * exact alert message the same way.
	 */
	@Test
	void testMalformedCallerValueGoldenAlert() {
		var badConfig = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.caller.logger.bad=nonsense
				""").build()).build();
		var cp = changePublisher(badConfig);

		cp.callerType("bad");

		var event = badConfig.alerts().dump().get(0);
		assertEquals(ChangePublisher.class.getName(), event.loggerName());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$ChangePublisher:
						Error for property. key: 'logging.caller.logger.bad' from PROPERTIES_STRING[logging.caller.logger.bad], \
						'nonsense' is not a valid value for io.jstach.rainbowgum.LogEvent.Caller.CallerType. Valid values: 'none', 'basic', 'true', 'false'""",
				event.message());
		var throwable = event.throwableOrNull();
		assertNotNull(throwable);
		assertEquals(LogProperty.ValidationException.class, throwable.getClass());
		var cause = throwable.getCause();
		assertNotNull(cause);
		assertEquals(IllegalArgumentException.class, cause.getClass());
		assertEquals("'nonsense' is not a valid value for io.jstach.rainbowgum.LogEvent.Caller.CallerType. "
				+ "Valid values: 'none', 'basic', 'true', 'false'", cause.getMessage());
	}

	@Test
	void testPublishClearsTheCacheForNamesRequestedAfterward() {
		MutableLogProperties props = MutableLogProperties.builder().copyProperties("""
				logging.change.mode=on
				logging.change.logger.foo=level
				logging.caller.logger.foo=false
				""").build();
		var mutableConfig = LogConfig.builder().properties(props).build();
		var cp = changePublisher(mutableConfig);

		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("foo"));
		assertEquals(CallerType.NONE, cp.callerType("foo"));

		props.put("logging.change.logger.foo", "false");
		props.put("logging.caller.logger.foo", "true");
		cp.publish();

		assertEquals(Set.of(), cp.allowedChanges("foo"),
				"a name requested after publish() must see the updated property, not the cached pre-publish value");
		assertEquals(CallerType.BASIC, cp.callerType("foo"),
				"callerType's own cache must also be cleared on publish(), independently of allowedChanges()'s");
	}

	@Test
	void changeModeOffIgnoresPerLoggerChanges() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.logger=level
				""").build()).build();
		assertEquals(Set.of(), config.changePublisher().allowedChanges("anything"));
	}

	@Test
	void changeModeOnUsesPerLoggerChanges() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.change.logger.com.mycompany=level
				""").build()).build();
		assertEquals(EnumSet.of(ChangeType.LEVEL), config.changePublisher().allowedChanges("com.mycompany.app"));
		assertEquals(Set.of(), config.changePublisher().allowedChanges("org.other"));
	}

	@Test
	void changeModeFromBuilderIsOverriddenByProperty() {
		var props = LogProperties.builder().fromProperties("""
				logging.change.logger=level
				""").build();
		var on = LogConfig.builder().changeMode(ChangeMode.ON).properties(props).build();
		assertEquals(EnumSet.of(ChangeType.LEVEL), on.changePublisher().allowedChanges("anything"));

		var offProps = LogProperties.builder().fromProperties("""
				logging.change.mode=off
				logging.change.logger=level
				""").build();
		var off = LogConfig.builder().changeMode(ChangeMode.ON).properties(offProps).build();
		assertEquals(Set.of(), off.changePublisher().allowedChanges("anything"));
	}

	@Test
	void callerInfoDoesNotNeedChangeMode() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.caller.logger.com.mycompany=true
				""").build()).build();
		assertEquals(CallerType.BASIC, config.changePublisher().callerType("com.mycompany.app"));
		assertEquals(Set.of(), config.changePublisher().allowedChanges("com.mycompany.app"));
	}

	@Test
	void changeModeOffDoesNotNotifySubscribers() {
		var config = LogConfig.builder().build();
		AtomicInteger calls = new AtomicInteger();
		config.changePublisher().subscribe(c -> calls.incrementAndGet());
		config.changePublisher().publish();
		assertEquals(0, calls.get());
	}

	@Test
	void malformedChangeModeGoldenError() {
		var props = LogProperties.builder().fromProperties("""
				logging.change.mode=yes
				""").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder().properties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.DefaultLogConfig:
						Error for property. key: 'logging.change.mode' from PROPERTIES_STRING[logging.change.mode], \
						'yes' is not a valid value for io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeMode. Valid values: 'off', 'on', 'true', 'false'""",
				e.getMessage());
	}

	/*
	 * Groups let a logger name that would be awkward as a property key be listed as a
	 * value instead.
	 */
	@Test
	void groupsApplyToChangeAndCallerForAwkwardLoggerNames() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.groups=weird
				logging.group.weird=my weird:logger,com.example.Outer$Inner
				logging.change.logger.weird=level
				logging.caller.logger.weird=true
				""").build()).build();
		var cp = config.changePublisher();
		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("my weird:logger"));
		assertEquals(CallerType.BASIC, cp.callerType("my weird:logger"));
		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("com.example.Outer$Inner"));
		assertEquals(Set.of(), cp.allowedChanges("com.example.Other"));
		assertEquals(CallerType.NONE, cp.callerType("com.example.Other"));
	}

	@Test
	void groupAppliesToDescendantsOfItsLoggers() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.groups=tomcat
				logging.group.tomcat=org.apache.catalina,org.apache.coyote
				logging.change.logger.tomcat=level
				""").build()).build();
		assertEquals(EnumSet.of(ChangeType.LEVEL),
				config.changePublisher().allowedChanges("org.apache.catalina.core.StandardContext"));
		assertEquals(Set.of(), config.changePublisher().allowedChanges("org.apache.tomcat"));
	}

	/*
	 * Same precedence as levels: at each step of the logger path the logger's own key
	 * wins over a group, and a match closer to the logger wins over one further up.
	 */
	@Test
	void groupPrecedenceMatchesLevels() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.groups=first,second,app
				logging.group.first=com.shared
				logging.group.second=com.shared
				logging.group.app=com.app
				logging.change.logger.first=level
				logging.change.logger.second=none
				logging.change.logger.app=level
				logging.change.logger.com.app=none
				logging.change.logger.com=level
				logging.caller.logger.app=true
				logging.caller.logger.com.app.Main=false
				""").build()).build();
		var cp = config.changePublisher();
		assertEquals(Set.of(), cp.allowedChanges("com.shared.Thing"), "the last group listed wins");
		assertEquals(Set.of(), cp.allowedChanges("com.app.Thing"), "the logger's own key wins over its group");
		assertEquals(CallerType.NONE, cp.callerType("com.app.Main"), "a closer key wins over a group further up");
		assertEquals(CallerType.BASIC, cp.callerType("com.app.Other"));
	}

	@Test
	void groupNotInGroupsPropertyIsIgnored() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.group.tomcat=org.apache.catalina
				logging.change.logger.tomcat=level
				""").build()).build();
		assertEquals(Set.of(), config.changePublisher().allowedChanges("org.apache.catalina"));
	}

	@Test
	void malformedGroupChangeValueGoldenAlert() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.change.mode=on
				logging.groups=tomcat
				logging.group.tomcat=org.apache.catalina
				logging.change.logger.tomcat=nonsense
				""").build()).build();
		assertEquals(Set.of(), config.changePublisher().allowedChanges("org.apache.catalina"));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig$ChangePublisher:
						Error for property. key: 'logging.change.logger.tomcat' from PROPERTIES_STRING[logging.change.logger.tomcat], \
						'nonsense' is not a valid value for io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType. Valid values: 'none', 'level', 'true', 'false'""",
				config.alerts().dump().get(0).message());
	}

	@Test
	void publishRereadsGroups() {
		MutableLogProperties props = MutableLogProperties.builder().copyProperties("""
				logging.change.mode=on
				logging.groups=g
				logging.group.g=a
				logging.change.logger.g=level
				""").build();
		var config = LogConfig.builder().properties(props).build();
		var cp = config.changePublisher();
		assertEquals(Set.of(), cp.allowedChanges("b"));
		props.put("logging.group.g", "a,b");
		cp.publish();
		assertEquals(EnumSet.of(ChangeType.LEVEL), cp.allowedChanges("b"));
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
