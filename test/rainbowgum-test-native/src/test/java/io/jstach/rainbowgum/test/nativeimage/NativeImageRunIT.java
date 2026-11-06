package io.jstach.rainbowgum.test.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/*
 * Runs in a normal JVM, well after (and completely unrelated to) the native executable
 * this module's `native` profile builds directly through native-maven-plugin's
 * compile-no-fork goal (see pom.xml), bound to the package phase. This class, bound to
 * maven-failsafe-plugin's integration-test/verify goals, launches that already-built
 * executable itself with a plain ProcessBuilder and checks its output: a black-box smoke
 * test, deliberately. No JUnit runs inside the native image itself.
 */
class NativeImageRunIT {

	@TempDir
	Path tempDir;

	@Test
	void testNativeImageRunProducesExpectedOutput() throws IOException, InterruptedException {
		String actual = run();
		String expected = "INFO native.image.smoke.test Hello from a GraalVM native image!\n";
		assertTrue(actual.contains(expected),
				() -> "expected output to contain: " + expected + "\nactual output was: " + actual);
		if (System.getProperty("native.test.api").equals("systemlogger")) {
			assertTrue(actual.contains("INFO native.image.jul.test Hello from JUL!\n"), () -> actual);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "INFO", "DEBUG", "ERROR" })
	void testLevelComesFromRuntimeProperties(String level) throws IOException, InterruptedException {
		String actual = run("-Dlogging.level=" + level);
		assertEquals(!level.equals("ERROR"),
				actual.contains("INFO native.image.smoke.test Hello from a GraalVM native image!\n"), () -> actual);
		assertEquals(level.equals("DEBUG"), actual.contains("DEBUG native.image.smoke.test Runtime debug logging\n"),
				() -> actual);
	}

	private String run(String... args) throws IOException, InterruptedException {
		Path executable = Path.of(System.getProperty("native.image.executable"));
		var command = new ArrayList<String>();
		command.add(executable.toString());
		command.addAll(List.of(args));
		Path output = Files.createTempFile(tempDir, "native-image-", ".log");
		Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
		boolean finished;
		try {
			finished = process.waitFor(30, TimeUnit.SECONDS);
		}
		finally {
			if (process.isAlive()) {
				process.destroyForcibly().waitFor(5, TimeUnit.SECONDS);
			}
		}
		String actual = Files.readString(output);
		assertTrue(finished, () -> "native image process did not finish in time, output so far: " + actual);
		assertEquals(0, process.exitValue(), () -> "native image failed: " + actual);
		return actual;
	}

}
