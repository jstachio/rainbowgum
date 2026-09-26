package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.annotation.RainbowGumVersion;

class RainbowGumVersionReaderTest {

	@Test
	void testVersionMatchesAnnotationModuleWhenPresent() {
		assertEquals(RainbowGumVersion.VERSION, RainbowGumVersionReader.version());
	}

}
