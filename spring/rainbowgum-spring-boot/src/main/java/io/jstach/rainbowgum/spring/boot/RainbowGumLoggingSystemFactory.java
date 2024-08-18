package io.jstach.rainbowgum.spring.boot;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.core.env.Environment;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Creates the RainbowGum LoggingSystem that gets called by Spring Boot initializing its
 * logging.
 */
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
			return environment.getProperty(key);
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
				.build();
			rainbowGum = RainbowGum.builder(config).set();
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
