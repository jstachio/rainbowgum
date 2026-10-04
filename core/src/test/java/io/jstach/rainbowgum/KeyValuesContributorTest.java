package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

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
	void contributorsMergeInNameOrderSoTheLastNameWins() {
		var registry = ServiceRegistry.of();
		registry.put(KeyValuesContributor.class, "b", contributor(Map.of("env", "b", "onlyB", "1")));
		registry.put(KeyValuesContributor.class, "a", contributor(Map.of("env", "a", "onlyA", "1")));
		registry.put(KeyValuesContributor.class, "c", contributor(Map.of("env", "c")));
		var kvs = KeyValuesContributor.of(registry).keyValues();
		assertEquals("c", kvs.getValueOrNull("env"));
		assertEquals("1", kvs.getValueOrNull("onlyA"));
		assertEquals("1", kvs.getValueOrNull("onlyB"));
		assertEquals(3, kvs.size());
	}

	@Test
	void excludedNamesAreSkipped() {
		var registry = ServiceRegistry.of();
		registry.put(KeyValuesContributor.class, "mine", contributor(Map.of("env", "mine")));
		registry.put(KeyValuesContributor.class, "other", contributor(Map.of("env", "other")));
		assertEquals("env=other", render(KeyValuesContributor.of(registry, "mine").keyValues()));
		assertTrue(KeyValuesContributor.of(registry, "mine", "other").keyValues().isEmpty());
	}

	@Test
	void registryIsReadWhenResolved() {
		var registry = ServiceRegistry.of();
		var resolved = KeyValuesContributor.of(registry);
		registry.put(KeyValuesContributor.class, "late", contributor(Map.of("env", "late")));
		assertTrue(resolved.keyValues().isEmpty());
	}

	@Test
	void globalFollowsTheBoundGumAndDefaultKeyValuesUsesIt() {
		var config = LogConfig.builder().build();
		config.serviceRegistry().put(KeyValuesContributor.class, "test", contributor(Map.of("requestId", "abc")));
		var factory = LogEventFactory.of("test");
		try (var gum = RainbowGum.builder(config).set()) {
			assertEquals("requestId=abc", render(factory.defaultKeyValues()));
			assertEquals("requestId=abc", render(KeyValuesContributor.global().keyValues()));
			assertTrue(KeyValuesContributor.global("test").keyValues().isEmpty());
		}
	}

}
