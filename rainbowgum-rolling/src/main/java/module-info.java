import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Bare minimum size based rolling file output. See
 * {@link io.jstach.rainbowgum.rolling.RollingFileOutput} for the full property list and
 * scope - deliberately does not support calendar/date based rotation or Logback's own
 * {@code fileNamePattern} conventions, only a numbered-suffix (<code>%i</code>) scheme.
 *
 * @provides RainbowGumServiceProvider
 * @see io.jstach.rainbowgum.rolling.RollingFileOutput
 */
module io.jstach.rainbowgum.rolling {

	exports io.jstach.rainbowgum.rolling;

	requires transitive io.jstach.rainbowgum;

	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;

	provides RainbowGumServiceProvider with io.jstach.rainbowgum.rolling.RollingConfigurator;

}
