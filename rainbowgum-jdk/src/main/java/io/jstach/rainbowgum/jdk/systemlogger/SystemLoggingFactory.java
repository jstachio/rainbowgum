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
		this(LogProperties.findGlobalProperties());

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
