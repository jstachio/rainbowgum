package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.jul.JULConfigurator;
import io.jstach.svc.ServiceProvider;

/**
 * System Logger rainbow gum implementation.
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
		return LogRouter.global().getLogger(name);
	}

}
