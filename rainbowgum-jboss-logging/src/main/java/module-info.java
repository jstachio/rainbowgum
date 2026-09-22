import io.jstach.rainbowgum.jbosslogging.RainbowGumJBossLoggerProvider;

/**
 * Rainbow Gum <a href="https://github.com/jboss-logging/jboss-logging">JBoss
 * Logging</a> integration: a native {@link org.jboss.logging.LoggerProvider}
 * implementation, not a bridge through SLF4J. JBoss Logging does not auto-select its own
 * SLF4J provider just because <code>slf4j-api</code> is on the classpath - confirmed by
 * decompiling <code>org.jboss.logging.LoggerProviders.findProvider()</code>, it requires
 * <code>-Dorg.jboss.logging.provider=slf4j</code> set before that class loads, otherwise
 * it silently falls back to {@code java.util.logging} while still reporting
 * {@code isXEnabled()} correctly - a real footgun since nothing looks broken. This module
 * is instead picked up by the same {@link java.util.ServiceLoader} lookup
 * {@code findProvider()} already does before falling back to any of that, so no extra
 * configuration is required.
 *
 * @provides org.jboss.logging.LoggerProvider
 */
module io.jstach.rainbowgum.jbosslogging {

	exports io.jstach.rainbowgum.jbosslogging;

	requires io.jstach.rainbowgum;
	requires org.jboss.logging;

	requires static org.jspecify;
	requires static io.jstach.svc;

	provides org.jboss.logging.LoggerProvider with RainbowGumJBossLoggerProvider;

}
