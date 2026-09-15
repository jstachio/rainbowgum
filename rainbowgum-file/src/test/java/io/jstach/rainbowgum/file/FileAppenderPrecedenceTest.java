package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/**
 * {@code "file"} is one of the two well known appender names
 * {@code DefaultAppenderRegistry} (core) gives a default output to when nothing is
 * configured; unlike {@code "console"}'s stdout default (a plain fallback, only used if
 * the generic appender output property is absent, see core's own
 * {@code DefaultAppenderPrecedenceTest}), {@link LogProperties#FILE_PROPERTY} (Spring
 * Boot's {@code logging.file.name}) is meant to outrank the generic
 * {@code logging.appender.file.output} outright, even when both are present. This
 * precedence needs the real {@code file:} scheme this module registers, which is why it
 * lives here rather than alongside {@code "console"}'s own version in core: proven by
 * logging a real message and asserting it landed in {@code logging.file.name}'s own path,
 * not by inspecting internals only core's own package can see. Had no test before the
 * appender-construction refactor that unified property-driven and programmatic appender
 * construction onto one {@code LogAppender.Builder} path.
 */
class FileAppenderPrecedenceTest {

	@Test
	void fileNamePropertyOutranksGenericAppenderOutputProperty(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("rainbowgum-precedence-test.log");
		var properties = LogProperties.builder().fromProperties("""
				logging.appenders=file
				logging.file.name=%s
				logging.appender.file.output=list:///
				""".formatted(file)).build();
		var config = LogConfig.builder().properties(properties).serviceLoader().build();
		var gum = RainbowGum.builder(config).build();
		try (var rg = gum.start()) {
			rg.log(TestLogEventFactory.of("test").eventNoArg(Level.INFO, "hello", KeyValues.of(), (Throwable) null));
		}
		assertTrue(Files.exists(file), () -> "expected logging.file.name's own path to have been used: " + file);
		String content = Files.readString(file);
		assertTrue(content.contains("hello"),
				() -> "expected logging.file.name to outrank logging.appender.file.output, file content was: "
						+ content);
	}

}
