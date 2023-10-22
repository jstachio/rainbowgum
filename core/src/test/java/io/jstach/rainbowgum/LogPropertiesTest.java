package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties.PropertyValue;

class LogPropertiesTest {

	@Test
	void testValue() {
		Map<String, String> m = Map.of("logging.level.com.stuff", "DEBUG");
		LogProperties props = s -> m.get(s);
		var extractor = ConfigLevelResolver.levelExtractor;
		var property = extractor.property("com.stuff");
		// assertEquals(Level.ALL, property.require(props));
		PropertyValue<Level> value = props.property(property);
		value.value();
	}

}
