/**
 * A {@link java.util.logging.LogManager} replacement that routes every
 * {@code java.util.logging.Logger} directly through Rainbow Gum, bypassing the
 * standard JUL Handler/Level dispatch machinery entirely.
 * <p>
 * This is a separate module from {@code io.jstach.rainbowgum.jul} (which stays a
 * Handler-based bridge, installed onto whatever the default {@link java.util.logging.LogManager}
 * already is) because replacing the {@code LogManager} itself is a much more invasive,
 * whole-JVM decision: it can only be activated via the {@code java.util.logging.manager}
 * system property, set before anything in the JVM has touched
 * {@code java.util.logging}, and it changes the behavior of every JUL {@code Logger} in
 * the process, not just the ones a Handler happens to see. Install by setting:
 * <pre>{@code java.util.logging.manager=io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager}</pre>
 * as a real {@code -D} JVM argument (or {@code JDK_JAVA_OPTIONS} environment variable) -
 * this cannot be done with {@link System#setProperty(String, String)} once the JVM has
 * started, since {@code java.util.logging.LogManager}'s own bootstrap reads the property
 * exactly once, the first time anything touches JUL.
 * <p>
 * This is primarily useful for frameworks whose own internal logging is JUL-based and
 * which offer no other seam to redirect it. Helidon is the motivating case (see
 * {@code doc/other-web-frameworks.md}): Helidon's own {@code helidon-logging-log4j}
 * module uses the exact same mechanism, setting
 * {@code java.util.logging.manager=org.apache.logging.log4j.jul.LogManager}. This
 * module's own {@link io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager} is
 * modeled closely on that Log4j2 implementation.
 * <p>
 * Works under GraalVM native-image with no consumer-side configuration - see
 * {@link io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager}'s own javadoc for the
 * build-time mechanism this module bundles to make that true.
 */
module io.jstach.rainbowgum.jul.logmanager {

	exports io.jstach.rainbowgum.jul.logmanager;

	requires io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.jul;

	requires java.logging;
	requires static org.jspecify;

}
