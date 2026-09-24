import io.jstach.rainbowgum.log4j.RainbowGumLog4jProvider;

/**
 * Rainbow Gum <a href="https://logging.apache.org/log4j/2.x/">Log4j2</a> integration: a
 * native {@link org.apache.logging.log4j.spi.LoggerContextFactory} implementation, not a
 * bridge through SLF4J. Registered via {@link java.util.ServiceLoader}
 * ({@link org.apache.logging.log4j.spi.Provider}), not the older
 * {@code META-INF/log4j-provider.properties} mechanism - see
 * {@link io.jstach.rainbowgum.log4j.RainbowGumLog4jProvider}'s javadoc for why that
 * older path doesn't actually work on this version of {@code log4j-api}.
 * <p>
 * Package/module named {@code log4j}, not {@code log4j2} (unlike the
 * {@code rainbowgum-log4j2} Maven artifact this belongs to): a module name component
 * ending in a digit ({@code log4j2}) is a {@code javac} lint warning (treated as an
 * error here, {@code -Werror}), and Log4j2's own {@code log4j-api} jar uses the exact
 * same undigited {@code org.apache.logging.log4j} package/module name for its entire
 * 2.x line for the same reason.
 *
 * @provides org.apache.logging.log4j.spi.Provider
 */
module io.jstach.rainbowgum.log4j {

	exports io.jstach.rainbowgum.log4j;

	requires transitive io.jstach.rainbowgum;
	requires transitive org.apache.logging.log4j;

	requires static org.jspecify;
	requires static io.jstach.svc;

	provides org.apache.logging.log4j.spi.Provider with RainbowGumLog4jProvider;

}
