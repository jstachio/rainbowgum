package io.jstach.rainbowgum.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.ValidationException;

/*
 * logging.publisher.{name}.bufferSize (DisruptorLogBuilder's only property) had zero
 * test coverage of any kind before this - not even a happy path - found by grepping the
 * property list ConfigProcessor#PROPERTY_LIST_OPTION generates against the test tree.
 */
class DisruptorConfiguratorTest {

	@Test
	void testBadBufferSizeFailsLoudlyWithPropertyDescription() {
		var properties = LogProperties.builder()
			.fromProperties("logging.publisher.async.bufferSize=notanumber")
			.build();
		var e = assertThrows(ValidationException.class,
				() -> new DisruptorLogBuilder("async").fromProperties(properties).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.disruptor.DisruptorLogBuilder:
						Error for property. key: 'logging.publisher.async.bufferSize' from PROPERTIES_STRING[logging.publisher.async.bufferSize], java.lang.NumberFormatException For input string: "notanumber\"""",
				e.getMessage());
	}

	@Test
	void testValidBufferSizeOverrideDoesNotThrow() {
		var properties = LogProperties.builder().fromProperties("logging.publisher.async.bufferSize=64").build();
		new DisruptorLogBuilder("async").fromProperties(properties).build();
	}

	@Test
	void testMissingBufferSizeFallsBackToDefault() {
		var properties = LogProperties.builder().fromProperties("").build();
		new DisruptorLogBuilder("async").fromProperties(properties).build();
	}

}
