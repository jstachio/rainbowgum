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
 * <p>
 * {@code APPENDER_TYPE} overrides the console appender's locking/buffering strategy (e.g.
 * {@code SYNCHRONIZED_THREAD_LOCAL_BUFFER}, matching Log4j2's own
 * {@code synchronized}-based approach more closely than the
 * {@code LOCK_THREAD_LOCAL_BUFFER} default - see {@code LogAppender.AppenderType}'s own
 * javadoc for why that default exists: real-workload benchmarking found
 * {@code SYNCHRONIZED_THREAD_LOCAL_BUFFER} loses to it specifically under virtual
 * threads, for reasons not fully understood, even though it used to win under platform
 * threads). Unset by default (Rainbow Gum's own default type applies).
 * <p>
 * {@code LOG_LEVEL} overrides the root level ({@code logging.level}) - e.g.
 * {@code LOG_LEVEL=ERROR} for a "mostly off" baseline, since none of
 * {@code BenchHandler}'s calls are above {@code INFO}.
 */
public final class App {

	private App() {
	}

	/**
	 * Entry point.
	 * @param args ignored; port defaults to 8080 unless {@code PORT} is set,
	 * {@code STRUCTURED_FORMAT=gelf} switches to GELF output, {@code APPENDER_TYPE}
	 * overrides the console appender's locking strategy, {@code LOG_LEVEL} overrides the
	 * root level.
	 * @throws Exception if the server fails to start.
	 */
	public static void main(String[] args) throws Exception {
		int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
		if ("gelf".equals(System.getenv("STRUCTURED_FORMAT"))) {
			System.setProperty("logging.appender.console.encoder", "gelf:///?host=benchmark-host");
		}
		String appenderType = System.getenv("APPENDER_TYPE");
		if (appenderType != null) {
			System.setProperty("logging.appender.console.type", appenderType);
		}
		String logLevel = System.getenv("LOG_LEVEL");
		if (logLevel != null) {
			System.setProperty("logging.level", logLevel);
		}
		BenchServer.startAndAwait(port);
	}

}
