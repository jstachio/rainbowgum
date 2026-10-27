package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class LogOutputRegistryTest {

	@Test
	void absoluteFileUriIsUnchanged() {
		assertEquals("file:///tmp/app.log", normalize("file:///tmp/app.log"));
	}

	@Test
	void fileUriKeepsQuery() {
		assertEquals("file:///tmp/app.log?prudent=true", normalize("file:///tmp/app.log?prudent=true"));
	}

	@Test
	void fileUriKeepsEncodedCharacters() {
		assertEquals("file:///tmp/my%20logs/app.log", normalize("file:///tmp/my%20logs/app.log"));
	}

	@Test
	void dotFileUriIsRelativeToTheWorkingDirectory() {
		assertEquals(Path.of("./app.log").toUri().toString(), normalize("file:///./app.log"));
	}

	@Test
	void fileUriFromPathRoundTrips() {
		/*
		 * Path.toUri() is how a Windows path becomes file:///C:/..., the form that used
		 * to fail there.
		 */
		var uri = Path.of("logs", "app.log").toAbsolutePath().toUri();
		assertEquals(uri.toString(), normalize(uri.toString()));
	}

	private static String normalize(String uri) {
		return DefaultOutputRegistry.normalize(URI.create(uri)).toString();
	}

}
