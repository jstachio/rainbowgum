package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AnsiSupportTest {

	@Test
	void testNoColorDisables() {
		assertFalse(AnsiSupport.isAnsiSupported("1", null));
		assertFalse(AnsiSupport.isAnsiSupported("", "xterm"));
	}

	@Test
	void testDumbTermDisables() {
		assertFalse(AnsiSupport.isAnsiSupported(null, "dumb"));
	}

	@Test
	void testSupportedByDefault() {
		assertTrue(AnsiSupport.isAnsiSupported(null, "xterm-256color"));
		assertTrue(AnsiSupport.isAnsiSupported(null, null));
	}

}
