package io.jstach.rainbowgum.test.aotcache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/*
 * Runs in its own, normal JVM, well after (and completely unrelated to) the AOT cache
 * itself, which this module's `aot` profile builds directly through exec-maven-plugin
 * (see pom.xml): "aot-cache-create" (trains and assembles the cache) at the package
 * phase, per JEP 483's own -XX:AOTCacheOutput shortcut. This class, bound to
 * maven-failsafe-plugin's integration-test/verify goals, launches java itself with a
 * plain ProcessBuilder, using -XX:AOTMode=on (so a failure to use the cache is a hard
 * error, not a silent fallback) plus the already-built cache: a black-box smoke test,
 * deliberately. It plays no part in the cache itself, per JEP 483's own warning against
 * including a rich test framework in the classes an AOT cache actually trains against.
 */
class AotCacheRunIT {

	@Test
	void testAotCacheRunProducesExpectedOutput() throws IOException, InterruptedException {
		String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
		String cacheFile = System.getProperty("aot.cache.file");
		String classpath = System.getProperty("aot.classpath");
		String mainClass = System.getProperty("aot.main.class");
		Process process = new ProcessBuilder(javaBinary, "-XX:AOTMode=on", "-XX:AOTCache=" + cacheFile, "-cp",
				classpath, mainClass)
			.redirectErrorStream(true)
			.start();
		String actual = new String(process.getInputStream().readAllBytes());
		boolean finished = process.waitFor(30, TimeUnit.SECONDS);
		assertTrue(finished, () -> "AOT cache run did not finish in time, output so far: " + actual);
		assertTrue(process.exitValue() == 0,
				() -> "AOT cache run exited " + process.exitValue() + ", expected 0. Actual output was: " + actual);
		String expected = "INFO aot.cache.test Hello from the JDK AOT cache!\n";
		assertTrue(actual.contains(expected),
				() -> "expected output to contain: " + expected + "\nactual output was: " + actual);
	}

}
