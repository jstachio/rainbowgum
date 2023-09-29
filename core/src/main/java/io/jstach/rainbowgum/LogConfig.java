package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

public interface LogConfig extends LogProperties {

	default @Nullable String property(String key) {
		return properties().property(key);
	}

	default RuntimeMode runtimeMode() {
		return Property.of(this, "mode") //
			.mapString(s -> s.toUpperCase(Locale.ROOT)) //
			.map(RuntimeMode::valueOf) //
			.requireElse(RuntimeMode.DEV);
	}

	public LogProperties properties();

	default Runnable shutdownHook() {
		return Defaults.shutdownHook.apply(this);
	}

	public LevelResolver levelResolver();

	public enum RuntimeMode {

		DEV, STAGE, PROD

	}

	default LogOutputProvider outputProvider() {
		return LogOutputProvider.of();
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

	public static LogConfig of() {
		return LogConfig.of(System::getProperty);
	}

	public static LogConfig of(LogProperties properties) {
		return new DefaultLogConfig(properties);
	}

}

class DefaultLogConfig implements LogConfig, ConfigLevelResolver {

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
		return properties;
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
