package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger;

import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.jul.SystemLoggerQueueJULHandler;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(System.LoggerFinder.class)
public class SystemLoggingFactory extends System.LoggerFinder {

	public SystemLoggingFactory() {
		SystemLoggerQueueJULHandler.install();
	}

	@Override
	public Logger getLogger(String name, Module module) {
		return LogRouter.global().getLogger(name);
	}

}
