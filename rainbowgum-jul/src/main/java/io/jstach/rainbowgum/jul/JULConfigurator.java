package io.jstach.rainbowgum.jul;

import java.util.Locale;
import java.util.logging.Logger;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Will install the JUL handler if the <code>java.logging</code> module is available and
 * is not alreadyi installed.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class JULConfigurator implements Configurator, AutoCloseable {

	/**
	 * If true will not install the JUL handler.
	 * <p>
	 * Property: <code>{@index logging.jul.disable}</code>
	 */
	public static final String JUL_DISABLE_PROPERTY = "logging.jul.disable";

	/**
	 * Sets the level of the java.util.logging root logger. java.util.logging checks it,
	 * and any levels configured in java.util.logging itself, before an event reaches
	 * Rainbow Gum, so an event below it is not logged even if Rainbow Gum would log it.
	 * Values (case is ignored):
	 * <ul>
	 * <li><code>sync</code> (default): the root level of Rainbow Gum. Events of a logger
	 * configured more verbose than the root, such as
	 * <code>logging.level.com.example=debug</code> with an <code>info</code> root, are
	 * not logged when they are below the root level.</li>
	 * <li><code>none</code>: the level is left as java.util.logging is configured, for
	 * example by <code>logging.properties</code>.</li>
	 * <li>A level such as <code>all</code>, <code>debug</code> or <code>info</code>: that
	 * level. With <code>all</code> every event reaches Rainbow Gum, which then applies
	 * its own levels, at the cost of a {@link java.util.logging.LogRecord} for every
	 * event including those Rainbow Gum does not log.</li>
	 * </ul>
	 * <p>
	 * Property: <code>{@index logging.jul.level}</code>
	 */
	public static final String JUL_LEVEL_PROPERTY = "logging.jul.level";

	private volatile boolean installed = false;

	private volatile boolean levelSet = false;

	/**
	 * For service laoder.
	 */
	public JULConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		config.reporter().addProperty(JULConfigurator.class, JUL_DISABLE_PROPERTY);
		config.reporter().addProperty(JULConfigurator.class, JUL_LEVEL_PROPERTY);
		if (!install(config.properties())) {
			return true;
		}
		else {
			installed = true;
		}
		var julLevel = config.properties()
			.forKey(JUL_LEVEL_PROPERTY)
			.ofString()
			.map(JULLevel::parse)
			.or(JULLevel.Policy.SYNC)
			.validateNow(JULConfigurator.class);
		System.Logger.Level level = switch (julLevel) {
			case JULLevel.Policy.NONE -> null;
			case JULLevel.Policy.SYNC -> config.levelResolver().resolveLevel("");
			case JULLevel.Fixed f -> f.level();
		};
		if (level != null) {
			var logger = Logger.getLogger("");
			if (logger != null) {
				logger.setLevel(julLevel(traceToAll(level)));
				levelSet = true;
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
	 */
	public static boolean install(@SuppressWarnings("exports") LogProperties properties) {
		if (properties.forKey(JUL_DISABLE_PROPERTY).ofBoolean().or(false).validateNow(JULConfigurator.class)) {
			return false;
		}
		if (!isLoggingModuleAvailable()) {
			return false;
		}
		var messageFormatter = LogMessageFormatter.ofJUL(properties);
		if (!SystemLoggerQueueJULHandler.isInstalled(messageFormatter)) {
			SystemLoggerQueueJULHandler.install(messageFormatter);
		}
		return true;

	}

	private static boolean isLoggingModuleAvailable() {
		ModuleLayer bootLayer = ModuleLayer.boot();
		return bootLayer.findModule("java.logging").isPresent();
	}

	/**
	 * Will test if already installed. This is mainly for testing purpsoes.
	 * @return true if installed.
	 */
	public static boolean isInstalled() {
		return SystemLoggerQueueJULHandler.isInstalled();
	}

	@Override
	public void close() {
		if (installed && levelSet) {
			var logger = Logger.getLogger("");
			if (logger != null) {
				logger.setLevel(java.util.logging.Level.INFO);
			}
		}

	}

	private static java.util.logging.Level julLevel(System.Logger.Level level) {
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

/*
 * A value of logging.jul.level.
 */
sealed interface JULLevel {

	enum Policy implements JULLevel {

		SYNC, NONE

	}

	record Fixed(System.Logger.Level level) implements JULLevel {
	}

	static JULLevel parse(String input) {
		return switch (input.strip().toLowerCase(Locale.ROOT)) {
			case "sync" -> Policy.SYNC;
			case "none" -> Policy.NONE;
			default -> {
				try {
					yield new Fixed(LevelResolver.parseLevel(input));
				}
				catch (IllegalArgumentException e) {
					throw new IllegalArgumentException("'" + input
							+ "' is not a valid value. Valid values: 'sync', 'none' or a level such as 'all', 'debug' or 'info'");
				}
			}
		};
	}

}
