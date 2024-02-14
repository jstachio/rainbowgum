package io.jstach.rainbowgum.slf4j.spi;

import org.slf4j.Logger;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.slf4j.ForwardingLogger;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * <strong>EXPERIMENTAL:</strong> this rainbowgum service provider allows wrapping slf4j
 * loggers created from the logger factory. <em>This allows for custom filtering using the
 * SLF4J api directly!</em>
 * <p>
 * If there are multiple registrations {@link #order()} and then {@link #name()} will be
 * used to sort where the lower order number and name will
 * {@linkplain #decorate(RainbowGum, Logger) decorate} first. See
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
	public final boolean configure(LogConfig config) {
		config.serviceRegistry().put(LoggerDecoratorService.class, this, name());
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
	 * @return decorated logger.
	 */
	public abstract Logger decorate(RainbowGum rainbowGum, Logger previousLogger);

}
