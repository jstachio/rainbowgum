package io.jstach.rainbowgum.jdk.jul;

import java.util.logging.Logger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogProperties.RequiredProperty;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Will install the JUL handler if it is not already set and the level of the loggers.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class JULConfigurator implements Configurator, AutoCloseable {

	/**
	 * If true will not install the JUL handler.
	 */
	public static final String JUL_DISABLE_PROPERTY = "logging.jul.disable";

	/**
	 * If true will not set the root loggers level to whatever the curreint rainbow gums
	 * global level default is.
	 */
	public static final String JUL_LEVEL_DISABLE_PROPERTY = "logging.jul.level.disable";

	static final RequiredProperty<Boolean> JUL_DISABLE_PROPERTY_ = Property.builder()
		.toBoolean() //
		.orElse(false) //
		.build(JUL_DISABLE_PROPERTY);

	static final RequiredProperty<Boolean> JUL_LEVEL_DISABLE_PROPERTY_ = Property.builder()
		.toBoolean() //
		.orElse(false) //
		.build(JUL_LEVEL_DISABLE_PROPERTY);

	/**
	 * For service laoder.
	 */
	public JULConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config) {
		if (!install(config.properties())) {
			return true;
		}
		var disableLevel = JUL_LEVEL_DISABLE_PROPERTY_.get(config.properties()).value();

		if (!disableLevel) {
			var logger = Logger.getLogger("");
			if (logger != null) {
				var systemLevel = traceToAll(config.levelResolver().resolveLevel(""));
				logger.setLevel(julLevel(systemLevel));
			}
		}
		return true;

	}

	private static System.Logger.Level traceToAll(System.Logger.Level level) {
		if (level == System.Logger.Level.TRACE) {
			return System.Logger.Level.ALL;
		}
		return level;
	}

	/**
	 * Will install the JUL handler if not disabled by properties. This method is
	 * currently exposed for testing purposes.
	 * @param properties properties to check
	 * @return true if enabled.
	 * @hidden
	 */
	public static boolean install(@SuppressWarnings("exports") LogProperties properties) {
		if (JUL_DISABLE_PROPERTY_.get(properties).value()) {
			return false;
		}

		if (!SystemLoggerQueueJULHandler.isInstalled()) {
			SystemLoggerQueueJULHandler.install();
		}
		return true;

	}

	/**
	 * Will test if already installed. This is mainly for testing purpsoes.
	 * @return true if installed.
	 * @hidden
	 */
	public static boolean isInstalled() {
		return SystemLoggerQueueJULHandler.isInstalled();
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
