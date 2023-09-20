package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.RainbowGum;

class RainbowGumLoggerFactoryTest {

	@Test
	void testGetLogger() {
		RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(RainbowGum.builder().build());
		var logger = factory.getLogger("crap");
		logger.trace("asdfasdf");
	}

}
