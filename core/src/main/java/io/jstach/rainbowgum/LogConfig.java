package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

public interface LogConfig {

	// default RuntimeMode runtimeMode() {
	// return Property.of(properties(), "mode") //
	// .mapString(s -> s.toUpperCase(Locale.ROOT)) //
	// .map(RuntimeMode::valueOf) //
	// .requireElse(RuntimeMode.DEV);
	// }
	//
	// public enum RuntimeMode {
	//
	// DEV, TEST, PROD
	//
	// }

	public LogProperties properties();

	public LevelResolver levelResolver();

	public Defaults defaults();

	default LogOutputProvider outputProvider() {
		return LogOutputProvider.of();
	}

	public static LogConfig of() {
		return LogConfig.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES);
	}

	public static LogConfig of(LogProperties properties) {
		return new DefaultLogConfig(properties);
	}

}

class DefaultLogConfig implements LogConfig, ConfigLevelResolver, LogProperties {

	private final LogProperties properties;

	private final ConcurrentHashMap<String, Level> levelCache = new ConcurrentHashMap<>();

	private final Defaults defaults = new Defaults(this);

	@Override
	public @Nullable String property(String key) {
		return properties.property("logging." + key);
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
	public Defaults defaults() {
		return defaults;
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
		return "level";
	}

}
