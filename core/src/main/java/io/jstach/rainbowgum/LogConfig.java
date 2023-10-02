package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

public interface LogConfig {

	default RuntimeMode runtimeMode() {
		return Property.of(properties(), "mode") //
			.mapString(s -> s.toUpperCase(Locale.ROOT)) //
			.map(RuntimeMode::valueOf) //
			.requireElse(RuntimeMode.DEV);
	}

	public LogProperties properties();

	public LevelResolver levelResolver();

	public enum RuntimeMode {

		DEV, TEST, PROD

	}

	default LogOutputProvider outputProvider() {
		return LogOutputProvider.of();
	}

	public static LogConfig of() {
		return LogConfig.of(SystemProperties.INSTANCE);
	}

	public static LogConfig of(LogProperties properties) {
		return new DefaultLogConfig(properties);
	}

}

enum SystemProperties implements LogProperties {

	INSTANCE;

	@Override
	public @Nullable String property(String key) {
		return System.getProperty(key);
	}

}

class DefaultLogConfig implements LogConfig, ConfigLevelResolver, LogProperties {

	private final LogProperties properties;

	private final ConcurrentHashMap<String, Level> levelCache = new ConcurrentHashMap<>();

	@Override
	public @Nullable String property(String key) {
		return properties.property("rainbowgum." + key);
	}

	public DefaultLogConfig(LogProperties properties) {
		super();
		this.properties = properties;
	}

	@Override
	public LogProperties properties() {
		return this;
	}

	@Override
	public LevelResolver levelResolver() {
		return this;
	}

	@Override
	public Level resolveLevel(String name) {
		var level = levelCache.get(name);
		if (level != null) {
			return level;
		}
		return levelCache.computeIfAbsent(name, n -> ConfigLevelResolver.super.resolveLevel(name));
	}

	@Override
	public String levelPropertyPrefix() {
		return "log";
	}

}
