package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;

class LogPropertiesTest {

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
