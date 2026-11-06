package io.jstach.rainbowgum.test.nativeimage;

import io.jstach.rainbowgum.RainbowGum;

/**
 * Native smoke test with no SLF4J on the classpath. System.Logger must discover its
 * finder and start Rainbow Gum itself, including the JUL configurator.
 */
public final class Main {

	private Main() {
	}

	/**
	 * Entry point.
	 * @param args unused.
	 */
	public static void main(String[] args) {
		var log = System.getLogger("native.image.smoke.test");
		if (RainbowGum.getOrNull() == null) {
			throw new IllegalStateException("System.Logger did not initialize Rainbow Gum");
		}
		log.log(System.Logger.Level.INFO, "Hello from a GraalVM native image!");
		log.log(System.Logger.Level.DEBUG, "Runtime debug logging");
		java.util.logging.Logger.getLogger("native.image.jul.test").info("Hello from JUL!");
	}

}
