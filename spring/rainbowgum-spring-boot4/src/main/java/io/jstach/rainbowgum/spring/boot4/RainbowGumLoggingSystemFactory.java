package io.jstach.rainbowgum.spring.boot4;

import java.lang.System.Logger.Level;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.ServiceLoader;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiStyle;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.SpringFactoriesLoader;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;

/**
 * Creates the RainbowGum LoggingSystem that gets called by Spring Boot initializing its
 * logging.
 */
@Order(value = Ordered.HIGHEST_PRECEDENCE)
public class RainbowGumLoggingSystemFactory implements LoggingSystemFactory {

	/**
	 * Spring calls.
	 */
	public RainbowGumLoggingSystemFactory() {
	}

	@Override
	public LoggingSystem getLoggingSystem(ClassLoader classLoader) {
		return new RainbowGumLoggingSystem(classLoader);
	}

	record SpringLogProperties(Environment environment) implements LogProperties {

		@Override
		public @Nullable String valueOrNull(String key) {
			if (key.equals(SpringBootSupportedProperties.LOGGING_LEVEL)) {
				String value = environment.getProperty(SpringBootSupportedProperties.LOGGING_LEVEL_ROOT);
				if (value != null) {
					return value;
				}
			}
			else if (key.equals(LogProperties.FILE_PROPERTY)) {
				String name = environment.getProperty(key);
				if (name != null && !name.isBlank()) {
					return name;
				}
				String path = environment.getProperty(SpringBootSupportedProperties.FILE_PATH);
				if (path != null && !path.isBlank()) {
					// Spring Boot's own default file name when only the directory is
					// given - see LogFile.get(...).
					return Path.of(path, "spring.log").toString();
				}
				return null;
			}
			else if (key.equals(LogProperties.APPENDERS_PROPERTY)) {
				String value = environment.getProperty(key);
				if (value != null) {
					return value;
				}
				boolean consoleEnabled = environment.getProperty(SpringBootSupportedProperties.CONSOLE_ENABLED,
						Boolean.class, true);
				// Only meaningful to restrict to just "file" if a file destination
				// actually resolves - otherwise fall through to the normal default
				// (console) rather than pointing at a nonexistent file appender.
				if (!consoleEnabled && valueOrNull(LogProperties.FILE_PROPERTY) != null) {
					return LogAppender.FILE_APPENDER_NAME;
				}
				return null;
			}
			else if (key.equals(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY)) {
				String ansiEnabled = environment.getProperty(SpringBootSupportedProperties.OUTPUT_ANSI_ENABLED);
				if (ansiEnabled != null) {
					String mapped = switch (ansiEnabled.toUpperCase(Locale.ROOT)) {
						case "NEVER" -> "true";
						case "ALWAYS" -> "false";
						// DETECT or unrecognized - let RainbowGum's own auto-detection
						// run rather than overriding it.
						default -> null;
					};
					if (mapped != null) {
						return mapped;
					}
				}
			}
			return environment.getProperty(key);
		}
	}

	final static class Patterns {

		final String NAME_AND_GROUP;

		@Nullable
		String CONSOLE_LOG_PATTERN;

		@Nullable
		String FILE_LOG_PATTERN;

		String LOG_DATEFORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

		String LOG_LEVEL_PATTERN = "%5p";

		String PID = "";

		String LOG_CORRELATION_PATTERN = "";

		String LOG_EXCEPTION_CONVERSION_WORD = "%wEx";

		Patterns(LogProperties properties, Environment environment) {
			CONSOLE_LOG_PATTERN = properties.valueOrNull("CONSOLE_LOG_PATTERN");
			FILE_LOG_PATTERN = properties.valueOrNull("FILE_LOG_PATTERN");
			LOG_DATEFORMAT_PATTERN = properties.value("LOG_DATEFORMAT_PATTERN", LOG_DATEFORMAT_PATTERN);
			LOG_LEVEL_PATTERN = properties.value("LOG_LEVEL_PATTERN", LOG_LEVEL_PATTERN);
			PID = properties.value("PID", PID);
			LOG_CORRELATION_PATTERN = properties.value("LOG_CORRELATION_PATTERN", LOG_CORRELATION_PATTERN);
			LOG_EXCEPTION_CONVERSION_WORD = properties.value("LOG_EXCEPTION_CONVERSION_WORD",
					LOG_EXCEPTION_CONVERSION_WORD);
			/*
			 * Spring Boot doesn't bridge these two toggles to system properties (unlike
			 * APPLICATION_NAME/APPLICATION_GROUP themselves), so they're read straight
			 * from the Environment rather than through the system-property-backed
			 * `properties` above.
			 */
			boolean includeApplicationName = environment
				.getProperty(SpringBootSupportedProperties.INCLUDE_APPLICATION_NAME, Boolean.class, true);
			boolean includeApplicationGroup = environment
				.getProperty(SpringBootSupportedProperties.INCLUDE_APPLICATION_GROUP, Boolean.class, true);
			NAME_AND_GROUP = (includeApplicationName ? "%esb(){APPLICATION_NAME}" : "")
					+ (includeApplicationGroup ? "%esb{APPLICATION_GROUP}" : "");
		}

		String consolePattern() {
			if (CONSOLE_LOG_PATTERN != null) {
				return CONSOLE_LOG_PATTERN;
			}
			return //
			faint(datetime()) + //
					" " + //
					colorByLevel(LOG_LEVEL_PATTERN) + //
					" " + //
					magenta(PID) + //
					" " + //
					faint("--- " + NAME_AND_GROUP + "[%15.15t]" + " " + LOG_CORRELATION_PATTERN) + //
					cyan("%-40.40logger{39}") + //
					" " + faint(":") + //
					" %m%n" + //
					LOG_EXCEPTION_CONVERSION_WORD;
		}

		String filePattern() {
			if (FILE_LOG_PATTERN != null) {
				return FILE_LOG_PATTERN;
			}
			return datetime() + " " + LOG_LEVEL_PATTERN + " " + PID + " --- " + //
					NAME_AND_GROUP + "[%t]" + " " + LOG_CORRELATION_PATTERN + //
					"%-40.40logger{39} : %m%n" + LOG_EXCEPTION_CONVERSION_WORD;
		}

		String datetime() {
			return "%d{" + LOG_DATEFORMAT_PATTERN + "}";
		}

		private static String faint(String value) {
			return color(value, AnsiStyle.FAINT.name().toLowerCase(Locale.ROOT));
		}

		private static String cyan(String value) {
			return color(value, AnsiColor.CYAN.name().toLowerCase(Locale.ROOT));
		}

		private static String magenta(String value) {
			return color(value, AnsiColor.MAGENTA.name().toLowerCase(Locale.ROOT));
		}

		private static String colorByLevel(String value) {
			return "%clr(" + value + "){}";
		}

		private static String color(String value, String color) {
			return "%clr(" + value + "){" + color + "}";
		}

	}

	final static class RainbowGumLoggingSystem extends LoggingSystem {

		/*
		 * TODO usually holding onto a class loader is a bad because of potential GC or
		 * class reloading issues. Hopefully this ok. Perhaps a weak reference is better.
		 */
		private final ClassLoader classLoader;

		private volatile @Nullable RainbowGum rainbowGum;

		RainbowGumLoggingSystem(ClassLoader classLoader) {
			super();
			this.classLoader = classLoader;
		}

		@Override
		public void beforeInitialize() {
		}

		@Override
		public void initialize(LoggingInitializationContext initializationContext, String configLocation,
				LogFile logFile) {
			var gum = RainbowGum.getOrNull();
			if (gum != null) {
				if (gum.config().serviceRegistry().findOrNull(PreBootRainbowGumProvider.BootFlag.class) == null) {
					var route = gum.router().route(getClass().getName(), Level.INFO);
					if (route.isEnabled()) {
						var currentThread = Thread.currentThread();
						var event = LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(),
								Level.INFO, getClass().getName(),
								"Rainbow Gum already loaded! Config will not be driven by Spring.", KeyValues.of(),
								null);
						route.log(event);
					}
					return;
				}
			}
			LogConfig config = LogConfig.builder()
				.properties(new SpringLogProperties(initializationContext.getEnvironment()))
				.serviceLoader(ServiceLoader.load(RainbowGumServiceProvider.class, classLoader))
				.configurator(new SpringBootPatternKeywordProvider())
				.build();
			Environment environment = initializationContext.getEnvironment();
			LogProperties patternProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			Patterns patterns = new Patterns(patternProperties, environment);

			var consoleEncoder = new PatternEncoderBuilder("console").pattern(patterns.consolePattern())
				.charset(environment.getProperty(SpringBootSupportedProperties.CHARSET_CONSOLE, Charset.class))
				.build();

			var fileEncoder = new PatternEncoderBuilder("file").pattern(patterns.filePattern())
				.charset(environment.getProperty(SpringBootSupportedProperties.CHARSET_FILE, Charset.class))
				.build();

			config.encoderRegistry().setEncoderForOutputType(OutputType.CONSOLE_OUT, consoleEncoder);
			config.encoderRegistry().setEncoderForOutputType(OutputType.FILE, fileEncoder);
			StructuredLogging.apply(config, environment);
			rainbowGum = findAndSet(config, classLoader, environment);
		}

		@Override
		public void cleanUp() {
			/*
			 * TODO hmm is it a good idea to close here or rather use shutdown handler.
			 */
			var gum = rainbowGum;
			if (gum != null) {
				gum.close();
			}
		}

		@Override
		public Runnable getShutdownHandler() {
			return super.getShutdownHandler();
		}

		@Override
		public Set<LogLevel> getSupportedLogLevels() {
			return LogLevels.SUPPORTED;
		}

		@Override
		public void setLogLevel(String loggerName, LogLevel level) {
			super.setLogLevel(loggerName, level);
		}

		@Override
		public List<LoggerConfiguration> getLoggerConfigurations() {
			return super.getLoggerConfigurations();
		}

		@Override
		public LoggerConfiguration getLoggerConfiguration(String loggerName) {
			return super.getLoggerConfiguration(loggerName);
		}

		public static RainbowGum findAndSet(LogConfig config, ClassLoader classLoader, Environment environment) {
			var gum = SpringFactoriesLoader.loadFactories(SpringRainbowGumServiceProvider.class, classLoader)
				.stream()
				.flatMap(p -> p.provide(config, classLoader, environment).stream())
				.findFirst()
				.orElseGet(() -> {
					return RainbowGum.builder(config).build();
				});
			return RainbowGum.set(gum).get();
		}

	}

	enum LogLevels {

		;
		public static final Set<LogLevel> SUPPORTED = supported();

		private static Set<LogLevel> supported() {
			return Set.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.OFF);
		}

		public static LogLevel fromSystem(Level level) {
			return switch (level) {
				case ALL -> LogLevel.TRACE;
				case TRACE -> LogLevel.TRACE;
				case DEBUG -> LogLevel.DEBUG;
				case INFO -> LogLevel.INFO;
				case WARNING -> LogLevel.WARN;
				case ERROR -> LogLevel.ERROR;
				case OFF -> LogLevel.OFF;
			};
		}

	}

}
