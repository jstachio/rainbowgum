package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor.Source.Standard;

class KeyValuesContributorTest {

	private static KeyValuesContributor contributor(Map<String, String> m) {
		var kvs = KeyValues.of(m);
		return () -> kvs;
	}

	private static String render(KeyValues kvs) {
		var sb = new StringBuilder();
		kvs.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
		return sb.toString().strip();
	}

	@Test
	void nothingRegisteredIsEmpty() {
		var registry = ServiceRegistry.of();
		assertTrue(KeyValuesContributor.of(registry).keyValues().isEmpty());
	}

	@Test
	void contributorsMergeInEnumOrderSoTheLaterConstantWins() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "slf4j", "onlySlf4j", "1")));
		KeyValuesContributor.register(registry, Standard.SCOPED_KEY_VALUES,
				contributor(Map.of("env", "scoped", "onlyScoped", "1")));
		KeyValuesContributor.register(registry, Standard.LOG4J2, contributor(Map.of("env", "log4j2")));
		var kvs = KeyValuesContributor.of(registry).keyValues();
		assertEquals("slf4j", kvs.getValueOrNull("env"));
		assertEquals("1", kvs.getValueOrNull("onlySlf4j"));
		assertEquals("1", kvs.getValueOrNull("onlyScoped"));
		assertEquals(3, kvs.size());
	}

	@Test
	void excludedSourcesAreSkipped() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "slf4j")));
		KeyValuesContributor.register(registry, Standard.JBOSS_LOGGING, contributor(Map.of("env", "jboss")));
		assertEquals("env=jboss", render(KeyValuesContributor.of(registry, Standard.SLF4J).keyValues()));
		assertTrue(KeyValuesContributor.of(registry, Standard.SLF4J, Standard.JBOSS_LOGGING).keyValues().isEmpty());
	}

	@Test
	void contributorsNotRegisteredForAStandardSourceAreIgnored() {
		var registry = ServiceRegistry.of();
		registry.put(KeyValuesContributor.class, "thirdParty", contributor(Map.of("env", "thirdParty")));
		assertTrue(KeyValuesContributor.of(registry).keyValues().isEmpty());
	}

	@Test
	void aRegisteredSourceIsNotReplaced() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "first")));
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "second")));
		assertEquals("env=first", render(KeyValuesContributor.of(registry).keyValues()));
	}

	@Test
	void registryIsReadWhenResolved() {
		var registry = ServiceRegistry.of();
		var resolved = KeyValuesContributor.of(registry);
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "late")));
		assertTrue(resolved.keyValues().isEmpty());
	}

	@Test
	void globalFollowsTheBoundGumAndDefaultKeyValuesUsesIt() {
		var config = LogConfig.builder().build();
		KeyValuesContributor.register(config.serviceRegistry(), Standard.SLF4J,
				contributor(Map.of("requestId", "abc")));
		var factory = LogEventFactory.of("test");
		try (var gum = RainbowGum.builder(config).set()) {
			assertEquals("requestId=abc", render(factory.defaultKeyValues()));
			assertEquals("requestId=abc", render(KeyValuesContributor.global().keyValues()));
			assertTrue(KeyValuesContributor.global(Standard.SLF4J).keyValues().isEmpty());
		}
	}

	private static final class ThreadLocalContributor implements KeyValuesContributor {

		@SuppressWarnings("ThreadLocalUsage") // one per store under test
		final ThreadLocal<@Nullable KeyValues> local = new ThreadLocal<>();

		@Override
		public KeyValues keyValues() {
			var kvs = local.get();
			return kvs == null ? KeyValues.of() : kvs;
		}

		@Override
		public void clear() {
			local.remove();
		}

	}

	@Test
	void clearClearsEveryRegisteredStore() {
		var registry = ServiceRegistry.of();
		var slf4j = new ThreadLocalContributor();
		var jboss = new ThreadLocalContributor();
		KeyValuesContributor.register(registry, Standard.SLF4J, slf4j);
		KeyValuesContributor.register(registry, Standard.JBOSS_LOGGING, jboss);
		KeyValuesContributor.register(registry, Standard.SCOPED_KEY_VALUES, contributor(Map.of("scoped", "1")));
		slf4j.local.set(KeyValues.of(Map.of("a", "1")));
		jboss.local.set(KeyValues.of(Map.of("b", "2")));
		var all = KeyValuesContributor.of(registry);
		assertEquals("scoped=1 b=2 a=1", render(all.keyValues()));
		all.clear();
		assertEquals("scoped=1", render(all.keyValues()));
	}

	@Test
	void globalClearClearsTheBoundGumsStores() {
		var config = LogConfig.builder().build();
		var slf4j = new ThreadLocalContributor();
		KeyValuesContributor.register(config.serviceRegistry(), Standard.SLF4J, slf4j);
		KeyValuesContributor.global().clear(); // nothing bound: does nothing
		try (var gum = RainbowGum.builder(config).set()) {
			slf4j.local.set(KeyValues.of(Map.of("requestId", "abc")));
			KeyValuesContributor.global().clear();
			assertTrue(KeyValuesContributor.global().keyValues().isEmpty());
		}
	}

	@Test
	void userWinsOverEveryOtherSource() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.USER, contributor(Map.of("env", "user")));
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "slf4j", "onlySlf4j", "1")));
		var kvs = KeyValuesContributor.of(registry).keyValues();
		assertEquals("user", kvs.getValueOrNull("env"));
		assertEquals("1", kvs.getValueOrNull("onlySlf4j"));
	}

	@Test
	void facadeOwnKeyValuesGoAboveOtherSourcesButBelowUser() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.SLF4J,
				contributor(Map.of("env", "slf4j", "other", "slf4j", "onlySlf4j", "1")));
		KeyValuesContributor.register(registry, Standard.USER, contributor(Map.of("env", "user")));
		var others = KeyValuesContributor.of(registry, Standard.JBOSS_LOGGING);
		var own = KeyValues.of(Map.of("env", "jboss", "other", "jboss"));
		var kvs = others.keyValues(own);
		assertEquals("user", kvs.getValueOrNull("env"));
		assertEquals("jboss", kvs.getValueOrNull("other"));
		assertEquals("1", kvs.getValueOrNull("onlySlf4j"));
	}

	@Test
	void facadeOwnKeyValuesAreReturnedAsIsWhenNothingElseContributes() {
		var own = KeyValues.of(Map.of("env", "jboss"));
		assertSame(own, KeyValuesContributor.of(ServiceRegistry.of()).keyValues(own));
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.JBOSS_LOGGING, contributor(Map.of("env", "x")));
		assertSame(own, KeyValuesContributor.of(registry, Standard.JBOSS_LOGGING).keyValues(own));
	}

	@Test
	void clearAlsoClearsUser() {
		var registry = ServiceRegistry.of();
		var user = new ThreadLocalContributor();
		KeyValuesContributor.register(registry, Standard.USER, user);
		user.local.set(KeyValues.of(Map.of("a", "1")));
		KeyValuesContributor.of(registry).clear();
		assertTrue(user.keyValues().isEmpty());
	}

}
