package io.jstach.rainbowgum.slf4j.spi;

import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.ForwardingLogger;
import io.jstach.rainbowgum.slf4j.WrappingLogger;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * <strong>EXPERIMENTAL:</strong> this rainbowgum service provider allows wrapping slf4j
 * loggers created from the logger factory. <em>This allows for custom filtering using the
 * SLF4J api directly!</em>
 * <p>
 * If there are multiple registrations {@link #order()} and then {@link #name()} will be
 * used to sort where the lower order number and name will
 * {@linkplain #decorate(RainbowGum, DepthAwareLogger, int) decorate} first. See
 * {@link RainbowGumServiceProvider} for details on how to register.
 * <p>
 * <strong>IMPORTANT implementation detail</strong> is that you should not call the SLF4J
 * logger factory otherwise a stackoverflow or similar will happen.
 *
 * @see ForwardingLogger
 * @see RainbowGumServiceProvider
 */
public abstract class LoggerDecoratorService implements RainbowGumServiceProvider.Configurator {

	/**
	 * No arg constructor for service loader.
	 */
	public LoggerDecoratorService() {

	}

	@Override
	public final boolean configure(LogConfig config, Pass pass) {
		config.serviceRegistry().put(LoggerDecoratorService.class, name(), this);
		return true;
	}

	/**
	 * The name of the decorator and should be unique to avoid collisions.
	 * @return name.
	 */
	public abstract String name();

	/**
	 * Lowest integer value will decorate first which means the highest order found
	 * actually has the strongest influence on filtering since its returned logger is the
	 * one used.
	 * @return order.
	 */
	public int order() {
		return 0;
	}

	/**
	 * Decorate a logger. To not decorate simply return the previous logger.
	 * @param rainbowGum rainbow gum passed to the slf4j factory.
	 * @param previousLogger the previous logger in the chain.
	 * @param depth amount of times the logger has been wrapped. If the inputted logger is
	 * returned this number does not increase.
	 * @return decorated logger and if decorated ideally one that implements
	 * {@link WrappingLogger} so that caller info depth is retained.
	 */
	public abstract Logger decorate(RainbowGum rainbowGum, DepthAwareLogger previousLogger, int depth);

	/**
	 * Because wrapping can change the depth of the logger in the callstack this interface
	 * allows loggers to recreate themselves with the proper depth if they support it.
	 */
	public interface DepthAwareLogger extends Logger {

		/**
		 * Will recreate the logger with desired depth.
		 * @param depth new depth.
		 * @return logger with new depth.
		 */
		public Logger withDepth(int depth);

		/**
		 * Will recreate the logger with desired depth if possible.
		 * @param logger to check.
		 * @param depth new depth.
		 * @return logger with new depth or the same logger if not possible.
		 */
		public static Logger withDepth(Logger logger, int depth) {
			if (logger instanceof DepthAwareLogger da) {
				return da.withDepth(depth);
			}
			return logger;
		}

	}

	/**
	 * Because wrapping can change the depth of the logger in the callstack this interface
	 * allows implementations to change the depth of the event builder.
	 */
	public interface DepthAwareEventBuilder extends LoggingEventBuilder {

		/**
		 * Sets the depth of an event builder.
		 * @param depth sets depth for caller info.
		 * @return this.
		 */
		public LoggingEventBuilder setDepth(int depth);

		/**
		 * Will recreate the logger with desired depth if possible.
		 * @param eventBuilder event builder to check.
		 * @param depth new depth.
		 * @return logger with new depth or the same logger if not possible.
		 */
		public static LoggingEventBuilder setDepth(LoggingEventBuilder eventBuilder, int depth) {
			if (eventBuilder instanceof DepthAwareEventBuilder da) {
				return da.setDepth(depth);
			}
			return eventBuilder;
		}

		/**
		 * Redirects the output.
		 * @param logger logger.
		 * @return this.
		 */
		LoggingEventBuilder setLogger(LogEventLogger logger);

	}

}
