package io.jstach.rainbowgum.jul;

import java.util.logging.Logger;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.Property;
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
	 */
	public static final String JUL_DISABLE_PROPERTY = "logging.jul.disable";

	/**
	 * If true will not set the root loggers level at all, leaving it to whatever JUL
	 * itself is separately configured with (e.g. via <code>logging.properties</code>),
	 * entirely independent of rainbow gum.
	 */
	public static final String JUL_LEVEL_DISABLE_PROPERTY = "logging.jul.level.disable";

	/**
	 * Explicitly sets the JUL root loggers level (e.g. <code>ALL</code>,
	 * <code>TRACE</code>, <code>DEBUG</code>, <code>INFO</code>...). JUL's own
	 * <code>Logger.isLoggable()</code> gate runs before a record ever reaches the JUL
	 * handler, using JUL's own (separately configured, hierarchical) per-logger level
	 * tree - not rainbow gum's - so a logger name with a rainbow gum route or package
	 * level override <em>more verbose</em> than whatever the JUL root ends up at will
	 * have its records silently dropped before rainbow gum's own (correct) route level
	 * check ever runs. Setting this to <code>ALL</code> disables JUL's own gate entirely
	 * and delegates all level filtering to rainbow gum's router, at the cost of JUL no
	 * longer being able to cheaply pre-filter disabled calls itself (no {@code LogRecord}
	 * allocation or lazy message {@code Supplier} avoidance for below-threshold calls).
	 * If this property is not set, the JUL root level is instead synced to rainbow gum's
	 * global default level ({@code logging.level}), which is cheaper for the common case
	 * but shares the same limitation for any more-verbose per-package override.
	 */
	public static final String JUL_ROOT_LEVEL_PROPERTY = "logging.jul.root.level";

	static final Property<Boolean> JUL_DISABLE_PROPERTY_ = Property.builder()
		.ofBoolean() //
		.build(JUL_DISABLE_PROPERTY);

	static final Property<Boolean> JUL_LEVEL_DISABLE_PROPERTY_ = Property.builder()
		.ofBoolean() //
		.build(JUL_LEVEL_DISABLE_PROPERTY);

	static final Property<System.Logger.Level> JUL_ROOT_LEVEL_PROPERTY_ = Property.builder()
		.build(JUL_ROOT_LEVEL_PROPERTY)
		.map(LevelResolver::parseLevel);

	private volatile boolean installed = false;

	/**
	 * For service laoder.
	 */
	public JULConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		if (!install(config.properties())) {
			return true;
		}
		else {
			installed = true;
		}
		var disableLevel = JUL_LEVEL_DISABLE_PROPERTY_.get(config.properties()).value(false);

		if (!disableLevel) {
			var logger = Logger.getLogger("");
			if (logger != null) {
				/*
				 * An explicit logging.jul.root.level wins outright (the caller knows what
				 * they want, e.g. ALL for full correctness at the cost of JUL no longer
				 * being able to cheaply pre-filter disabled calls itself). Otherwise fall
				 * back to syncing with rainbow gum's own global default level, same as
				 * before - cheap for the common case, but a route/package level override
				 * more verbose than the global default will still be silently dropped by
				 * JUL's own gate before it ever reaches SystemLoggerQueueJULHandler's own
				 * (correct) route.isEnabled() check. logging.jul.root.level is the escape
				 * hatch for that.
				 */
				var explicitRootLevel = JUL_ROOT_LEVEL_PROPERTY_.get(config.properties()).valueOrNull();
				var systemLevel = explicitRootLevel != null ? explicitRootLevel
						: traceToAll(config.levelResolver().resolveLevel(""));
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
		if (JUL_DISABLE_PROPERTY_.get(properties).value(false)) {
			return false;
		}
		if (!isLoggingModuleAvailable()) {
			return false;
		}
		if (!SystemLoggerQueueJULHandler.isInstalled()) {
			SystemLoggerQueueJULHandler.install();
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
	 * @hidden
	 */
	public static boolean isInstalled() {
		return SystemLoggerQueueJULHandler.isInstalled();
	}

	@Override
	public void close() {
		if (installed) {
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
