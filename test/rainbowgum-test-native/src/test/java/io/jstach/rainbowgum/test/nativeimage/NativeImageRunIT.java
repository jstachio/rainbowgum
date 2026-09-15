package io.jstach.rainbowgum.test.nativeimage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

/*
 * Runs in a normal JVM, well after (and completely unrelated to) the native executable
 * this module's `native` profile builds directly through native-maven-plugin's
 * compile-no-fork goal (see pom.xml), bound to the package phase. This class, bound to
 * maven-failsafe-plugin's integration-test/verify goals, launches that already-built
 * executable itself with a plain ProcessBuilder and checks its output: a black-box smoke
 * test, deliberately. No JUnit runs inside the native image itself.
 */
class NativeImageRunIT {

	@Test
	void testNativeImageRunProducesExpectedOutput() throws IOException, InterruptedException {
		Path executable = Path.of(System.getProperty("native.image.executable"));
		Process process = new ProcessBuilder(executable.toString()).redirectErrorStream(true).start();
		String actual = new String(process.getInputStream().readAllBytes());
		boolean finished = process.waitFor(30, TimeUnit.SECONDS);
		assertTrue(finished, () -> "native image process did not finish in time, output so far: " + actual);
		assertTrue(process.exitValue() == 0, () -> "native image process exited " + process.exitValue()
				+ ", expected 0. Actual output was: " + actual);
		String expected = "INFO native.image.smoke.test Hello from a GraalVM native image!\n";
		assertTrue(actual.contains(expected),
				() -> "expected output to contain: " + expected + "\nactual output was: " + actual);
	}

}
