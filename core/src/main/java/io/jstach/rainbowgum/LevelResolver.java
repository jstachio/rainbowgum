package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;

public interface LevelResolver {

	public Level logLevel(String name);

	public Level defaultLogLevel();

	default boolean isEnabled(String loggerName, Level level) {
		return logLevel(loggerName).getSeverity() <= level.getSeverity();
	}

	public static LevelResolver of(LogProperties config) {
		return new ConfigLevelResolver() {

			@Override
			public LogProperties properties() {
				return config;
			}
		};
	}

}

interface ConfigLevelResolver extends LevelResolver {

	LogProperties properties();

	default @Nullable Level logLevelOrNull(String name) {
		String key = "log." + name;
		return Property.of(properties(), key) //
			.mapString(s -> s.toUpperCase(Locale.ROOT)) //
			.map(Level::valueOf) //
			.value();
	}

	default Level logLevel(String name, Level fallback) {
		String tempName = name;
		Level level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = logLevelOrNull(tempName);
			indexOfLastDot = String.valueOf(tempName).lastIndexOf(".");
		}
		if (level != null) {
			return level;
		}
		return fallback;
	}

	default Level logLevel(String name) {
		return logLevel(name, defaultLogLevel());
	}

	default Level defaultLogLevel() {
		return Level.INFO;
	}

}