package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.ContentType.DefaultContentType;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;

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
	void ofCreatesADefaultContentTypeWhenNothingMatches() {
		var contentType = ContentType.of("text/plain", StandardCharsets.ISO_8859_1);
		assertEquals(new DefaultContentType("text/plain", StandardCharsets.ISO_8859_1), contentType);
		assertEquals("text/plain", contentType.contentType());
		assertEquals(StandardCharsets.ISO_8859_1, contentType.charsetOrNull());
	}

	@Test
	void ofDoesNotMatchApplicationJsonWithAWrongCharset() {
		var contentType = ContentType.of("application/json", StandardCharsets.ISO_8859_1);
		assertEquals(new DefaultContentType("application/json", StandardCharsets.ISO_8859_1), contentType);
	}

}
