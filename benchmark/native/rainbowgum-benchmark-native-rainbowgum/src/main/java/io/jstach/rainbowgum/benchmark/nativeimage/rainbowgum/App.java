package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import io.jstach.rainbowgum.benchmark.nativeimage.BenchServer;

/**
 * Rainbow Gum side of the GraalVM native image benchmark. Deliberately zero
 * configuration: depending on {@code rainbowgum-slf4j} is the entire setup - console
 * output and the TTLL format are Rainbow Gum's own zero-config defaults (see
 * {@code LogEncoder#ofTTLL()}), so there is nothing else for this class to do.
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
