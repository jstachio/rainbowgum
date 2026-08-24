/**
 * Rainbow Gum JUL (<code>java.util.logging</code>) integration. This module installs a
 * {@link java.util.logging.Handler} on the JUL root logger so that any
 * <code>java.util.logging.Logger</code> usage (including from libraries and frameworks
 * that log via JUL directly, e.g. embedded Tomcat) is routed through Rainbow Gum.
 * <p>
 * This is a separate module (rather than being bundled directly into
 * <code>io.jstach.rainbowgum.jdk</code>) specifically so that it can be opted out of by
 * simply excluding this artifact, instead of relying on a properties-based disable flag.
 * To disable installation of the handler while still depending on this module set the
 * property:
 * {@value io.jstach.rainbowgum.jul.JULConfigurator#JUL_DISABLE_PROPERTY} to
 * <code>true</code>. Alternatively if in a custom modular environment using jlink and
 * the module <code>java.logging</code> is not included the handler will not be
 * installed. Furthermore <strong>the module <code>java.logging</code> is not required
 * and thus jlink might not automatically include it as it is
 * <code>requires static</code>.</strong>
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.jul {

	exports io.jstach.rainbowgum.jul;

	requires io.jstach.rainbowgum;

	requires static java.logging;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with io.jstach.rainbowgum.jul.JULConfigurator;

}
