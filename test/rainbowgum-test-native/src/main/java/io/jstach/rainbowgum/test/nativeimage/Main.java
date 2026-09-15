package io.jstach.rainbowgum.test.nativeimage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compiled directly into the native executable by this module's {@code native} profile
 * (see its pom.xml, {@code native-maven-plugin}'s {@code compile-no-fork} goal), then run
 * as a plain subprocess with its stdout captured to a file. No JUnit runs inside the
 * native image itself: {@code native-maven-plugin}'s own {@code test} goal (compiling
 * JUnit's own launcher/engine into the image) was tried first and dropped, since it
 * bundles its own, much older JUnit Platform jars that clash with this project's, and
 * separately trips a GraalVM build-time-initialization error in {@code rainbowgum-jdk}'s
 * {@code SystemLoggingFactory}, triggered by the native JUnit runner's own
 * progress-reporting code, not by anything this module's own code does.
 */
public final class Main {

	private Main() {
	}

	/**
	 * Entry point.
	 * @param args unused.
	 */
	public static void main(String[] args) {
		Logger log = LoggerFactory.getLogger("native.image.smoke.test");
		log.info("Hello from a GraalVM native image!");
	}

}
