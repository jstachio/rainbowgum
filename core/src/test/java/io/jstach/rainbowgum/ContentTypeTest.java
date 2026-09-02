package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;

/*
 * Moved into io.jstach.rainbowgum (from io.jstach.rainbowgum.output) so this can
 * construct SimpleContentType directly - it's package-private now (see the "minimize
 * public API" note), not something outside this package should ever need to touch.
 */
class ContentTypeTest {

	@Test
	void textPlainIsUtf8() {
		assertEquals(StandardCharsets.UTF_8, StandardContentType.TEXT_PLAIN.charsetOrNull());
	}

	@Test
	void applicationJsonIsAlwaysUtf8() {
		assertEquals(StandardCharsets.UTF_8, StandardContentType.APPLICATION_JSON.charsetOrNull());
	}

	@Test
	void ofReturnsTheCanonicalStandardContentTypeWhenItMatches() {
		assertSame(StandardContentType.TEXT_PLAIN, ContentType.of("text/plain", StandardCharsets.UTF_8));
		assertSame(StandardContentType.APPLICATION_JSON, ContentType.of("application/json", StandardCharsets.UTF_8));
	}

	@Test
	void ofCreatesASimpleContentTypeWhenNothingMatches() {
		var contentType = ContentType.of("text/plain", StandardCharsets.ISO_8859_1);
		assertEquals(new SimpleContentType("text/plain", StandardCharsets.ISO_8859_1), contentType);
		assertEquals("text/plain", contentType.contentType());
		assertEquals(StandardCharsets.ISO_8859_1, contentType.charsetOrNull());
	}

	@Test
	void ofDoesNotMatchApplicationJsonWithAWrongCharset() {
		var contentType = ContentType.of("application/json", StandardCharsets.ISO_8859_1);
		assertEquals(new SimpleContentType("application/json", StandardCharsets.ISO_8859_1), contentType);
	}

}
