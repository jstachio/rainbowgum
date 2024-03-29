package io.jstach.rainbowgum.avaje;

import java.lang.System.Logger;
import java.util.ResourceBundle;

import io.avaje.applog.AppLog;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLogger;
import io.jstach.svc.ServiceProvider;

/**
 * Provides an Avaje logger to use RainbowGum global queue to avoid initialization issues.
 */
@ServiceProvider(AppLog.Provider.class)
public class RainbowGumAppLog implements AppLog.Provider {

	/**
	 * For service loader.
	 */
	public RainbowGumAppLog() {
	}

	@Override
	public Logger getLogger(String name) {
		return RainbowGumSystemLogger.of(name, LogRouter.global());
	}

	@Override
	public Logger getLogger(String name, ResourceBundle bundle) {
		return getLogger(name);
	}

}
