package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import io.jstach.rainbowgum.benchmark.nativeimage.BenchServer;

/**
 * Rainbow Gum side of the GraalVM native image benchmark. Deliberately zero configuration
 * for the default (TTLL) scenario: depending on {@code rainbowgum-slf4j} is the entire
 * setup - console output and the TTLL format are Rainbow Gum's own zero-config defaults
 * (see {@code LogEncoder#ofTTLL()}).
 * <p>
 * {@code STRUCTURED_FORMAT=gelf} switches the console appender's encoder to
 * {@code rainbowgum-json}'s {@code GelfEncoder} instead - set as a plain system property
 * before the first logger is created (Rainbow Gum's property lookups are lazy/runtime,
 * not frozen at native-image build time the way {@code com.sun.net.httpserver}'s own
 * config happens to be), so no separate native image is needed for this scenario.
 */
public final class App {

	private App() {
	}

	/**
	 * Entry point.
	 * @param args ignored; port defaults to 8080 unless {@code PORT} is set,
	 * {@code STRUCTURED_FORMAT=gelf} switches to GELF output.
	 * @throws Exception if the server fails to start.
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
		if ("gelf".equals(System.getenv("STRUCTURED_FORMAT"))) {
			System.setProperty("logging.appender.console.encoder", "gelf:///?host=benchmark-host");
		}
		BenchServer.startAndAwait(port);
	}

}
