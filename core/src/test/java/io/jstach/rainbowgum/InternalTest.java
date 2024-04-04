package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InternalTest {

	@Test
	void testWriteCharArray() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			w.write("hello".toCharArray(), 0, 4);
			w.flush();
		}
		String expected = "hell"; // hell is testing bullshit like this.
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	@Test
	void testWriteCharArrayBadBounds() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			assertThrows(IndexOutOfBoundsException.class, () -> {
				w.write("hello".toCharArray(), 0, -1);
			});
		}
	}

	@Test
	void testStringPringWriter() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			w.write("hello", 0, 4);
			w.flush();
		}
		String expected = "hell";
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	@Test
	void testStringPringWriterAppendChar() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			w.append('h');
			w.flush();
		}
		String expected = "h";
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	@Test
	void testStringPringWriterAppend() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			w.append("hello");
			w.flush();
		}
		String expected = "hello";
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

	@Test
	void testStringPringWriterAppendRange() {
		StringBuilder sb = new StringBuilder();
		try (var w = Internal.StringBuilderPrintWriter.of(sb)) {
			w.append("hello", 0, 4);
			w.flush();
		}
		String expected = "hell";
		String actual = sb.toString();
		assertEquals(expected, actual);
	}

}
