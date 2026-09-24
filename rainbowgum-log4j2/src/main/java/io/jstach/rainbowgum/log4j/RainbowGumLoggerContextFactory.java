package io.jstach.rainbowgum.log4j;

import java.net.URI;

import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.jspecify.annotations.Nullable;

/**
 * Constructed directly by {@link RainbowGumLog4jProvider}'s own constructor (passed as a
 * {@link Class} to the {@link org.apache.logging.log4j.spi.Provider} superclass, which
 * instantiates it reflectively via a public no-arg constructor on first use). Always
 * returns the single {@link RainbowGumLoggerContext#INSTANCE}, since RainbowGum's
 * {@link io.jstach.rainbowgum.LogRouter#global()} is itself process-global - there is no
 * per-classloader/per-application context to separate here.
 */
public final class RainbowGumLoggerContextFactory implements LoggerContextFactory {

	/**
	 * For reflective construction by {@link org.apache.logging.log4j.spi.Provider}.
	 */
	public RainbowGumLoggerContextFactory() {
	}

	@Override
	public LoggerContext getContext(String fqcn, ClassLoader loader, @Nullable Object externalContext,
			boolean currentContext) {
		return RainbowGumLoggerContext.INSTANCE;
	}

	@Override
	public LoggerContext getContext(String fqcn, ClassLoader loader, @Nullable Object externalContext,
			boolean currentContext, @Nullable URI configLocation, @Nullable String name) {
		return RainbowGumLoggerContext.INSTANCE;
	}

	@Override
	public void removeContext(LoggerContext context) {
	}

	@Override
	public boolean isClassLoaderDependent() {
		return false;
	}

}
