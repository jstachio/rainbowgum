package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogProperties.PropertyMissingException;
import io.jstach.rainbowgum.LogProperties.PropertyValue;

class LogPropertiesTest {

	@Test
	void testLevelResolverProperty() {
		Map<String, String> m = Map.of("logging.level.com.stuff", "DEBUG");
		LogProperties props = s -> m.get(s);
		var extractor = ConfigLevelResolver.levelExtractor;
		var property = extractor.property("com.stuff");
		// assertEquals(Level.ALL, property.require(props));
		PropertyValue<Level> value = property.get(props);
		value.value();
	}

	@Test
	void testStaticPropertiesDescription() {
		var properties = LogProperties.of(List.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES,
				LogProperties.StandardProperties.ENVIRONMENT_VARIABLES));
		try {
			Property.builder().build("logging.some.ignoreMe").get(properties).value();
			fail("expected exception");
		}
		catch (PropertyMissingException e) {
			assertEquals(
					"Property missing. keys: ['logging.some.ignoreMe' from SYSTEM_PROPERTIES, "
							+ "'logging.some.ignoreMe' from ENVIRONMENT_VARIABLES[logging_some_ignoreMe]]",
					e.getMessage());
		}
	}

	@Test
	void testLogPropertiesBuilderMissingDescription() {
		var props = LogProperties.builder()
			.fromURIQuery(URI.create("stuff:///?blah=hello"))
			.renameKey(k -> LogProperties.removeKeyPrefix(k, LogProperties.ROOT_PREFIX))
			.build();
		try {
			Property.builder().build("logging.some.ignoreMe").get(props).value();
			fail("expected exception");
		}
		catch (PropertyMissingException e) {
			assertEquals(
					"Property missing. keys: ['logging.some.ignoreMe' from URI('stuff:///?blah=hello')[some.ignoreMe]]",
					e.getMessage());
		}
	}

	@Test
	void testLogPropertiesRenameKey() {
		var props = LogProperties.builder()
			.fromURIQuery(URI.create("stuff:///?blah=hello"))
			.renameKey(k -> LogProperties.removeKeyPrefix(k, LogProperties.ROOT_PREFIX))
			.build();
		String actual = Property.builder().build("logging.blah").get(props).value();
		assertEquals("hello", actual);

	}

}
