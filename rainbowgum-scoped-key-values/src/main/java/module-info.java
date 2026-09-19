/**
 * <strong>EXPERIMENTAL</strong> The RainbowGum-backed
 * {@code io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider} for
 * {@code io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues} - request/task-scoped key
 * values backed by {@code java.lang.ScopedValue} instead of {@code ThreadLocal}. Simply
 * being on the classpath is enough: this module's
 * {@link io.jstach.rainbowgum.scopedkeyvalues.provider.ScopedKeyValuesConfigurator}
 * registers a {@code LogEventFactory} that {@code rainbowgum-slf4j} picks up
 * automatically, merging the current scoped key values underneath MDC for every event it
 * builds.
 * <p>
 * Requires a JVM supporting {@code java.lang.ScopedValue} (finalized in JDK 25,
 * <a href="https://openjdk.org/jeps/506">JEP 506</a>) - newer than this project's usual
 * baseline, which is why this module alone overrides its own build's Java release. The
 * {@code io.jstach.rainbowgum.scopedkeyvalues} API module this implements does not share
 * that requirement.
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 * @provides io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider
 */
module io.jstach.rainbowgum.scopedkeyvalues.provider {

	exports io.jstach.rainbowgum.scopedkeyvalues.provider;

	requires io.jstach.rainbowgum.scopedkeyvalues;
	requires io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.slf4j;

	requires static org.jspecify;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
			with io.jstach.rainbowgum.scopedkeyvalues.provider.ScopedKeyValuesConfigurator;

	provides io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider
			with io.jstach.rainbowgum.scopedkeyvalues.provider.ScopedKeyValuesProviderImpl;

}
