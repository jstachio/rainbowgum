package io.jstach.rainbowgum.pattern;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PaddingTest {

	@Test
	void failEndingInPeriod() {
		var e = assertThrows(IllegalArgumentException.class, () -> {
			Padding.valueOf(".");
		});
		assertEquals("Formatting string [.] should not end with '.'", e.getMessage());
	}

	@Test
	void testNegativeMax() {
		StringBuilder b = new StringBuilder();
		Padding.valueOf(".-10").format(b, "a");
		String actual = b.toString();
		assertEquals("a", actual);
	}

	@Test
	void testNegativeMin() {
		StringBuilder b = new StringBuilder();
		Padding.valueOf("-10").format(b, "a");
		String actual = b.toString();
		assertEquals("a         ", actual);
	}

	@Test
	void testPositiveMinNegativeMax() {
		StringBuilder b = new StringBuilder();
		Padding.valueOf("10.-10").format(b, "a");
		String actual = b.toString();
		assertEquals("         a", actual);
	}

	@Test
	void testFormat() {
		StringBuilder b = new StringBuilder();
		Padding.of(5, 10).format(b, "a");
		assertEquals("    a", b.toString());
		b.setLength(0);
		Padding.of(5, 10).format(b, "");
		assertEquals("", b.toString());

		b.setLength(0);
		Padding.of(-5, -10).format(b, "");
		assertEquals("", b.toString());

	}

}
