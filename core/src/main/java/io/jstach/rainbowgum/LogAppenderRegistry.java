package io.jstach.rainbowgum;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogProperty.Property;
import io.jstach.rainbowgum.LogProperty.PropertyValue;
import io.jstach.rainbowgum.LogProperty.Result;

/**
 * Register appenders by name. TODO probably can remove this.
 */
sealed interface LogAppenderRegistry permits DefaultAppenderRegistry {

}

record AppenderConfig(String name, @Nullable LogOutput output, @Nullable LogEncoder encoder,
		@Nullable Set<AppenderFlag> flags) {

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

	static final Property<URI> fileProperty = Property.builder().ofURI().build(LogProperties.FILE_PROPERTY);

	/*
	 * TODO The shit in here is a mess because auto configuration of appenders based on
	 * properties is complicated particularly because we want to support Spring Boots
	 * configuration OOB.
	 */
	static List<LogProvider<LogAppender>> appenders(LogConfig config, String routeName) {
		var b = Property.builder() //
			.ofList() //
			.withKey(LogProperties.ROUTE_APPENDERS_PROPERTY) //
			.addNameParam(routeName);
		if (routeName.equals(LogProperties.DEFAULT_NAME)) {
			b.addKey(LogProperties.APPENDERS_PROPERTY);
		}
		var appenderNamesProperty = b.build();

		Result<List<String>> result = appenderNamesProperty.get(config.properties());

		if (routeName.equals(LogProperties.DEFAULT_NAME)) {
			result = result.or(() -> addDefaultAppenderNames(config));
		}

		var _r = result;
		return result.<List<LogProvider<LogAppender>>>map(appenderNames -> {
			List<LogProvider<LogAppender>> appenders = new ArrayList<>();
			for (String appenderName : appenderNames.stream().distinct().toList()) {
				appenders.add(appender(appenderName)
					.describe("Appender: '" + appenderName + "' from property: " + _r.describe()));
			}
			return appenders;
		}).value();

		// List<LogProvider<LogAppender>> appenders = new ArrayList<>();
		// var _appenderNames = result.value().stream().distinct().toList();
		// for (String appenderName : _appenderNames) {
		// appenders.add(appender(appenderName));
		// }
		// return appenders;
	}

	private static List<String> addDefaultAppenderNames(LogConfig config) {
		List<String> appenderNames = new ArrayList<>();
		fileProperty.get(config.properties())
			.optional()
			.ifPresent(a -> appenderNames.add(LogAppender.FILE_APPENDER_NAME));
		appenderNames.add(LogAppender.CONSOLE_APPENDER_NAME);
		return appenderNames;
	}

	static LogProvider<LogAppender> appender(String name) {
		return (_n, config) -> {
			if (name.equals(LogAppender.FILE_APPENDER_NAME)) {
				return fileAppender(config);
			}
			if (name.equals(LogAppender.CONSOLE_APPENDER_NAME)) {
				return defaultConsoleAppender(config);
			}
			var builder = new AppenderConfig(name, null, null, null);
			var outputProperty = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config);
			var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
			return appender(builder, config, outputProperty, encoderProperty);
		};
	}

	private static LogAppender defaultConsoleAppender(LogConfig config) {
		String name = LogAppender.CONSOLE_APPENDER_NAME;

		var output = outputProperty(LogAppender.APPENDER_OUTPUT_PROPERTY, name, config) //
			.get() //
			.value(() -> LogOutput.ofStandardOut().provide(name, config));

		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);

		var encoder = resolveEncoder(name, config, output, encoderProperty).value();

		var flags = resolveFlags(config, name);

		var consoleAppender = LogAppender.builder(LogAppender.CONSOLE_APPENDER_NAME)
			.output(output)
			.encoder(encoder)
			.flags(flags)
			.build()
			.provide(LogAppender.CONSOLE_APPENDER_NAME, config);
		return consoleAppender;
	}

	private static Set<AppenderFlag> resolveFlags(LogConfig config, String name) {
		return Property.builder() //
			.ofList() //
			.map(AppenderFlag::parse) //
			.buildWithName(LogAppender.APPENDER_FLAGS_PROPERTY, name) //
			.get(config.properties())
			.value(EnumSet.noneOf(LogAppender.AppenderFlag.class));
	}

	static LogAppender fileAppender(LogConfig config) {
		final String name = LogAppender.FILE_APPENDER_NAME;
		PropertyValue<LogOutput> fileProperty = Property.builder() //
			.ofProvider(LogOutput::of)
			.map(p -> p.provide(name, config))
			.withKey(LogProperties.FILE_PROPERTY)
			.addKeyWithName(LogAppender.APPENDER_OUTPUT_PROPERTY, name)
			.build()
			.bind(fileProperties(config.properties()));
		var encoderProperty = encoderProperty(LogAppender.APPENDER_ENCODER_PROPERTY, name, config);
		return appender(name, config, fileProperty, encoderProperty);
	}

	/*
	 * Unlike the generic appender output property (which falls back to here and is
	 * genuinely ambiguous - a bare word could be a deliberate URI scheme like "stdout")
	 * LogProperties.FILE_PROPERTY (Spring Boot's logging.file.name) is documented to
	 * always be a file path. Rewrite its value to a resolved file URI before it reaches
	 * the generic URI-scheme-sniffing property pipeline so callers do not need to know
	 * about the "./" prefix workaround required for ordinary output properties.
	 *
	 * The original per-source LogProperties (and thus its description/diagnostics) is
	 * preserved via FoundProperty.StringProperty so error messages still say which
	 * underlying source (system properties, environment variables, etc.) the value came
	 * from. Values that are already a proper URI with an explicit scheme (e.g.
	 * file:///...) or that fail to parse as a URI at all are passed through untouched -
	 * the former is already unambiguous and the latter should surface its original, well
	 * understood URI syntax error rather than being silently reinterpreted as a literal
	 * (almost certainly wrong) file name.
	 */
	private static LogProperties fileProperties(LogProperties properties) {
		return new LogProperties() {
			@Override
			public @Nullable String valueOrNull(String key) {
				String v = properties.valueOrNull(key);
				if (v != null && key.equals(LogProperties.FILE_PROPERTY)) {
					return normalizeFileValue(v);
				}
				return v;
			}

			@Override
			public LogProperties.FoundProperty.@Nullable StringProperty stringPropertyOrNull(String key) {
				var found = properties.stringPropertyOrNull(key);
				if (found != null && key.equals(LogProperties.FILE_PROPERTY)) {
					return new LogProperties.FoundProperty.StringProperty(found.properties(), key,
							normalizeFileValue(found.value()));
				}
				return found;
			}

			@Override
			public String description(String key) {
				return properties.description(key);
			}

			@Override
			public int order() {
				return properties.order();
			}
		};
	}

	private static String normalizeFileValue(String value) {
		URI uri;
		try {
			uri = new URI(value);
		}
		catch (URISyntaxException e) {
			return value;
		}
		if (uri.getScheme() != null) {
			return value;
		}
		return Paths.get(value).toUri().toString();
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
			AppenderConfig appenderConfig, //
			LogConfig config, //
			PropertyValue<LogOutput> outputProperty, //
			PropertyValue<LogEncoder> encoderProperty) {

		LogOutput output = outputProperty.override(appenderConfig.output());

		@Nullable
		LogEncoder encoder = appenderConfig.encoder();

		if (output instanceof LogEncoder e) {
			encoder = e;
		}
		String name = appenderConfig.name();

		encoder = resolveEncoder(name, config, output, encoderProperty).override(encoder);

		@Nullable
		Set<LogAppender.AppenderFlag> flags = appenderConfig.flags();
		if (flags == null) {
			flags = resolveFlags(config, name);
		}

		return DirectLogAppender.of(name, output, encoder, flags);
	}

	private static PropertyValue<LogEncoder> resolveEncoder(String name, LogConfig config, LogOutput output,
			PropertyValue<LogEncoder> encoderProperty) {
		return encoderProperty.or(() -> {
			var encoderRegistry = config.encoderRegistry();
			return encoderRegistry.encoderForOutputType(output.type()).provide(name, config);
		});
	}

	static LogAppender appender( //
			String name, //
			LogConfig config, //
			PropertyValue<LogOutput> outputProperty, PropertyValue<LogEncoder> encoderProperty) {
		var builder = new AppenderConfig(name, null, null, null);
		return appender(builder, config, outputProperty, encoderProperty);

	}

	private static PropertyValue<LogOutput> outputProperty(String propertyKey, String name, LogConfig config) {
		return Property.builder() //
			.ofProvider(LogOutput::of)
			.map(p -> p.provide(name, config))
			.buildWithName(propertyKey, name)
			.bind(config.properties());
	}

	private static PropertyValue<LogEncoder> encoderProperty(String propertyKey, String name, LogConfig config) {
		return Property.builder() //
			.ofProvider(LogEncoder::of)
			.map(p -> p.provide(name, config))
			.buildWithName(propertyKey, name)
			.bind(config.properties());
	}

}
