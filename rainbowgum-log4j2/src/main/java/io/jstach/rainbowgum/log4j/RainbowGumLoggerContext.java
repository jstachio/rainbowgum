package io.jstach.rainbowgum.log4j;

import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerRegistry;
import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.RainbowGum;

/**
 * A single, stateless {@link LoggerContext}: RainbowGum itself is process-global (see
 * {@link io.jstach.rainbowgum.LogRouter#global()}), so unlike Log4j2's own
 * {@code LoggerContext} implementations (one per classloader/webapp), one instance covers
 * the whole JVM. {@link LoggerRegistry} is the same name/{@link MessageFactory} keyed
 * cache, and {@code null} messageFactory handling (default to
 * {@link ParameterizedMessageFactory#INSTANCE}), Log4j2's own bundled
 * {@code SimpleLoggerContext} uses (confirmed by decompiling it).
 * <p>
 * Unlike SLF4J (whose {@code org.slf4j.spi.SLF4JServiceProvider#initialize()} is called
 * automatically by {@code org.slf4j.LoggerFactory}'s own static init, giving
 * {@code rainbowgum-slf4j} a natural one-time hook to eagerly call
 * {@link RainbowGum#of()}), Log4j2's
 * {@link org.apache.logging.log4j.spi.Provider}/{@link org.apache.logging.log4j.spi.LoggerContextFactory}
 * contract has no such callback. {@link #INSTANCE}'s lazy, once-only static field
 * initialization is used as the equivalent hook instead. Confirmed empirically that
 * without this call, a fresh
 * {@link org.apache.logging.log4j.LogManager#getLogger(String)} only reaches RainbowGum's
 * failsafe (errors-only) fallback until something else happens to bind a real
 * {@link RainbowGum} first.
 */
final class RainbowGumLoggerContext implements LoggerContext {

	static final RainbowGumLoggerContext INSTANCE = new RainbowGumLoggerContext();

	private static final MessageFactory DEFAULT_MESSAGE_FACTORY = ParameterizedMessageFactory.INSTANCE;

	private final LoggerRegistry<ExtendedLogger> registry = new LoggerRegistry<>();

	private RainbowGumLoggerContext() {
		RainbowGum.of();
	}

	@Override
	public @Nullable Object getExternalContext() {
		return null;
	}

	@Override
	public ExtendedLogger getLogger(String name) {
		return getLogger(name, DEFAULT_MESSAGE_FACTORY);
	}

	@Override
	public ExtendedLogger getLogger(String name, @Nullable MessageFactory messageFactory) {
		var mf = messageFactory == null ? DEFAULT_MESSAGE_FACTORY : messageFactory;
		var existing = registry.getLogger(name, mf);
		if (existing != null) {
			return existing;
		}
		registry.putIfAbsent(name, mf, new RainbowGumLogger(name, mf));
		return registry.getLogger(name, mf);
	}

	@Override
	public boolean hasLogger(String name) {
		return registry.hasLogger(name, DEFAULT_MESSAGE_FACTORY);
	}

	@Override
	public boolean hasLogger(String name, @Nullable MessageFactory messageFactory) {
		return registry.hasLogger(name, messageFactory == null ? DEFAULT_MESSAGE_FACTORY : messageFactory);
	}

	@Override
	public boolean hasLogger(String name, Class<? extends MessageFactory> messageFactoryClass) {
		return registry.hasLogger(name, messageFactoryClass);
	}

}
