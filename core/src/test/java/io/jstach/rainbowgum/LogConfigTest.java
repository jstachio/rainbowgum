package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

class LogConfigTest {

	@Test
	void test() {
		System.setProperty("rainbowgum.log.stuff", "DEBUG");

		var resolver = LogConfig.of().levelResolver();
		var actual = resolver.resolveLevel("stuff");

		assertEquals(Level.DEBUG, actual);

		assertTrue(LogConfig.of().levelResolver().isEnabled("stuff", Level.DEBUG));
	}

}
