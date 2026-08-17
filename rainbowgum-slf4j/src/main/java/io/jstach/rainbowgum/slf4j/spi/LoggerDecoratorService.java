package io.jstach.rainbowgum.slf4j.spi;

import org.eclipse.jdt.annotation.Nullable;
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
 * <h2>Writing a decorator</h2>
 *
 * Do not extend {@link org.slf4j.helpers.AbstractLogger} directly to implement the
 * returned {@link Logger}: getting caller-info depth right means depending on that
 * class's internal multi-arity dispatch shape, which is an unversioned SLF4J
 * implementation detail rather than a contract, and is easy to get subtly wrong. Extend
 * {@link AbstractFilteringLogger} instead - it owns and tests its own depth accounting so
 * a decorator author never has to reason about stack frames.
 * <p>
 * A decorator that samples DEBUG level events and surfaces any {@link org.slf4j.Marker}
 * as a key value (Rainbow Gum core has no built-in {@code Marker} support, so this is how
 * a decorator can still make use of one):
 *
 * {@snippet class = "snippets.DecoratorExample" region = "decoratorExample" }
 *
 * Once built, register it like any other {@link RainbowGumServiceProvider}. If your
 * application is modularized:
 *
 * {@snippet :
 *
 * provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with com.mycompany.DecoratorExample;
 *
 * }
 *
 * @see ForwardingLogger
 * @see RainbowGumServiceProvider
 * @see AbstractFilteringLogger
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

		/**
		 * The message set on this builder so far, if any. Plain
		 * {@link LoggingEventBuilder} has no such accessor, which makes read-modify-write
		 * use cases (e.g. prefixing) impossible without this.
		 * @return message or null if not set.
		 */
		@Nullable
		String message();

		/**
		 * The message set on the builder so far, if possible.
		 * @param eventBuilder event builder to check.
		 * @return message, or null if not set or not a {@link DepthAwareEventBuilder}.
		 */
		public static @Nullable String message(LoggingEventBuilder eventBuilder) {
			if (eventBuilder instanceof DepthAwareEventBuilder da) {
				return da.message();
			}
			return null;
		}

	}

}
