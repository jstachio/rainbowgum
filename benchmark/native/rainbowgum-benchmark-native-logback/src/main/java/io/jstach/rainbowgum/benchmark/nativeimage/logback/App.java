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
 * {@code STRUCTURED_FORMAT=json} switches to {@code logback-json-builtin.xml} instead,
 * which uses logback-classic's own built-in {@code JsonEncoder} (part of logback-classic
 * itself since 1.5.x, no third-party dependency) - a fairer look at Logback's own JSON
 * path than the Jackson-based {@code logstash-logback-encoder}, which this benchmark's
 * own results found to be doing real, comparatively expensive per-event work (and, on
 * HotSpot specifically, a serious unbounded memory growth issue - see
 * {@code 0-11-2-RESULTS.md}). Not GELF or Logstash format either - its own generic,
 * RFC-8259 JSON-Lines representation of the event.
 * <p>
 * {@code LOG_LEVEL} overrides the root level (all three config files substitute
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
	 * {@code STRUCTURED_FORMAT=json} switches to Logback's own built-in JSON encoder,
	 * {@code LOG_LEVEL} overrides the root level.
	 * @throws Exception if the server fails to start.
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
		String structuredFormat = System.getenv("STRUCTURED_FORMAT");
		if ("gelf".equals(structuredFormat)) {
			System.setProperty("logback.configurationFile", "logback-json.xml");
		}
		else if ("json".equals(structuredFormat)) {
			System.setProperty("logback.configurationFile", "logback-json-builtin.xml");
		}
		String logLevel = System.getenv("LOG_LEVEL");
		if (logLevel != null) {
			System.setProperty("bench.log.level", logLevel);
		}
		BenchServer.startAndAwait(port);
	}

}
