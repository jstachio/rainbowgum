package io.jstach.rainbowgum.benchmark.nativeimage.logback;

import io.jstach.rainbowgum.benchmark.nativeimage.BenchServer;

/**
 * Logback side of the GraalVM native image benchmark. Configuration is
 * {@code logback.xml} on the classpath (see {@code src/main/resources}): a
 * ConsoleAppender with a pattern matching Rainbow Gum's TTLL layout field-for-field
 * ({@code Time Thread Level Logger - message}), so the log output shape is the same
 * across every app in this benchmark.
 * <p>
 * {@code STRUCTURED_FORMAT=gelf} switches to {@code logback-json.xml} instead, which uses
 * the third-party {@code logstash-logback-encoder} (Logback has no first-party structured
 * logging support at all) - Logstash-format JSON, not GELF, since that is Logback's own
 * idiomatic choice rather than forcing GELF parity with the other two apps in this
 * benchmark.
 * <p>
 * {@code LOG_LEVEL} overrides the root level (both config files substitute
 * {@code ${bench.log.level:-INFO}} for the {@code root} level) - e.g.
 * {@code LOG_LEVEL=ERROR} for a "mostly off" baseline, since none of
 * {@code BenchHandler}'s calls are above {@code INFO}.
 */
public final class App {

	private App() {
	}

	/**
	 * Entry point.
	 * @param args ignored; port defaults to 8080 unless {@code PORT} is set,
	 * {@code STRUCTURED_FORMAT=gelf} switches to structured (Logstash JSON) output,
	 * {@code LOG_LEVEL} overrides the root level.
	 * @throws Exception if the server fails to start.
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
		if ("gelf".equals(System.getenv("STRUCTURED_FORMAT"))) {
			System.setProperty("logback.configurationFile", "logback-json.xml");
		}
		String logLevel = System.getenv("LOG_LEVEL");
		if (logLevel != null) {
			System.setProperty("bench.log.level", logLevel);
		}
		BenchServer.startAndAwait(port);
	}

}
