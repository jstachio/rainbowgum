package io.jstach.rainbowgum.jdk.systemlogger;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.jdk.jul.JULConfigurator;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder;
import io.jstach.svc.ServiceProvider;

/**
 * System Logger rainbow gum implementation. Unlike the SLF4J implementation
 * <strong>Rainbow Gum does not cache System Loggers</strong> by name!
 *
 * @see #INITIALIZE_RAINBOW_GUM_PROPERTY
 * @see JULConfigurator#JUL_DISABLE_PROPERTY
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

	protected SystemLoggingFactory(LogProperties properties) {
		super(() -> initOption(properties));
		JULConfigurator.install(properties);
	}

}
