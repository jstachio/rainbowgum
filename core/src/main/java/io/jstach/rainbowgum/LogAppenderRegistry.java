package io.jstach.rainbowgum;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.AppenderProvider;
import io.jstach.rainbowgum.LogProperties.Property;

/**
 * Register appenders by name.
 */
public sealed interface LogAppenderRegistry permits DefaultAppenderRegistry {

	/**
	 * Finds an appender by name.
	 * @param name name of the appender.
	 * @return appender provider to be used for creating the appender.
	 */
	Optional<AppenderProvider> appender(String name);

	/**
	 * Registers an appender provider by name.
	 * @param name of the appender.
	 * @param appenderProvider factory to be used for creating appenders.
	 */
	void register(String name, AppenderProvider appenderProvider);

	/**
	 * Creates a log appender registry.
	 * @return appender registry.
	 */
	public static LogAppenderRegistry of() {
		return new DefaultAppenderRegistry();
	}

}

final class DefaultAppenderRegistry implements LogAppenderRegistry {

	private final Map<String, AppenderProvider> providers = new ConcurrentHashMap<>();

	static final Property<URI> fileProperty = Property.builder().toURI().build(LogProperties.FILE_PROPERTY);

	static final Property<List<String>> appendersProperty = Property.builder()
		.map(LogProperties::parseList)
		.orElse(List.of())
		.build(LogProperties.APPENDERS_PROPERTY);

	static List<LogAppender> defaultAppenders(LogConfig config) {
		List<LogAppender> appenders = new ArrayList<>();
		fileAppender(config).ifPresent(appenders::add);
		List<String> appenderNames = appendersProperty.get(config.properties()).value(List.of());

		for (String appenderName : appenderNames) {
			var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, appenderName, config);
			var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, appenderName, config);
			appenders.add(appender(appenderName, outputProperty, encoderProperty, config));
		}
		if (appenders.isEmpty()) {
			var consoleAppender = defaultConsoleAppender(config);
			appenders.add(consoleAppender);
		}
		return appenders;
	}

	private static LogAppender defaultConsoleAppender(LogConfig config) {
		var consoleAppender = LogAppender.builder().output(LogOutput.ofStandardOut()).build().provide(config);
		return consoleAppender;
	}

	static Optional<LogAppender> fileAppender(LogConfig config) {
		String name = LogAppender.FILE_APPENDER_NAME;
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return fileProperty //
			.map(u -> appender(name, outputProperty, encoderProperty, config))
			.get(config.properties())
			.optional();
	}

	static LogAppender appender( //
			String name, Property<LogOutput> outputProperty, Property<LogEncoder> encoderProperty, LogConfig config) {
		var appenderRegistry = config.appenderRegistry();
		var appender = appenderRegistry.appender(name).map(a -> a.provide(config)).orElse(null);
		if (appender != null) {
			return appender;
		}
		var builder = LogAppender.builder();
		LogOutput output = outputProperty.get(config.properties()).valueOrNull();
		if (output != null) {
			builder.output(output);
		}
		LogEncoder encoder = encoderProperty.get(config.properties()).valueOrNull();
		if (encoder != null) {
			builder.encoder(encoder);
		}
		return builder.build().provide(config);

	}

	private static @Nullable LogOutput outputForAppender(String propertyName, String name, LogConfig config) {
		LogOutput output = Property.builder() //
			.toURI() //
			.map(u -> config.outputRegistry().output(u, name, config.properties())) //
			.buildWithName(propertyName, name) //
			.get(config.properties()) //
			.valueOrNull();
		return output;
	}

	private static Property<LogOutput> outputProperty(String propertyName, String name, LogConfig config) {
		return Property.builder() //
			.toURI() //
			.map(u -> config.outputRegistry().output(u, name, config.properties())) //
			.buildWithName(propertyName, name);
	}

	private static Property<LogEncoder> encoderProperty(String propertyName, String name, LogConfig config) {
		return Property.builder() //
			.toURI() //
			.map(u -> config.encoderRegistry().provide(u, name, config.properties())) //
			.buildWithName(propertyName, name);
	}

	@Override
	public Optional<AppenderProvider> appender(String name) {
		return Optional.ofNullable(providers.get(name));
	}

	@Override
	public void register(String name, AppenderProvider appenderProvider) {
		providers.put(name, appenderProvider);

	}

}
