package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LogConfigTest {

	@Test
	void test() {
		// System.setProperty("rainbowgum.log.stuff", "DEBUG");

		var config = LogConfig.builder()
			.properties(Map.<String, String>of("logging.level.stuff", "DEBUG")::get)
			.build();
		var resolver = config.levelResolver();
		var actual = resolver.resolveLevel("stuff");

		assertEquals(Level.DEBUG, actual);
	}

}
