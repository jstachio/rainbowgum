package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.Result;

class LogProviderRefTest {

	@Test
	void testOfUriHasNullKey() {
		var ref = LogProviderRef.of(URI.create("console:///"));
		assertEquals(URI.create("console:///"), ref.uri());
		assertNull(ref.keyOrNull());
	}

	@Test
	void testOfUriAndKey() {
		var ref = LogProviderRef.of(URI.create("console:///"), "logging.appender.default.output");
		assertEquals(URI.create("console:///"), ref.uri());
		assertEquals("logging.appender.default.output", ref.keyOrNull());
	}

	@Test
	void testOfSuccessResultCapturesKeyAndValue() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.out", "console:///");
		Property<URI> property = Property.builder().ofURI().build("logging.out");
		Result<URI> result = property.get(properties);
		if (!(result instanceof Result.Success<URI> success)) {
			throw new AssertionError("expected a successful result");
		}
		var ref = LogProviderRef.of(success);
		assertEquals(URI.create("console:///"), ref.uri());
		assertEquals("logging.out", ref.keyOrNull());
	}

	@Test
	void testNotFoundExceptionMessage() {
		var e = new LogProviderRef.NotFoundException("no provider for scheme");
		assertEquals("no provider for scheme", e.getMessage());
	}

	@Test
	void testNormalizeUriPassesThroughWhenSchemeAlreadyPresent() {
		var uri = URI.create("console:///?a=b");
		assertEquals(uri, DefaultLogProviderRef.normalize(uri));
	}

	@Test
	void testNormalizeUriTreatsBareWordAsScheme() {
		var uri = URI.create("console");
		assertEquals(URI.create("console:///"), DefaultLogProviderRef.normalize(uri));
	}

	@Test
	void testNormalizeUriRejectsPathThatCannotBecomeAScheme() {
		// A scheme-less absolute or relative path (e.g. "/foo" or "./foo") cannot be
		// turned into a "<path>:///" URI since "/" and "." are not valid scheme
		// characters, and a leading "/" or "." also keeps the URI parser from ever
		// attempting scheme detection - so appending ":///" would otherwise silently
		// produce another scheme-less, unresolvable URI instead of throwing. This used
		// to be special-cased into a "name://" URI to reference another named
		// component's config, but that was never actually wired up to look anything up.
		// Removed; this now fails fast with a clear error instead. See todo.md.
		var absolute = URI.create("/foo");
		assertThrows(IllegalArgumentException.class, () -> DefaultLogProviderRef.normalize(absolute));

		var relative = URI.create("./foo");
		assertThrows(IllegalArgumentException.class, () -> DefaultLogProviderRef.normalize(relative));
	}

	@Test
	void testNormalizeUriRejectsPathWithIllegalSchemeCharacters() {
		// A scheme-less path with no leading "/" is a candidate for becoming the scheme
		// itself, but this one has a space in it, which is not a legal scheme
		// character - so URI construction throws URISyntaxException, which normalize()
		// wraps as IllegalArgumentException.
		var uri = URI.create("hello%20world");
		assertThrows(IllegalArgumentException.class, () -> DefaultLogProviderRef.normalize(uri));
	}

	@Test
	void testNormalizeLogProviderRefPreservesKeyAndNormalizesUri() {
		var ref = LogProviderRef.of(URI.create("console"), "logging.appender.default.output");
		var normalized = DefaultLogProviderRef.normalize(ref);
		assertEquals(URI.create("console:///"), normalized.uri());
		assertEquals("logging.appender.default.output", normalized.keyOrNull());
	}

}
