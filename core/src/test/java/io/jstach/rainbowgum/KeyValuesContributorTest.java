package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
	void defaultsLoseToEveryOtherSourceAndTheCallersOwn() {
		var registry = ServiceRegistry.of();
		KeyValuesContributor.register(registry, Standard.DEFAULTS,
				contributor(Map.of("env", "default", "version", "1.0", "status", "default")));
		KeyValuesContributor.register(registry, Standard.SCOPED_KEY_VALUES, contributor(Map.of("env", "scoped")));
		var own = KeyValues.of(Map.of("status", "own"));
		var kvs = KeyValuesContributor.of(registry).keyValues(own);
		assertEquals("scoped", kvs.getValueOrNull("env"));
		assertEquals("own", kvs.getValueOrNull("status"));
		assertEquals("1.0", kvs.getValueOrNull("version"));
	}

	@Test
	void disabledPropertyLeavesSourcesOut() {
		var config = LogConfig.builder()
			.properties(LogProperties.builder()
				.fromProperties("logging.keyvalues.disabled=defaults,scoped_key_values")
				.build())
			.build();
		var registry = config.serviceRegistry();
		KeyValuesContributor.register(registry, Standard.DEFAULTS, contributor(Map.of("version", "1.0")));
		KeyValuesContributor.register(registry, Standard.SCOPED_KEY_VALUES, contributor(Map.of("scoped", "1")));
		KeyValuesContributor.register(registry, Standard.SLF4J, contributor(Map.of("env", "slf4j")));
		assertEquals("env=slf4j", render(KeyValuesContributor.of(registry).keyValues()));
		// a facade's own context is still used
		assertEquals("env=slf4j own=1",
				render(KeyValuesContributor.of(registry).keyValues(KeyValues.of(Map.of("own", "1")))));
	}

	@Test
	void invalidDisabledSourceFailsTheConfig() {
		var properties = LogProperties.builder().fromProperties("logging.keyvalues.disabled=defaults,mdc").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder().properties(properties).build());
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogConfig:
				Error for property. key: 'logging.keyvalues.disabled' from PROPERTIES_STRING[logging.keyvalues.disabled], \
				'mdc' is not a valid value for io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor.Source.Standard. \
				Valid values: 'defaults', 'opentelemetry', 'scoped_key_values', 'jboss_logging', 'log4j2', 'slf4j', 'user'""";
		assertEquals(expected, e.getMessage());
	}

	@ParameterizedTest
	@EnumSource(Standard.class)
	void disabledSourceCleanupFollowsItsPolicy(Standard source) {
		var properties = LogProperties.builder()
			.fromProperties("logging.keyvalues.disabled=" + source.name().toLowerCase(Locale.ROOT))
			.build();
		var config = LogConfig.builder().properties(properties).build();
		var calls = new ArrayList<String>();
		KeyValuesContributor.register(config.serviceRegistry(), source, new KeyValuesContributor() {
			@Override
			public KeyValues keyValues() {
				calls.add("keyValues");
				return KeyValues.of(Map.of("requestId", "previous-request"));
			}

			@Override
			public void clear() {
				calls.add("clear");
			}
		});
		try (var gum = RainbowGum.builder(config).set()) {
			var global = KeyValuesContributor.global();
			assertTrue(global.keyValues().isEmpty());
			var own = KeyValues.of(Map.of("requestId", "own"));
			assertSame(own, global.keyValues(own));
			global.clear();
			var expected = switch (source) {
				case SLF4J, LOG4J2, JBOSS_LOGGING -> List.of("clear");
				case DEFAULTS, OPENTELEMETRY, SCOPED_KEY_VALUES, USER -> List.<String>of();
			};
			assertEquals(expected, calls);
			KeyValuesContributor.global(source).clear();
			assertEquals(expected, calls, "Explicitly excluded sources must not be cleared");
		}
	}

	@ParameterizedTest
	@EnumSource(Standard.class)
	void enabledSourceStillClearsRegardlessOfItsDisabledPolicy(Standard source) {
		var config = LogConfig.builder().build();
		var store = new ThreadLocalContributor();
		store.local.set(KeyValues.of(Map.of("requestId", "previous-request")));
		KeyValuesContributor.register(config.serviceRegistry(), source, store);
		try (var gum = RainbowGum.builder(config).set()) {
			assertEquals("previous-request", KeyValuesContributor.global().keyValues().getValueOrNull("requestId"));
			KeyValuesContributor.global().clear();
			assertNull(store.local.get());
		}
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

	enum GlobalContextCase {

		ALL, EXCLUDE_SLF4J;

		KeyValuesContributor contributor() {
			return this == ALL ? KeyValuesContributor.global() : KeyValuesContributor.global(Standard.SLF4J);
		}

	}

	@ParameterizedTest
	@EnumSource(GlobalContextCase.class)
	void globalContributorDoesNotRetainClosedGum(GlobalContextCase testCase) throws InterruptedException {
		var contributor = testCase.contributor();
		try {
			var closed = closedGum(contributor);
			/* No subsequent context lookup should be required to release a closed gum. */
			for (int i = 0; i < 100 && !closed.refersTo(null); i++) {
				System.gc();
				Thread.sleep(25);
			}
			assertNull(closed.get(), "reading context must not retain a closed gum");
		}
		finally {
			Reference.reachabilityFence(contributor);
		}
	}

	private static WeakReference<@Nullable RainbowGum> closedGum(KeyValuesContributor contributor) {
		var config = LogConfig.builder().build();
		/* Capture the config as a real context store may do. */
		KeyValuesContributor.register(config.serviceRegistry(), Standard.USER,
				() -> KeyValues.of(Map.of("config", config.toString())));
		try (var gum = RainbowGum.builder(config).set()) {
			assertEquals(config.toString(), contributor.keyValues().getValueOrNull("config"));
			return new WeakReference<>(gum);
		}
	}

	@ParameterizedTest
	@EnumSource(GlobalContextCase.class)
	void globalContributorFollowsRebinding(GlobalContextCase testCase) {
		var contributor = testCase.contributor();
		for (String value : new String[] { "first", "second" }) {
			var config = LogConfig.builder().build();
			KeyValuesContributor.register(config.serviceRegistry(), Standard.USER, contributor(Map.of("env", value)));
			KeyValuesContributor.register(config.serviceRegistry(), Standard.SLF4J,
					contributor(Map.of("slf4j", "present")));
			try (var gum = RainbowGum.builder(config).set()) {
				assertEquals(value, contributor.keyValues().getValueOrNull("env"));
				assertEquals(testCase == GlobalContextCase.ALL ? "present" : null,
						contributor.keyValues().getValueOrNull("slf4j"));
			}
			assertTrue(contributor.keyValues().isEmpty());
		}
	}

}
