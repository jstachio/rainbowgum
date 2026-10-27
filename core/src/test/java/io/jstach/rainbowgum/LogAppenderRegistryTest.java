package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class LogAppenderRegistryTest {

	@Test
	void fileNameUriKeepsUris() throws URISyntaxException {
		assertEquals("file:///tmp/app.log", fileNameUri("file:///tmp/app.log"));
		assertEquals("rolling:///C:/logs/app.log", fileNameUri("rolling:///C:/logs/app.log"));
	}

	@Test
	void fileNameUriKeepsRelativePathsForNormalizing() throws URISyntaxException {
		assertEquals("logs/app.log", fileNameUri("logs/app.log"));
	}

	@Test
	void fileNameUriTreatsWindowsPathsAsPaths() throws URISyntaxException {
		for (String path : new String[] { "C:/logs/app.log", "C:\\logs\\app.log", "logs\\app.log" }) {
			assertEquals(Path.of(path).toUri().toString(), fileNameUri(path));
		}
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void fileNameUriOfWindowsPath() throws URISyntaxException {
		assertEquals("file:///C:/logs/app.log", fileNameUri("C:/logs/app.log"));
		assertEquals("file:///C:/logs/app.log", fileNameUri("C:\\logs\\app.log"));
	}

	private static String fileNameUri(String value) throws URISyntaxException {
		return DefaultAppenderRegistry.fileNameUri(value).toString();
	}

}
