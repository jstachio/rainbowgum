package io.jstach.rainbowgum.benchmark.nativeimage.log4j2;

import io.jstach.rainbowgum.benchmark.nativeimage.BenchServer;

/**
 * Log4j2 side of the GraalVM native image benchmark. Configuration is {@code log4j2.xml}
 * on the classpath (see {@code src/main/resources}): a Console appender with a pattern
 * matching Rainbow Gum's TTLL layout field-for-field
 * ({@code Time Thread Level Logger - message}), so the log output shape is the same
 * across every app in this benchmark.
 */
public final class App {

	private App() {
	}

	/**
	 * Entry point.
	 * @param args ignored; port defaults to 8080 unless {@code PORT} is set.
	 * @throws Exception if the server fails to start.
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
		BenchServer.startAndAwait(port);
	}

}
