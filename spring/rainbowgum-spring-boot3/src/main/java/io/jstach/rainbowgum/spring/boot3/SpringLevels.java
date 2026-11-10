package io.jstach.rainbowgum.spring.boot3;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.Environment;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;

/*
 * Spring Boot's logger levels: its built-in groups, the loggers debug and trace turn on,
 * and levels set through LoggingSystem.setLogLevel, for example by the actuator loggers
 * endpoint.
 */
final class SpringLevels {

	private SpringLevels() {
	}

	static final Set<LogLevel> SUPPORTED = Set.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN,
			LogLevel.ERROR, LogLevel.OFF);

	static final String GROUP_PREFIX = "logging.group.";

	static final String LEVEL_PREFIX = LogProperties.LEVEL_PREFIX + LogProperties.SEP;

	/*
	 * LoggingApplicationListener.DEFAULT_GROUP_LOGGERS. A group of the same name in the
	 * environment replaces it, as in Spring Boot.
	 */
	static final Map<String, List<String>> DEFAULT_GROUPS = Map.of( //
			"web",
			List.of("org.springframework.core.codec", "org.springframework.http", "org.springframework.web",
					"org.springframework.boot.actuate.endpoint.web",
					"org.springframework.boot.web.servlet.ServletContextInitializerBeans"), //
			"sql", List.of("org.springframework.jdbc.core", "org.hibernate.SQL", "org.jooq.tools.LoggerListener"));

	/*
	 * LoggingApplicationListener.SPRING_BOOT_LOGGING_LOGGERS: what debug or trace set.
	 * Only one list applies, debug winning over trace.
	 */
	static final Map<LogLevel, List<String>> BOOT_LOGGERS = Map.of( //
			LogLevel.DEBUG, List.of("sql", "web", "org.springframework.boot"), //
			LogLevel.TRACE, List.of("org.springframework", "org.apache.tomcat", "org.apache.catalina",
					"org.eclipse.jetty", "org.hibernate.tool.hbm2ddl"));

	/*
	 * Spring Boot groups are on once defined, unlike Rainbow Gum's, which are listed in
	 * logging.groups.
	 */
	static String groups(Environment environment) {
		var names = new LinkedHashSet<String>(DEFAULT_GROUPS.keySet());
		Binder.get(environment)
			.bind("logging.group", Bindable.mapOf(String.class, String.class))
			.ifBound(m -> names.addAll(m.keySet()));
		return String.join(",", names);
	}

	static @Nullable String defaultGroup(String group) {
		var loggers = DEFAULT_GROUPS.get(group);
		return loggers == null ? null : String.join(",", loggers);
	}

	/*
	 * The level debug or trace gives a logger or group, which logging.level.{name} in the
	 * environment overrides.
	 */
	static @Nullable String bootLevel(Environment environment, String name) {
		LogLevel level = isSet(environment, "debug") ? LogLevel.DEBUG
				: isSet(environment, "trace") ? LogLevel.TRACE : null;
		if (level == null || !BOOT_LOGGERS.getOrDefault(level, List.of()).contains(name)) {
			return null;
		}
		return level.name();
	}

	// LoggingApplicationListener.isSet
	private static boolean isSet(Environment environment, String property) {
		String value = environment.getProperty(property);
		return value != null && !value.equals("false");
	}

	static Level toSystem(LogLevel level) {
		return switch (level) {
			case TRACE -> Level.TRACE;
			case DEBUG -> Level.DEBUG;
			case INFO -> Level.INFO;
			case WARN -> Level.WARNING;
			case ERROR, FATAL -> Level.ERROR;
			case OFF -> Level.OFF;
		};
	}

	static LogLevel fromSystem(Level level) {
		return switch (level) {
			case ALL, TRACE -> LogLevel.TRACE;
			case DEBUG -> LogLevel.DEBUG;
			case INFO -> LogLevel.INFO;
			case WARNING -> LogLevel.WARN;
			case ERROR -> LogLevel.ERROR;
			case OFF -> LogLevel.OFF;
		};
	}

	static String loggerName(@Nullable String springName) {
		return springName == null || springName.equalsIgnoreCase(LoggingSystem.ROOT_LOGGER_NAME) ? "" : springName;
	}

	static String levelKey(String loggerName) {
		return loggerName.isEmpty() ? LogProperties.LEVEL_PREFIX : LEVEL_PREFIX + loggerName;
	}

	/*
	 * Held in the service registry of the Rainbow Gum Spring Boot built. Marks it as
	 * Spring's, holds the levels set through setLogLevel and, like Logback's marker in
	 * its context, whether a later initialize should replace it.
	 */
	static final class State {

		private final Map<String, String> levels = new ConcurrentHashMap<>();

		final MutableLogProperties overrides = MutableLogProperties.builder()
			.description("Spring Boot logger levels")
			.with(levels)
			.build();

		volatile boolean initialized = true;

		static @Nullable State of(RainbowGum gum) {
			return gum.config().serviceRegistry().findOrNull(State.class);
		}

		/*
		 * Registers this state and its levels as the level override properties, which win
		 * over every other source of levels.
		 */
		LogConfig.Builder register(LogConfig.Builder builder) {
			var registry = ServiceRegistry.of();
			registry.put(State.class, this);
			registry.put(LogProperties.class, LevelResolver.OVERRIDE_PROPERTIES_NAME, overrides);
			return builder.serviceRegistry(registry);
		}

		/*
		 * Spring Boot calls this at startup for every logging.level.{name}, every group
		 * member and the debug and trace loggers. Rainbow Gum already has those levels
		 * from the environment, so a level the logger already has is not a change.
		 */
		void setLogLevel(LogConfig config, @Nullable String springName, @Nullable LogLevel level) {
			String name = loggerName(springName);
			String key = levelKey(name);
			if (level == null) {
				if (levels.containsKey(key)) {
					checkAllowed(config, name);
					levels.remove(key);
					config.changePublisher().publish();
				}
				return;
			}
			Level systemLevel = toSystem(level);
			if (!levels.containsKey(key) && config.levelResolver().resolveLevel(name) == systemLevel) {
				return;
			}
			checkAllowed(config, name);
			levels.put(key, systemLevel.name());
			config.changePublisher().publish();
		}

		private static void checkAllowed(LogConfig config, String name) {
			if (!config.changePublisher().allowedChanges(name).contains(ChangeType.LEVEL)) {
				String logger = name.isEmpty() ? LoggingSystem.ROOT_LOGGER_NAME : name;
				throw new UnsupportedOperationException(
						"Level changes are off for logger '" + logger + "'. Set " + LogProperties.CHANGE_MODE_PROPERTY
								+ "=on and " + LogProperties.CHANGE_LOGGER_PREFIX + ".{logger}=level to allow them.");
			}
		}

		/*
		 * The root logger first, then every logger a facade has created and every logger
		 * given a level through setLogLevel, by name.
		 */
		List<LoggerConfiguration> loggerConfigurations(LogConfig config) {
			var names = new TreeSet<String>(config.loggerRegistry().loggerNames());
			for (String key : levels.keySet()) {
				if (key.startsWith(LEVEL_PREFIX)) {
					names.add(key.substring(LEVEL_PREFIX.length()));
				}
			}
			names.remove("");
			var result = new ArrayList<LoggerConfiguration>(names.size() + 1);
			result.add(loggerConfiguration(config, ""));
			for (String name : names) {
				result.add(loggerConfiguration(config, name));
			}
			return result;
		}

		static LoggerConfiguration loggerConfiguration(LogConfig config, String name) {
			Level configured = config.levelResolver().levelOrNull(name);
			Level effective = config.levelResolver().resolveLevel(name);
			return new LoggerConfiguration(name.isEmpty() ? LoggingSystem.ROOT_LOGGER_NAME : name,
					configured == null ? null : fromSystem(configured), fromSystem(effective));
		}

	}

}
