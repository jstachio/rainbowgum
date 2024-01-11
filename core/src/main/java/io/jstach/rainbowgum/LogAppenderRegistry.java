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

record AppenderConfig(String name, @Nullable LogOutput output, @Nullable LogEncoder encoder) {

	AppenderConfig {
		validateName(name);
	}

	private static String validateName(String name) {
		if (name.isBlank()) {
			throw new IllegalStateException("Appender name cannot be null. name=" + name);
		}
		if (name.contains(" ") || name.contains("\t") || name.contains("\n") || name.contains("\r")) {
			throw new IllegalStateException("Appender name cannot have whitespace");
		}
		if (name.contains(LogProperties.SEP)) {
			throw new IllegalStateException("Appender name cannot have '" + LogProperties.SEP + "'");
		}
		return name;
	}
}

final class DefaultAppenderRegistry implements LogAppenderRegistry {

	/*
	 * The shit in here is a mess because auto configuration of appenders based on
	 * properties is complicated particularly because we want to support Spring Boots
	 * configuration OOB.
	 */
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
			appenders.add(appender(appenderName, config));
		}
		if (appenders.isEmpty()) {
			var consoleAppender = defaultConsoleAppender(config);
			appenders.add(consoleAppender);
		}
		return appenders;
	}

	private static LogAppender defaultConsoleAppender(LogConfig config) {
		var consoleAppender = LogAppender.builder(LogAppender.CONSOLE_APPENDER_NAME)
			.output(LogOutput.ofStandardOut())
			.build()
			.provide(config);
		return consoleAppender;
	}

	static Optional<LogAppender> fileAppender(LogConfig config) {
		String name = LogAppender.FILE_APPENDER_NAME;
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return fileProperty //
			.map(u -> appender(name, config, outputProperty, encoderProperty))
			.get(config.properties())
			.optional();
	}

	private static final Property<Boolean> defaultsAppenderBufferProperty = Property.builder()
		.map(s -> Boolean.parseBoolean(s))
		.orElse(false)
		.build(LogProperties.concatKey("defaults.appender.buffer"));

	static LogAppender appender( //
			String name, LogConfig config) {
		var builder = new AppenderConfig(name, null, null);
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(builder, config, outputProperty, encoderProperty);
	}

	static LogAppender appender( //
			AppenderConfig appenderConfig, //
			LogConfig config) {
		String name = appenderConfig.name();
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(appenderConfig, config, outputProperty, encoderProperty);
	}

	static LogAppender appender( //
			AppenderConfig builder, //
			LogConfig config, //
			Property<LogOutput> outputProperty, Property<LogEncoder> encoderProperty) {

		@Nullable
		LogOutput output = builder.output();
		@Nullable
		LogEncoder encoder = builder.encoder();
		var properties = config.properties();

		if (output == null) {
			output = outputProperty.get(properties).value();
		}

		if (encoder == null) {
			var _output = output;
			encoder = encoderProperty.get(properties).value(() -> {
				var formatterRegistry = config.formatterRegistry();
				return LogEncoder.of(formatterRegistry.formatterForOutputType(_output.type()));
			});
		}

		return defaultsAppenderBufferProperty.get(properties).value() ? new BufferLogAppender(output, encoder)
				: new DefaultLogAppender(output, encoder);
	}

	static LogAppender appender( //
			String name, //
			LogConfig config, //
			Property<LogOutput> outputProperty, Property<LogEncoder> encoderProperty) {
		var appenderRegistry = config.appenderRegistry();
		var appender = appenderRegistry.appender(name).map(a -> a.provide(config)).orElse(null);
		if (appender != null) {
			return appender;
		}
		var builder = new AppenderConfig(name, null, null);
		return appender(builder, config, outputProperty, encoderProperty);

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
