package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.ValidatedResult;

/**
 * Register appenders by name. TODO probably can remove this.
 */
sealed interface LogAppenderRegistry permits DefaultAppenderRegistry {

}

final class DefaultAppenderRegistry implements LogAppenderRegistry {

	static List<LogProvider<LogAppender>> appenders(LogConfig config, String routeName) {
		var keyed = config.properties().forKey(LogProperties.ROUTE_APPENDERS_PROPERTY, routeName);
		if (routeName.equals(LogProperties.DEFAULT_NAME)) {
			keyed = keyed.or(LogProperties.APPENDERS_PROPERTY);
		}

		Result<List<String>> result = keyed.ofList();

		if (routeName.equals(LogProperties.DEFAULT_NAME)) {
			result = result.or(() -> addDefaultAppenderNames(config));
		}

		var _r = result;
		/*
		 * Validated as a plain list of names, before any of them is used to build a
		 * LogProvider, so a bad name is reported against this list property itself
		 * (LogProperty.Validator.validateNames throws its own uncaught
		 * ValidationException) - not wrapped/reformatted by Result.map's own
		 * exception-to-rich-error handling, and not deferred until some unrelated
		 * {name}-keyed property (e.g. this appender's own output/encoder) happens to
		 * interpolate it first.
		 */
		List<String> appenderNames = rawValue(result).value().stream().distinct().toList();
		LogProperty.Validator.validateNames(LogAppender.class, "appender", _r, appenderNames);

		List<LogProvider<LogAppender>> appenders = new ArrayList<>();
		for (String appenderName : appenderNames) {
			appenders.add(appender(appenderName)
				.describe("Appender: '" + appenderName + "' from property: " + _r.describe()));
		}
		return appenders;
	}

	private static List<String> addDefaultAppenderNames(LogConfig config) {
		List<String> appenderNames = new ArrayList<>();
		rawValue(config.properties().forKey(LogProperties.FILE_PROPERTY).ofURI()).optional()
			.ifPresent(a -> appenderNames.add(LogAppender.FILE_APPENDER_NAME));
		appenderNames.add(LogAppender.CONSOLE_APPENDER_NAME);
		return appenderNames;
	}

	/*
	 * appenders() (a Missing route-appenders property is a genuine misconfiguration with
	 * no fallback for a non-default route), addDefaultAppenderNames() (an Error on the
	 * optional logging.file.name check must still surface, unwrapped), and
	 * LogAppender.Builder.build()'s own final "genuinely missing, no default" output
	 * fallback all need the raw, unwrapped exception a Missing/Error's own
	 * value()/optional() throws, not the "Validation failed for X:" wrapping a Validator
	 * would add, which would also change the exception's public type away from
	 * PropertyMissingException/PropertyConvertException for callers (see
	 * FileOutputPropertiesTest) that catch those specifically. LogProperty.Result no
	 * longer exposes value()/optional() itself (a bare Result might still be Missing), so
	 * this just narrows to LogProperty.ValidatedResult, true for every concrete Result,
	 * Missing included, without going through a Validator at all.
	 */
	static <T> ValidatedResult<T> rawValue(Result<T> result) {
		return switch (result) {
			case ValidatedResult<T> vr -> vr;
		};
	}

	static LogProvider<LogAppender> appender(String name) {
		return (_n, config) -> {
			if (name.equals(LogAppender.FILE_APPENDER_NAME)) {
				return fileAppender(config);
			}
			if (name.equals(LogAppender.CONSOLE_APPENDER_NAME)) {
				return defaultConsoleAppender(config);
			}
			return LogAppender.builder(name).build().provide(name, config);
		};
	}

	private static LogAppender defaultConsoleAppender(LogConfig config) {
		String name = LogAppender.CONSOLE_APPENDER_NAME;
		return LogAppender.builder(name).outputDefault(LogOutput.ofStandardOut()).build().provide(name, config);
	}

	static LogAppender fileAppender(LogConfig config) {
		final String name = LogAppender.FILE_APPENDER_NAME;
		/*
		 * Unchanged from before the appender-construction refactor unified everything
		 * else onto LogAppender.Builder: LogProperties.FILE_PROPERTY (Spring Boot's
		 * logging.file.name) is chained ahead of the generic
		 * logging.appender.file.output, and LogProperty.mapValue's own
		 * exception-to-rich-property-error wrapping around the URI-to-LogProviderRef
		 * conversion step (normalizeFileUri/LogProviderRef.of) both need the exact Result
		 * plumbing below, not something a plain builder setter can express without losing
		 * "Error converting property..."/"Tried: ..." context real
		 * FileOutputPropertiesTest cases assert on. Only the very last step changes: the
		 * fully-resolved LogOutput is handed to a real LogAppender.Builder (as an
		 * explicit value, so build() never re-resolves it from properties) instead of
		 * bypassing it via AppenderConfig.
		 */
		Result<URI> uriResult = config.properties()
			.forKey(LogProperties.FILE_PROPERTY)
			.or(LogAppender.APPENDER_OUTPUT_PROPERTY, name)
			.ofURI();
		Result<LogProviderRef> refResult = switch (uriResult) {
			case Result.Success<URI> s -> LogProperty.mapValue(s, LogProviderRef.of(normalizeFileUri(s), s.key()));
			case Result.Missing<URI> m -> m.convert();
			case Result.Error<URI> e -> e.convert();
		};
		LogOutput output = rawValue(LogProperty.provideValue(refResult.map(LogOutput::of), name, config)).value();
		return LogAppender.builder(name).output(output).build().provide(name, config);
	}

	/*
	 * Values that already have an explicit scheme (e.g. file:///...) are left as-is since
	 * they are already unambiguous; genuinely malformed values never reach here, since
	 * ofURI() itself already failed them with their original, well understood URI syntax
	 * error before this runs. Only a value that actually came from FILE_PROPERTY (not the
	 * generic, already-unambiguous APPENDER_OUTPUT_PROPERTY fallback) gets this
	 * treatment, checked via the key the value was actually found at, not which property
	 * this lookup started from.
	 */
	private static URI normalizeFileUri(Result.Success<URI> result) {
		URI uri = result.value();
		if (!LogProperties.FILE_PROPERTY.equals(result.key()) || uri.getScheme() != null) {
			return uri;
		}
		return Paths.get(uri.getPath()).toUri();
	}

}
