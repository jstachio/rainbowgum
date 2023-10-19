package io.jstach.rainbowgum.avaje;

import java.lang.System.Logger;
import java.util.ResourceBundle;

import io.avaje.applog.AppLog;
import io.jstach.rainbowgum.LogRouter;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(AppLog.Provider.class)
public class RainbowGumAppLog implements AppLog.Provider {

	@Override
	public Logger getLogger(String name) {
		return LogRouter.global().getLogger(name);
	}

	@Override
	public Logger getLogger(String name, ResourceBundle bundle) {
		return LogRouter.global().getLogger(name);
	}

}
