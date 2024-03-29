package io.jstach.rainbowgum.jdk.systemlogger;

import java.lang.System.Logger;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.jdk.jul.JULConfigurator;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLogger;
import io.jstach.svc.ServiceProvider;

/**
 * System Logger rainbow gum implementation. Unlike the SLF4J implementation
 * <strong>Rainbow Gum does not cache System Loggers</strong> by name!
 */
@ServiceProvider(System.LoggerFinder.class)
public final class SystemLoggingFactory extends System.LoggerFinder {

	/**
	 * No-Arg for Service Loader.
	 */
	public SystemLoggingFactory() {
		JULConfigurator.install(LogProperties.findGlobalProperties());
	}

	@Override
	public Logger getLogger(String name, Module module) {
		return RainbowGumSystemLogger.of(name, LogRouter.global());
	}

}
