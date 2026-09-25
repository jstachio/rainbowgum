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
 * <p>
 * <strong>This module alone will not trigger Rainbow Gum to initialize.</strong> This is
 * by design, and a real limitation of {@code java.util.logging} itself, not something
 * Rainbow Gum works around. SLF4J's own
 * {@code org.slf4j.spi.SLF4JServiceProvider#initialize()} callback runs automatically the
 * first time application code touches {@code org.slf4j.LoggerFactory}, and
 * {@code System.Logger}'s {@link java.lang.System.LoggerFinder} is resolved the first time
 * anything calls {@code System.getLogger(...)}. Plain
 * {@code java.util.logging.Logger.getLogger(name)} has no equivalent
 * {@link java.util.ServiceLoader}-style indirection at all: it talks straight to the
 * classic {@code LogManager}. {@link io.jstach.rainbowgum.jul.JULConfigurator} is a
 * {@code io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator}, which only runs
 * <em>as part of</em> Rainbow Gum's own bootstrap sequence (it has no hook that could
 * <em>cause</em> that bootstrap in the first place). A pure JUL application (nothing
 * touching SLF4J or {@code System.Logger} anywhere, including incidentally via the JDK's
 * own internals) that depends on this module alone will see no change at all: the bridge
 * {@link java.util.logging.Handler} is never installed, because nothing ever builds a
 * Rainbow Gum for {@code JULConfigurator} to run against.
 * <p>
 * Two ways to fix this:
 * <ul>
 * <li>Depend on {@code io.jstach.rainbowgum.jdk} instead (which pulls this module in
 * transitively) to get this working automatically. See {@code doc/overview.html}'s
 * "java.lang.System.Logger and java.util.logging" section for the full explanation, and
 * {@code examples/helidon}'s own {@code README.md} for a real, empirically-verified
 * before/after.</li>
 * <li>If neither an existing {@code System.Logger}/SLF4J call site nor
 * {@code io.jstach.rainbowgum.jdk} is an option, call
 * {@code io.jstach.rainbowgum.RainbowGum#of()} explicitly before any JUL logging
 * happens.</li>
 * </ul>
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.jul {

	exports io.jstach.rainbowgum.jul;

	requires io.jstach.rainbowgum;

	requires static java.logging;
	requires static org.jspecify;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with io.jstach.rainbowgum.jul.JULConfigurator;

}
