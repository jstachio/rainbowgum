package io.jstach.rainbowgum.pattern.format;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Pattern Config needed for pattern keywords that is generally platform specific. Through
 * property configuration there can only be one pattern config but through programmatic
 * configuration there is no limit by creating custom {@link PatternCompiler}.
 * <p>
 * This class is also a configurator for easy programmatic registration with
 * {@link LogConfig.Builder#configurator(Configurator)}.
 *
 * @see PatternCompiler
 *
 */
public sealed interface PatternConfig extends Configurator {

	/**
	 * Formatter properties prefix
	 */
	public static final String PATTERN_CONFIG_PREFIX = LogProperties.ROOT_PREFIX + "pattern.config.{name}.";

	/**
	 * Key Values that can be retrieved by various patterns.
	 */
	public static final String PATTERN_PROPERY_PREFIX = LogProperties.ROOT_PREFIX + "pattern.property.";

	/**
	 * Default zoneId if not specified.
	 * @return zone id.
	 */
	public ZoneId zoneId();

	/**
	 * Line separator for %n by default uses {@link System#lineSeparator()}.
	 * @return line separator.
	 */
	public String lineSeparator();

	/**
	 * Whether or not print escape outputs. If true than the color patterns will not
	 * decorate.
	 * @return false if ANSI escape sequences can be outputted.
	 */
	public boolean ansiDisabled();

	/**
	 * Key Value function for ad-hoc property replacements and lookup in various patterns.
	 * @return function.
	 * @see #PATTERN_PROPERY_PREFIX
	 */
	@SuppressWarnings("exports")
	public Function<String, @Nullable String> propertyFunction();

	/**
	 * Creates a builder to create formatter config.
	 * @return builder.
	 * @apiNote {@link PatternConfig} implements {@link Configurator} so it can be
	 * registered at config time easily.
	 */
	public static PatternConfigBuilder builder() {
		return builder(LogProperties.DEFAULT_NAME);
	}

	/**
	 * Creates a builder to create formatter config.
	 * @param name for property resolution: {@value #PATTERN_CONFIG_PREFIX}.
	 * @return builder.
	 * @apiNote {@link PatternConfig} implements {@link Configurator} so it can be
	 * registered at config time easily.
	 */
	public static PatternConfigBuilder builder(String name) {
		return new PatternConfigBuilder(name);
	}

	/**
	 * Copies the config to builder.
	 * @param builder to receive properties.
	 * @param config to copy from.
	 * @return builder passed in.
	 */
	public static PatternConfigBuilder copy(PatternConfigBuilder builder, PatternConfig config) {
		builder.ansiDisabled(config.ansiDisabled());
		builder.lineSeparator(config.lineSeparator());
		builder.zoneId(config.zoneId());
		builder.propertyFunction(config.propertyFunction());
		return builder;
	}

	/**
	 * Default config.
	 * @return default config.
	 */
	public static PatternConfig of() {
		return StandardFormatterConfig.DEFAULT_FORMATTER_CONFIG;
	}

	/**
	 * Platform independent formatter config that will not change across timezones or
	 * platforms. <strong>The zoneId is UTC, line separator is LF, and ansi is
	 * disabled.</strong>
	 * @return config.
	 */
	public static PatternConfig ofUniversal() {
		return StandardFormatterConfig.UNIVERSAL_FORMATTER_CONFIG;
	}

	/**
	 * Creates a property function.
	 * @param properties log properties.
	 * @param prefix prefix keys before asking LogProperties.
	 * @return function.
	 */
	@SuppressWarnings("exports")
	public static Function<String, @Nullable String> propertyFunction(LogProperties properties, String prefix) {
		record LogPropertiesPatternKeyValues(String prefix,
				LogProperties properties) implements Function<String, @Nullable String> {

			@Override
			public @Nullable String apply(String t) {
				String key = LogProperties.concatKey(prefix, t);
				return properties.valueOrNull(key);
			}

		}
		return new LogPropertiesPatternKeyValues(prefix, properties);
	}

	@Override
	default boolean configure(LogConfig config, Pass pass) {
		config.serviceRegistry().put(PatternConfig.class, ServiceRegistry.DEFAULT_SERVICE_NAME, this);
		return true;
	}

}

non-sealed interface DefaultFormatterConfig extends PatternConfig {

	/**
	 * Default zoneId if not specified. If not overridden the system default will be used.
	 * @return zone id.
	 */
	default ZoneId zoneId() {
		return ZoneId.systemDefault();
	}

	/**
	 * Line separator for %n by default uses {@link System#lineSeparator()}.
	 * @return line separator.
	 */
	default String lineSeparator() {
		return System.lineSeparator();
	}

	/**
	 * Whether or not print escape outputs. If true than the color patterns will not
	 * decorate.
	 * @return false if ANSI escape sequences can be outputted.
	 */
	default boolean ansiDisabled() {
		return false;
	}

	@Override
	default Function<String, @Nullable String> propertyFunction() {
		return StandardPropertyFunction.INSTANCE;
	}

	enum StandardPropertyFunction implements Function<String, @Nullable String> {

		INSTANCE;

		@Override
		public @Nullable String apply(String t) {
			return null;
		}

	}

}

enum StandardFormatterConfig implements DefaultFormatterConfig {

	DEFAULT_FORMATTER_CONFIG(), //
	UNIVERSAL_FORMATTER_CONFIG {
		@Override
		public String lineSeparator() {
			return "\n";
		}

		@Override
		public ZoneId zoneId() {
			return ZoneId.from(ZoneOffset.UTC);
		}

		@Override
		public boolean ansiDisabled() {
			return true;
		}
	};

}

record SimpleFormatterConfig(ZoneId zoneId, String lineSeparator, boolean ansiDisabled,
		Function<String, @Nullable String> propertyFunction) implements PatternConfig {

}