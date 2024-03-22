package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogFormatterTest {

	@Test
	@SuppressWarnings("StringSplitter")
	void testThrowable() {
		Throwable t = new RuntimeException("expected");
		StringBuilder sb = new StringBuilder();
		var event = TestEventBuilder.of().build(e -> e.throwable(t));
		LogFormatter.builder().throwable().build().format(sb, event);
		String actual = sb.toString().split("\n")[0];
		assertEquals("java.lang.RuntimeException: expected", actual);
	}

}
