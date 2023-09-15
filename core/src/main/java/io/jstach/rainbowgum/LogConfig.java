package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public interface LogConfig {

	@Nullable
	String property(String key);
	
	default RuntimeException throwPropertyError(String key, RuntimeException e) {
		throw new RuntimeException("Error for property: " + key, e);
	}
	
	default LogEncoder defaultOutput() {
		return LogEncoder.of(System.out);
	}

	default String hostName() {
		String hostName = property("HOSTNAME");
		if (hostName == null) {
			return "localhost";
		}
		return hostName;
	}
	
	default Map<String, String> headers() {
		return Map.of();
	}
	
	default @Nullable Level logLevelOrNull(String name) {
		String key = "log." + name;
		String s = property(key);
		if (s == null || s.isBlank()) {
			return null;
		}
		try {
			return Level.valueOf(s.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw throwPropertyError(key, e);
		}
	}
	
	
	default Level logLevel(String name, Level fallback) {
		String tempName = name;
		Level level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = logLevelOrNull(tempName);
			indexOfLastDot = String.valueOf(tempName)
				.lastIndexOf(".");
		}
		return level;
	}
	
	default Level logLevel(String name) {
		return logLevel(name, defaultLogLevel());
	}
	
	default Level defaultLogLevel() {
		return Level.INFO;
	}
	
	public static LogConfig of() {
		return LogConfig.of(System::getProperty);
	}
	
	public static LogConfig of(Function<String, @Nullable String> propertySupplier) {
		return new LogConfig() {
			
			@Override
			public @Nullable String property(
					String key) {
				return propertySupplier.apply("rainbowgum." + key);
			}
		};
	}
}

