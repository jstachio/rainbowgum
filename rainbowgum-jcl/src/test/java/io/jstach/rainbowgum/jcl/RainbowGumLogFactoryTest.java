package io.jstach.rainbowgum.jcl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RainbowGumLogFactoryTest {

	@Test
	void testGetInstanceReturnsRainbowGumLog() {
		var factory = new RainbowGumLogFactory();
		assertInstanceOf(RainbowGumLog.class, factory.getInstance("some.logger.name"));
		assertInstanceOf(RainbowGumLog.class, factory.getInstance(RainbowGumLogFactoryTest.class));
	}

	@Test
	void testAttributesRoundTrip() {
		var factory = new RainbowGumLogFactory();
		assertNull(factory.getAttribute("missing"));
		assertArrayEquals(new String[0], factory.getAttributeNames());

		factory.setAttribute("a", "b");
		assertArrayEquals(new String[] { "a" }, factory.getAttributeNames());
		assertNull(factory.getAttribute("missing"));

		factory.setAttribute("a", null);
		assertArrayEquals(new String[0], factory.getAttributeNames());

		factory.setAttribute("a", "b");
		factory.removeAttribute("a");
		assertArrayEquals(new String[0], factory.getAttributeNames());
	}

}
