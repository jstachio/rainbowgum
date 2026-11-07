package io.jstach.rainbowgum.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Pass;

/*
 * logging.route.{name}.publisher.{scheme}.bufferSize (DisruptorLogBuilder's only property) had zero
 * test coverage of any kind before this - not even a happy path - found by grepping the
 * property list ConfigProcessor#PROPERTY_LIST_OPTION generates against the test tree.
 */
class DisruptorConfiguratorTest {

	@Test
	void configuringDisruptorPreservesTheAsyncProvider() {
		var config = LogConfig.builder().build();
		PublisherFactory async = PublisherFactory.async().build();
		config.publisherRegistry().register("async", ref -> async);
		new DisruptorConfigurator().configure(config, Pass.FIRST);
		assertSame(async, config.publisherRegistry().provide(LogProviderRef.of(URI.create("async:///"))));
	}

	@Test
	void testBadBufferSizeFailsLoudlyWithPropertyDescription() {
		var properties = LogProperties.builder()
			.fromProperties("logging.route.default.publisher.disruptor.bufferSize=notanumber")
			.build();
		var e = assertThrows(ValidationException.class,
				() -> new DisruptorLogBuilder("default", "disruptor").fromProperties(properties).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.disruptor.DisruptorLogBuilder:
						Error for property. key: 'logging.route.default.publisher.disruptor.bufferSize' from PROPERTIES_STRING[logging.route.default.publisher.disruptor.bufferSize], java.lang.NumberFormatException For input string: "notanumber\"""",
				e.getMessage());
	}

	@Test
	void testValidBufferSizeOverrideDoesNotThrow() {
		var properties = LogProperties.builder()
			.fromProperties("logging.route.default.publisher.disruptor.bufferSize=64")
			.build();
		new DisruptorLogBuilder("default", "disruptor").fromProperties(properties).build();
	}

	@Test
	void testMissingBufferSizeFallsBackToDefault() {
		var properties = LogProperties.builder().fromProperties("").build();
		new DisruptorLogBuilder("default", "disruptor").fromProperties(properties).build();
	}

}
