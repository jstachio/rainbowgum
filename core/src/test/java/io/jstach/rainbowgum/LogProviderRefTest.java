package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

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
		var ref = LogProviderRef.of(URI.create("console:///"), "logging.appender.default.output.uri");
		assertEquals(URI.create("console:///"), ref.uri());
		assertEquals("logging.appender.default.output.uri", ref.keyOrNull());
	}

	@Test
	void testOfSuccessResultCapturesKeyAndValue() {
		LogProperties properties = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.out", "console:///");
		Result<URI> result = properties.forKey("logging.out").ofURI();
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
	void testNotFoundExceptionOfUnknownSchemeHasNoModuleHint() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.OUTPUT, "bogus",
				URI.create("bogus:///"), List.of());
		assertEquals("No output found. Scheme not registered. scheme: 'bogus', URI: 'bogus:///'", e.getMessage());
	}

	@Test
	void testNotFoundExceptionOfKnownSchemeButWrongComponentTypeHasNoModuleHint() {
		// "gelf" is only registered as an encoder scheme, not an output scheme.
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.OUTPUT, "gelf", URI.create("gelf:///"),
				List.of());
		assertEquals("No output found. Scheme not registered. scheme: 'gelf', URI: 'gelf:///'", e.getMessage());
	}

	@Test
	void testNotFoundExceptionOfKnownSchemeHasModuleHint() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.ENCODER, "gelf",
				URI.create("gelf:///"), List.of());
		String v = RainbowGumVersion.VERSION;
		assertEquals("No encoder found. Scheme not registered. scheme: 'gelf', URI: 'gelf:///'. "
				+ "Scheme 'gelf' is provided by module 'io.jstach.rainbowgum.json' "
				+ "(Maven: 'io.jstach.rainbowgum:rainbowgum-json:" + v + "'). Add that dependency. "
				+ "See https://jstach.io/doc/rainbowgum/" + v + "/apidocs/index.html#gelf", e.getMessage());
	}

	@Test
	void testNotFoundExceptionOfJfrOutputHasModuleHint() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.OUTPUT, "jfr", URI.create("jfr:///"),
				List.of());
		String v = RainbowGumVersion.VERSION;
		assertEquals("No output found. Scheme not registered. scheme: 'jfr', URI: 'jfr:///'. "
				+ "Scheme 'jfr' is provided by module 'io.jstach.rainbowgum.jfr' "
				+ "(Maven: 'io.jstach.rainbowgum:rainbowgum-jfr:" + v + "'). Add that dependency. "
				+ "See https://jstach.io/doc/rainbowgum/" + v + "/apidocs/index.html#jfr", e.getMessage());
	}

	@Test
	void testNotFoundExceptionWithoutOverviewSectionLinksModulePage() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.PUBLISHER, "disruptor",
				URI.create("disruptor:///"), List.of());
		String v = RainbowGumVersion.VERSION;
		assertEquals("No publisher found. Scheme not registered. scheme: 'disruptor', URI: 'disruptor:///'. "
				+ "Scheme 'disruptor' is provided by module 'io.jstach.rainbowgum.disruptor' "
				+ "(Maven: 'io.jstach.rainbowgum:rainbowgum-disruptor:" + v + "'). Add that dependency. "
				+ "See https://jstach.io/doc/rainbowgum/" + v
				+ "/apidocs/io.jstach.rainbowgum.disruptor/module-summary.html", e.getMessage());
	}

	@Test
	void testNotFoundExceptionSuggestsASimilarlySpelledRegisteredScheme() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.PUBLISHER, "asyc",
				URI.create("asyc:///"), List.of("sync", "async", "default", "batchswap"));
		assertEquals("""
				No publisher found. Scheme not registered. scheme: 'asyc', URI: 'asyc:///'. Did you mean 'async'? \
				Registered publisher schemes: 'async', 'batchswap', 'default', 'sync'""", e.getMessage());
	}

	@Test
	void testNotFoundExceptionSuggestsASimilarlySpelledModuleScheme() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.ENCODER, "patern",
				URI.create("patern:///"), List.of("ttll", "json5", "logfmt"));
		String v = RainbowGumVersion.VERSION;
		assertEquals("""
				No encoder found. Scheme not registered. scheme: 'patern', URI: 'patern:///'. Did you mean 'pattern'? \
				Registered encoder schemes: 'json5', 'logfmt', 'ttll'. \
				Scheme 'pattern' is provided by module 'io.jstach.rainbowgum.pattern' \
				(Maven: 'io.jstach.rainbowgum:rainbowgum-pattern:VERSION'). Add that dependency. \
				See https://jstach.io/doc/rainbowgum/VERSION/apidocs/index.html#pattern""".replace("VERSION", v),
				e.getMessage());
	}

	@Test
	void testNotFoundExceptionListsRegisteredSchemesWhenNoneIsSimilarlySpelled() {
		var e = LogProviderRef.NotFoundException.of(ProviderModule.ComponentType.OUTPUT, "xyz", URI.create("xyz:///"),
				List.of("stdout", "stderr", "list"));
		assertEquals("""
				No output found. Scheme not registered. scheme: 'xyz', URI: 'xyz:///'. \
				Registered output schemes: 'list', 'stderr', 'stdout'""", e.getMessage());
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
		var ref = LogProviderRef.of(URI.create("console"), "logging.appender.default.output.uri");
		var normalized = DefaultLogProviderRef.normalize(ref);
		assertEquals(URI.create("console:///"), normalized.uri());
		assertEquals("logging.appender.default.output.uri", normalized.keyOrNull());
	}

}
