package io.jstach.rainbowgum.micronaut5;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.micronaut.logging.LogLevel;
import io.micronaut.management.endpoint.loggers.LoggerConfiguration;
import io.micronaut.management.endpoint.loggers.ManagedLoggingSystem;
import jakarta.inject.Singleton;

/**
 * A real, native {@link ManagedLoggingSystem}: Micronaut's own
 * {@code io.micronaut.logging.PropertiesLoggingLevelsConfigurer} (an eagerly initialized,
 * {@code @Context}-scoped bean already bundled in {@code micronaut-context}) reads every
 * {@code logger.levels.*} (or bare {@code logger.*}) key from
 * {@code application.yml}/{@code application.properties} and calls
 * {@link #setLogLevel(String, LogLevel)} on every
 * {@link io.micronaut.logging.LoggingSystem} bean it finds, both at context startup and
 * on every {@code RefreshEvent}. Simply being a {@link Singleton} bean implementing this
 * interface is the entire integration: no {@link java.util.ServiceLoader} registration,
 * no early-lifecycle hook, nothing else to wire up.
 * <p>
 * This also closes the separate {@code /loggers} management endpoint gap
 * ({@code NoSuchBeanException: No bean of type [ManagedLoggingSystem] exists}, since
 * Micronaut's bundled {@code LogbackLoggingSystem}/{@code Log4jLoggingSystem} both
 * disable themselves once Logback/Log4j2 are not on the classpath): both problems are the
 * exact same missing bean.
 * <p>
 * Runtime level changes only take effect for loggers created after
 * {@link RainbowGumMicronautPropertiesProvider} has enabled them (a bare, root
 * {@code logging.change=level} property, present from the very first Rainbow Gum
 * bootstrap). Without that module also being on the classpath, {@link #setLogLevel} still
 * updates the underlying property, but already-created loggers that were frozen as
 * non-changeable before this bean was even asked to do anything will not see it.
 */
@Singleton
public final class RainbowGumLoggingSystem implements ManagedLoggingSystem {

	private static final String ROOT_NAME = "ROOT";

	/*
	 * Micronaut's LoggerConfiguration.configuredLevel() has no equivalent in Rainbow
	 * Gum's own LevelResolver (it only ever reports the *effective*, already-resolved
	 * level, never "was this one explicitly set or inherited"), so tracking which names
	 * this bean itself has been asked to configure is the only way to answer that
	 * question at all for getLoggers()/getLogger(String). Levels set some other way (a
	 * classpath logging.properties file, a system property) are invisible here:
	 * getLoggers() can only ever list names this bean itself has touched.
	 */
	private final ConcurrentHashMap<String, LogLevel> configuredLevels = new ConcurrentHashMap<>();

	/**
	 * For Micronaut dependency injection.
	 */
	public RainbowGumLoggingSystem() {
	}

	@Override
	public void setLogLevel(String name, LogLevel level) {
		var gum = RainbowGum.of();
		var mutable = mutableProperties();
		String key = propertyKey(name);
		if (level == LogLevel.NOT_SPECIFIED) {
			mutable.put(key, null);
			configuredLevels.remove(name);
		}
		else {
			mutable.put(key, toSystemLevel(level).toString());
			configuredLevels.put(name, level);
		}
		gum.config().changePublisher().publish();
	}

	@Override
	public Collection<LoggerConfiguration> getLoggers() {
		var levelResolver = RainbowGum.of().config().levelResolver();
		List<LoggerConfiguration> list = new ArrayList<>(configuredLevels.size());
		for (String name : configuredLevels.keySet()) {
			list.add(toConfiguration(name, levelResolver));
		}
		return list;
	}

	@Override
	public LoggerConfiguration getLogger(String name) {
		return toConfiguration(name, RainbowGum.of().config().levelResolver());
	}

	private LoggerConfiguration toConfiguration(String name, LevelConfig levelResolver) {
		var configured = configuredLevels.getOrDefault(name, LogLevel.NOT_SPECIFIED);
		var effective = fromSystemLevel(levelResolver.resolveLevel(resolverName(name)));
		return new LoggerConfiguration(name, configured, effective);
	}

	private MutableLogProperties mutableProperties() {
		var registry = RainbowGum.of().config().serviceRegistry();
		var mutable = registry.findOrNull(MutableLogProperties.class,
				RainbowGumMicronautPropertiesProvider.REGISTRY_NAME);
		if (mutable == null) {
			throw new IllegalStateException(
					"RainbowGumMicronautPropertiesProvider was not found in the ServiceRegistry. "
							+ "Is rainbowgum-micronaut5 fully on the classpath (its own META-INF/services entry missing)?");
		}
		return mutable;
	}

	private static String propertyKey(String name) {
		if (ROOT_NAME.equals(name)) {
			return LogProperties.LEVEL_PREFIX;
		}
		return LogProperties.LEVEL_PREFIX + LogProperties.SEP + name;
	}

	private static String resolverName(String name) {
		return ROOT_NAME.equals(name) ? "" : name;
	}

	private static java.lang.System.Logger.Level toSystemLevel(LogLevel level) {
		return switch (level) {
			case ALL -> java.lang.System.Logger.Level.ALL;
			case TRACE -> java.lang.System.Logger.Level.TRACE;
			case DEBUG -> java.lang.System.Logger.Level.DEBUG;
			case INFO -> java.lang.System.Logger.Level.INFO;
			case WARN -> java.lang.System.Logger.Level.WARNING;
			case ERROR -> java.lang.System.Logger.Level.ERROR;
			case OFF -> java.lang.System.Logger.Level.OFF;
			case NOT_SPECIFIED -> java.lang.System.Logger.Level.INFO;
		};
	}

	private static LogLevel fromSystemLevel(java.lang.System.Logger.Level level) {
		return switch (level) {
			case ALL -> LogLevel.ALL;
			case TRACE -> LogLevel.TRACE;
			case DEBUG -> LogLevel.DEBUG;
			case INFO -> LogLevel.INFO;
			case WARNING -> LogLevel.WARN;
			case ERROR -> LogLevel.ERROR;
			case OFF -> LogLevel.OFF;
		};
	}

}
