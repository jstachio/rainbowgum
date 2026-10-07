package io.jstach.rainbowgum.keyvalues.contributor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

class DefaultKeyValuesTest {

	private static LogConfig config(String properties) {
		return LogConfig.builder()
			.serviceLoader()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.build();
	}

	private static String render(KeyValues kvs) {
		var sb = new StringBuilder();
		kvs.forEach((k, v) -> sb.append(k).append('=').append(v).append(' '));
		return sb.toString().strip();
	}

	/*
	 * What a logging facade's events get, through the globally bound gum.
	 */
	private static String eventKeyValues(LogConfig config) {
		try (var gum = RainbowGum.builder(config).set()) {
			return render(LogEventFactory.of("test").defaultKeyValues());
		}
	}

	@Test
	void propertyValuesAreOnEveryEventInDeclaredOrder() {
		var config = config("logging.keyvalues.contributor.defaults=instance.id=i-42&git.commit=abc123");
		assertEquals("instance.id=i-42 git.commit=abc123", eventKeyValues(config));
	}

	@Test
	void everyOtherSourceWins() {
		var config = config("logging.keyvalues.contributor.defaults=env=default&version=1.0");
		var slf4j = KeyValues.of(Map.of("env", "slf4j"));
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.SLF4J,
				() -> slf4j);
		// an overridden key keeps its position and takes the winning value
		assertEquals("env=slf4j version=1.0", eventKeyValues(config));
	}

	@Test
	void propertyWinsOverCode() {
		var config = config("logging.keyvalues.contributor.defaults=env=property");
		var defaults = DefaultKeyValues.of(config.serviceRegistry());
		defaults.put("env", "code");
		defaults.put("app_status", "STARTING");
		assertEquals("env=property app_status=STARTING", eventKeyValues(config));
	}

	@Test
	void changesInCodeShowOnTheNextEvent() {
		var config = config("");
		var defaults = DefaultKeyValues.of(config.serviceRegistry());
		try (var gum = RainbowGum.builder(config).set()) {
			var factory = LogEventFactory.of("test");
			assertTrue(factory.defaultKeyValues().isEmpty());
			defaults.put("app_status", "STARTING");
			assertEquals("app_status=STARTING", render(factory.defaultKeyValues()));
			defaults.put("app_status", "READY");
			assertEquals("app_status=READY", render(factory.defaultKeyValues()));
			defaults.remove("app_status");
			assertTrue(factory.defaultKeyValues().isEmpty());
			defaults.set(KeyValues.of(Map.of("a", "1")));
			assertEquals("a=1", render(factory.defaultKeyValues()));
		}
	}

	@Test
	void setCopiesOnce() {
		var config = config("");
		var defaults = DefaultKeyValues.of(config.serviceRegistry());
		var mutable = KeyValues.MutableKeyValues.of();
		mutable.putKeyValue("a", "1");
		defaults.set(mutable);
		mutable.putKeyValue("a", "changed");
		assertEquals("a=1", render(defaults.keyValues()));
	}

	@Test
	void disabledLeavesThemOut() {
		var config = config("""
				logging.keyvalues.contributor.defaults=git.commit=abc123
				logging.keyvalues.disabled=defaults
				""");
		assertEquals("", eventKeyValues(config));
	}

}
