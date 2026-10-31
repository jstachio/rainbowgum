package snippets;

import java.lang.System.Logger.Level;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

// @start region = "routeFilterExample"
public class RouteFilterExample implements Configurator {

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		Filter.register(config.serviceRegistry(), "quietDependency", (routeName, c) -> event -> {
			if (event.loggerName().startsWith("com.noisy.") && event.level() == Level.WARNING) {
				/*
				 * Dropped if the route does not enable INFO for the logger.
				 */
				return LogEvent.withLevel(event, Level.INFO);
			}
			return event;
		});
		return true;
	}

}
// @end
