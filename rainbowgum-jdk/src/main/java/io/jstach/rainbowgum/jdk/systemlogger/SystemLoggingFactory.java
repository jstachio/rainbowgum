package io.jstach.rainbowgum.jdk.systemlogger;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder;
import io.jstach.svc.ServiceProvider;

/**
 * System Logger rainbow gum implementation. Unlike the SLF4J implementation
 * <strong>Rainbow Gum does not cache System Loggers</strong> by name!
 * <p>
 * This no longer eagerly installs the <code>java.util.logging</code> handler itself -
 * that happens via the normal
 * {@link io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator} pass (see
 * {@code io.jstach.rainbowgum.jul.JULConfigurator}, found via
 * {@link java.util.ServiceLoader} if the <code>rainbowgum-jul</code> artifact is present)
 * once a real Rainbow Gum actually loads, whether triggered eagerly by this very class
 * (see {@link RainbowGumSystemLoggerFinder.InitOption#CHECK}) or later by SLF4J. Any
 * <code>java.util.logging</code> calls made before that point are no worse off than the
 * System.Logger events this class already queues until a real Rainbow Gum loads.
 * <p>
 * To keep just the handler installation disabled while still depending on it, see
 * {@code io.jstach.rainbowgum.jul.JULConfigurator#JUL_DISABLE_PROPERTY}.
 * <p>
 * <b>GraalVM native image</b>: this module bundles a {@code native-image.properties} (at
 * {@code META-INF/native-image/io.jstach.rainbowgum/rainbowgum-jdk/}) with
 * {@code --initialize-at-build-time=io.jstach.rainbowgum.jdk.systemlogger.SystemLoggingFactory}.
 * That flag is still needed even though this class does no eager work of its own: the
 * JDK's own internals ({@code java.time}, {@code java.util.Locale}/{@code Calendar}
 * formatting) call {@code System.getLogger(...)} incidentally, for their own diagnostics,
 * from unrelated static-init paths that end up reachable during a real native-image
 * build, and whichever registered {@code System.LoggerFinder} is on the classpath, namely
 * this class, gets swept up regardless. native-image's own embedded configuration
 * discovery picks this up from this module's jar automatically, no extra plugin or flag
 * needed on the consuming side; see {@code rainbowgum-systemlogger}'s own bundled
 * {@code native-image.properties} for the companion flag this one usually needs alongside
 * it.
 *
 * @see #INITIALIZE_RAINBOW_GUM_PROPERTY
 */
@ServiceProvider(System.LoggerFinder.class)
public final class SystemLoggingFactory extends RainbowGumSystemLoggerFinder {

	/**
	 * Initialization flag.
	 * @see RainbowGumSystemLoggerFinder.InitOption
	 */
	public static final String INITIALIZE_RAINBOW_GUM_PROPERTY = RainbowGumSystemLoggerFinder.INITIALIZE_RAINBOW_GUM_PROPERTY;

	/**
	 * No-Arg for Service Loader.
	 */
	public SystemLoggingFactory() {
		/*
		 * Deferring LogProperties.findGlobalProperties() itself into the lazy supplier,
		 * not just initOption(...), matters: that method checks RainbowGum.getOrNull()
		 * and falls back to real system properties, both of which must be resolved
		 * against whatever is true when a logger is actually first requested, not
		 * whatever happened to be true the moment this ServiceLoader-constructed instance
		 * came into being (see RainbowGumSystemLoggerFinder's own javadoc on why its
		 * constructor no longer does any of this eagerly either).
		 */
		super(() -> initOption(LogProperties.findGlobalProperties()));
	}

	/**
	 * For subclasses/testing that need to supply properties directly rather than through
	 * the no-arg constructor's {@link LogProperties#findGlobalProperties()}.
	 * @param properties properties to resolve the init option from.
	 */
	protected SystemLoggingFactory(LogProperties properties) {
		super(() -> initOption(properties));
	}

}
