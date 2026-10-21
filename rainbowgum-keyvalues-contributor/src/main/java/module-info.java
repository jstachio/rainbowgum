/**
 * <strong>EXPERIMENTAL</strong> Key values added to every event, such as a build version
 * or instance id, from properties or code. Simply being on the classpath registers
 * {@link io.jstach.rainbowgum.keyvalues.contributor.DefaultKeyValues} as the
 * {@code DEFAULTS} key values source, which every other source (SLF4J MDC, scoped key
 * values, ...) overrides on a key collision.
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-keyvalues-contributor")
module io.jstach.rainbowgum.keyvalues.contributor {
	requires static io.jstach.rainbowgum.annotation;

	exports io.jstach.rainbowgum.keyvalues.contributor;

	requires io.jstach.rainbowgum;

	requires static org.jspecify;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
			with io.jstach.rainbowgum.keyvalues.contributor.DefaultKeyValuesConfigurator;

}
