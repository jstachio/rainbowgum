package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.Internal.LineSeparator;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

class LineSeparatorTest {

	@Test
	void defaultIsLfOnEveryPlatform() {
		assertEquals("\n", LogFormatter.lineSeparator());
	}

	@Test
	void valuesSelectTheirSeparator() {
		assertEquals("\n", LogProperty.enumValue(LineSeparator.class, "lf").separator);
		assertEquals("\r\n", LogProperty.enumValue(LineSeparator.class, "crlf").separator);
		assertEquals(System.lineSeparator(), LogProperty.enumValue(LineSeparator.class, "platform").separator);
	}

	@Test
	void stackTraceLinesEndWithTheLineSeparator() {
		var output = new StringBuilder();
		ThrowableFormatter.appendThrowable(output, new RuntimeException("boom"));
		String trace = output.toString();
		assertTrue(trace.startsWith("java.lang.RuntimeException: boom\n\tat "), trace);
		assertFalse(trace.contains("\r"), trace);
	}

}
