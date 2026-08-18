import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Provides a {@link io.jstach.rainbowgum.LogOutput} that commits each log event as a
 * <a href="https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/module-summary.html">JDK
 * Flight Recorder</a> event instead of writing bytes anywhere, with URI scheme
 * {@value io.jstach.rainbowgum.jfr.JfrLogOutput#JFR_SCHEME}.
 * <p>
 * This is its own module (rather than in core) because it requires the JDK's
 * {@code jdk.jfr} module, which is not something every application wants as a
 * dependency.
 *
 * @provides RainbowGumServiceProvider
 * @see io.jstach.rainbowgum.jfr.JfrLogOutput
 * @see io.jstach.rainbowgum.jfr.RainbowGumLogEvent
 */
module io.jstach.rainbowgum.jfr {

	exports io.jstach.rainbowgum.jfr;

	requires transitive io.jstach.rainbowgum;
	requires jdk.jfr;

	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;

	provides RainbowGumServiceProvider with io.jstach.rainbowgum.jfr.JfrConfigurator;
}
