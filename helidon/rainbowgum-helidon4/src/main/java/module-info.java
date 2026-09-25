import io.jstach.rainbowgum.helidon4.RainbowGumLoggingProvider;

/**
 * Rainbow Gum <a href="https://helidon.io">Helidon</a> SE 4.x integration: implements
 * Helidon's own {@link io.helidon.logging.common.spi.LoggingProvider} extension point
 * (the same one {@code io.helidon.logging.jul.JulProvider}/
 * {@code io.helidon.logging.log4j.Log4jProvider} implement), registered via
 * {@link java.util.ServiceLoader}. See
 * {@link io.jstach.rainbowgum.helidon4.RainbowGumLoggingProvider} for the full
 * explanation of why this activates before any application log line, and beats
 * {@code JulProvider} automatically with no configuration needed.
 * <p>
 * Named {@code helidon4}, not {@code helidon}: Helidon's own major-version releases
 * (4.x, then whatever comes after) force a new artifact each time this project's own
 * shared, single-version-across-every-module scheme can't otherwise express an
 * upstream-driven break, the same reason {@code rainbowgum-spring-boot3}/
 * {@code rainbowgum-spring-boot4} are two separate artifacts rather than one.
 *
 * @provides io.helidon.logging.common.spi.LoggingProvider
 */
module io.jstach.rainbowgum.helidon4 {

	exports io.jstach.rainbowgum.helidon4;

	requires io.jstach.rainbowgum;
	requires io.helidon.logging.common;

	requires static org.jspecify;
	requires static io.jstach.svc;
	/*
	 * Not needed by this module's own main code (it never touches java.util.logging
	 * directly, only rainbowgum-jul does, at runtime), only by its tests, which use a
	 * plain java.util.logging.Logger to exercise the whole chain end to end. static
	 * since production code has no real dependency on it.
	 */
	requires static java.logging;

	provides io.helidon.logging.common.spi.LoggingProvider with RainbowGumLoggingProvider;

}
