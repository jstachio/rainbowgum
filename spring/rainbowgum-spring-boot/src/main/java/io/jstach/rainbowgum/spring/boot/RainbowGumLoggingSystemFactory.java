package io.jstach.rainbowgum.spring.boot;

import java.lang.System.Logger.Level;
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

import io.jstach.rainbowgum.LogConfig;
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
		/*
		 * Rainbow Gum uses logging.level as the root but strangely probably because of
		 * binding boot uses the below.
		 */
		private static final String ROOT_LEVEL = "logging.level.root";

		@Override
		public @Nullable String valueOrNull(String key) {
			if (key.equals("logging.level")) {
				String value = environment.getProperty(ROOT_LEVEL);
				if (value != null) {
					return value;
				}
			}
			return environment.getProperty(key);
		}
	}

	final static class Patterns {

		static final String NAME_AND_GROUP = "%esb(){APPLICATION_NAME}%esb{APPLICATION_GROUP}";

		@Nullable
		String CONSOLE_LOG_PATTERN;

		@Nullable
		String FILE_LOG_PATTERN;

		String LOG_DATEFORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

		String LOG_LEVEL_PATTERN = "%5p";

		String PID = "";

		String LOG_CORRELATION_PATTERN = "";

		String LOG_EXCEPTION_CONVERSION_WORD = "%wEx";

		Patterns(LogProperties properties) {
			CONSOLE_LOG_PATTERN = properties.valueOrNull("CONSOLE_LOG_PATTERN");
			FILE_LOG_PATTERN = properties.valueOrNull("FILE_LOG_PATTERN");
			LOG_DATEFORMAT_PATTERN = properties.value("LOG_DATEFORMAT_PATTERN", LOG_DATEFORMAT_PATTERN);
			LOG_LEVEL_PATTERN = properties.value("LOG_LEVEL_PATTERN", LOG_LEVEL_PATTERN);
			PID = properties.value("PID", PID);
			LOG_CORRELATION_PATTERN = properties.value("LOG_CORRELATION_PATTERN", LOG_CORRELATION_PATTERN);
			LOG_EXCEPTION_CONVERSION_WORD = properties.value("LOG_EXCEPTION_CONVERSION_WORD",
					LOG_EXCEPTION_CONVERSION_WORD);
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
					gum.router()
						.eventBuilder(getClass().getName(), Level.INFO)
						.message("Rainbow Gum already loaded! Config will not be driven by Spring.")
						.log();
					return;
				}
			}
			LogConfig config = LogConfig.builder()
				.properties(new SpringLogProperties(initializationContext.getEnvironment()))
				.serviceLoader(ServiceLoader.load(RainbowGumServiceProvider.class, classLoader))
				.configurator(new SpringBootPatternKeywordProvider())
				.build();
			LogProperties patternProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			Patterns patterns = new Patterns(patternProperties);
			var consoleEncoder = new PatternEncoderBuilder("console").pattern(patterns.consolePattern())
				.build()
				.provide("console", config);

			var fileEncoder = new PatternEncoderBuilder("file").pattern(patterns.filePattern())
				.build()
				.provide("file", config);

			config.encoderRegistry().setEncoderForOutputType(OutputType.CONSOLE_OUT, () -> consoleEncoder);
			config.encoderRegistry().setEncoderForOutputType(OutputType.FILE, () -> fileEncoder);
			rainbowGum = findAndSet(config, classLoader, initializationContext.getEnvironment());
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
			// TODO Auto-generated method stub
			return super.getShutdownHandler();
		}

		@Override
		public Set<LogLevel> getSupportedLogLevels() {
			return LogLevels.SUPPORTED;
		}

		@Override
		public void setLogLevel(String loggerName, LogLevel level) {
			// TODO Auto-generated method stub
			super.setLogLevel(loggerName, level);
		}

		@Override
		public List<LoggerConfiguration> getLoggerConfigurations() {
			// TODO Auto-generated method stub
			return super.getLoggerConfigurations();
		}

		@Override
		public LoggerConfiguration getLoggerConfiguration(String loggerName) {
			// TODO Auto-generated method stub
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
