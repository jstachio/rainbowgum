package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public interface LogConfig {

	@Nullable
	String property(String key);
	
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
	
	default @Nullable Level logLevel(String name) {
		String s = property("log." + name);
		if (s == null || s.isBlank()) {
			return null;
		}
		return Level.valueOf(s);
	}
	
	public static LogConfig of(Function<String, @Nullable String> propertySupplier) {
		return new LogConfig() {
			
			@Override
			public @Nullable String property(
					String key) {
				return "rainbowgum." + key;
			}
		};
	}
}

