package io.jstach.rainbowgum;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperty.Property;

/**
 * Register appenders by name. TODO probably can remove this.
 */
sealed interface LogAppenderRegistry permits DefaultAppenderRegistry {

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

	static final Property<URI> fileProperty = Property.builder().toURI().build(LogProperties.FILE_PROPERTY);

	static final Property<List<String>> appendersProperty = Property.builder()
		.toList()
		.orElse(List.of())
		.build(LogProperties.APPENDERS_PROPERTY);

	/*
	 * TODO The shit in here is a mess because auto configuration of appenders based on
	 * properties is complicated particularly because we want to support Spring Boots
	 * configuration OOB.
	 */
	static List<LogAppender> defaultAppenders(LogConfig config, List<String> appenderNames) {
		List<LogAppender> appenders = new ArrayList<>();
		if (appenderNames.isEmpty()) {
			appenderNames = appendersProperty.get(config.properties()).value(List.of());
		}
		if (appenderNames.isEmpty()) {
			appenderNames = addDefaultAppenderNames(config);
		}
		appenderNames = appenderNames.stream().distinct().toList();
		for (String appenderName : appenderNames) {
			appenders.add(appender(appenderName, config));
		}
		return appenders;
	}

	private static List<String> addDefaultAppenderNames(LogConfig config) {
		List<String> appenderNames = new ArrayList<>();
		fileProperty.get(config.properties())
			.optional()
			.ifPresent(a -> appenderNames.add(LogAppender.FILE_APPENDER_NAME));
		appenderNames.add(LogAppender.CONSOLE_APPENDER_NAME);
		return appenderNames;
	}

	static LogAppender appender( //
			String name, LogConfig config) {
		if (name.equals(LogAppender.FILE_APPENDER_NAME)) {
			return fileAppender(config);
		}
		if (name.equals(LogAppender.CONSOLE_APPENDER_NAME)) {
			return defaultConsoleAppender(config);
		}
		var builder = new AppenderConfig(name, null, null);
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(builder, config, outputProperty, encoderProperty);
	}

	private static LogAppender defaultConsoleAppender(LogConfig config) {
		String name = LogAppender.CONSOLE_APPENDER_NAME;

		var output = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config) //
			.get(config.properties()) //
			.value(() -> LogOutput.ofStandardOut().provide(name, config));

		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);

		var encoder = resolveEncoder(config, output, encoderProperty);

		var consoleAppender = LogAppender.builder(LogAppender.CONSOLE_APPENDER_NAME)
			.output(output)
			.encoder(encoder)
			.build()
			.provide(LogAppender.CONSOLE_APPENDER_NAME, config);
		return consoleAppender;
	}

	static LogAppender fileAppender(LogConfig config) {
		final String name = LogAppender.FILE_APPENDER_NAME;
		Property<LogOutput> fileProperty = Property.builder() //
			.toURI() //
			.mapResult(u -> LogOutput.of(LogProviderRef.of(u)))
			.withKey(LogProperties.FILE_PROPERTY)
			.addKeyWithName(LogAppender.APPENDER_OUTPUT_PROPERTY, name)
			.build()
			.map(p -> p.provide(name, config));
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(name, config, fileProperty, encoderProperty);
	}

	@SuppressWarnings("null") // TODO Eclipse Null bug.
	private static final Property<Boolean> defaultsAppenderBufferProperty = Property.builder()
		.map(s -> Boolean.parseBoolean(s))
		.orElse(false)
		.build(LogProperties.concatKey("defaults.appender.buffer"));

	static LogAppender appender( //
			AppenderConfig appenderConfig, //
			LogConfig config) {
		String name = appenderConfig.name();
		var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(appenderConfig, config, outputProperty, encoderProperty);
	}

	static LogAppender appender( //
			AppenderConfig appenderConfig, //
			LogConfig config, //
			Property<LogOutput> outputProperty, //
			Property<LogEncoder> encoderProperty) {

		@Nullable
		LogOutput output = appenderConfig.output();
		@Nullable
		LogEncoder encoder = appenderConfig.encoder();
		var properties = config.properties();

		if (output == null) {
			output = outputProperty.get(properties).value();
		}

		if (output instanceof LogEncoder e) {
			encoder = e;
		}

		if (encoder == null) {
			encoder = resolveEncoder(config, output, encoderProperty);
		}

		return defaultsAppenderBufferProperty.get(properties).value() ? new BufferLogAppender(output, encoder)
				: new DefaultLogAppender(output, encoder);
	}

	private static LogEncoder resolveEncoder(LogConfig config, LogOutput output, Property<LogEncoder> encoderProperty) {
		@Nullable
		LogEncoder encoder;
		var _output = output;
		encoder = encoderProperty.get(config.properties()).value(() -> {
			var encoderRegistry = config.encoderRegistry();
			return encoderRegistry.encoderForOutputType(_output.type());
		});
		return encoder;
	}

	static LogAppender appender( //
			String name, //
			LogConfig config, //
			Property<LogOutput> outputProperty, Property<LogEncoder> encoderProperty) {
		var builder = new AppenderConfig(name, null, null);
		return appender(builder, config, outputProperty, encoderProperty);

	}

	private static Property<LogOutput> outputProperty(String propertyName, String name, LogConfig config) {
		return Property.builder() //
			.toURI() //
			.mapResult(u -> LogOutput.of(LogProviderRef.of(u))) //
			.map(p -> p.provide(name, config))
			.buildWithName(propertyName, name);
	}

	private static Property<LogEncoder> encoderProperty(String propertyName, String name, LogConfig config) {
		return Property.builder() //
			.toURI() //
			.map(u -> LogEncoder.of(u)) //
			.map(p -> p.provide(name, config))
			.buildWithName(propertyName, name);
	}

}
