package io.jstach.rainbowgum.test.aotcache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/*
 * Runs in its own JVM, well after (and completely unrelated to) the two subprocess runs
 * of Main that this module's `aot` profile drives directly through exec-maven-plugin
 * (see pom.xml): "aot-cache-create" (trains and assembles the AOT cache) at the package
 * phase, then "aot-cache-run" (actually loads it, with -XX:AOTMode=on so a failure to
 * use the cache is a hard error, not a silent fallback) at the pre-integration-test
 * phase, with its stdout captured to aot.run.output. This class, bound to maven-failsafe
 * -plugin's integration-test/verify goals, only reads that captured file back and
 * asserts on it; it plays no part in the cache itself, deliberately, since JEP 483 warns
 * against including a rich test framework in the classes an AOT cache actually trains
 * against.
 */
class AotCacheRunIT {

	@Test
	void testAotCacheRunProducedExpectedOutput() throws IOException {
		Path outputFile = Path.of(System.getProperty("aot.run.output"));
		String actual = Files.readString(outputFile);
		String expected = "INFO aot.cache.test Hello from the JDK AOT cache!\n";
		assertTrue(actual.contains(expected),
				() -> "expected " + outputFile + " to contain: " + expected + "\nactual content was: " + actual);
	}

}
