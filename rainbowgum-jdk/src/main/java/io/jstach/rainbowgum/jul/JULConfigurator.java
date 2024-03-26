package io.jstach.rainbowgum.jul;

import java.util.logging.Logger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Will install the JUL handler if it is not already set and the level of the loggers.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JULConfigurator implements Configurator, AutoCloseable {

	/**
	 * If true will not set the root loggers level to whatever the curreint rainbow gums
	 * global level default is.
	 */
	public static String JUL_LEVEL_DISABLE_PROPERTY = "logging.jul.level.disable";

	/**
	 * For service laoder.
	 */
	public JULConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config) {
		if (!SystemLoggerQueueJULHandler.isInstalled()) {
			SystemLoggerQueueJULHandler.install();
		}
		var disableLevel = Property.builder()
			.toBoolean() //
			.orElse(false) //
			.build(JUL_LEVEL_DISABLE_PROPERTY) //
			.get(config.properties()) //
			.value();

		if (!disableLevel) {
			var logger = Logger.getLogger("");
			if (logger != null) {
				var systemLevel = config.levelResolver().defaultLevel();
				logger.setLevel(julLevel(systemLevel));
			}
		}
		return true;
	}

	@Override
	public void close() {
		var logger = Logger.getLogger("");
		if (logger != null) {
			logger.setLevel(java.util.logging.Level.INFO);
		}

	}

	private java.util.logging.Level julLevel(System.Logger.Level level) {
		var julLevel = switch (level) {
			case TRACE -> java.util.logging.Level.FINEST;
			case DEBUG -> java.util.logging.Level.FINE;
			case INFO -> java.util.logging.Level.INFO;
			case WARNING -> java.util.logging.Level.WARNING;
			case ERROR -> java.util.logging.Level.SEVERE;
			case ALL -> java.util.logging.Level.ALL;
			case OFF -> java.util.logging.Level.OFF;
		};
		return julLevel;
	}

}
