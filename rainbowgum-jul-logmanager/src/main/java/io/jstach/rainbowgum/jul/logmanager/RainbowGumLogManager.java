package io.jstach.rainbowgum.jul.logmanager;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * A {@link LogManager} implementation that vends {@link RainbowGumJULLogger}s, routing
 * every {@code java.util.logging.Logger} call directly through Rainbow Gum instead of the
 * normal Handler/Level dispatch. Modeled closely on
 * {@code org.apache.logging.log4j.jul.LogManager} (log4j-jul), including its guard
 * against reentrant {@link #getLogger(String)} calls.
 * <p>
 * <strong>Installation is a JVM-wide, one-shot decision made before this class is even
 * loaded.</strong> Set the system property {@code java.util.logging.manager} to this
 * class's fully qualified name, as a real {@code -D} JVM argument (or via
 * {@code JDK_JAVA_OPTIONS}), before any code in the JVM touches
 * {@code java.util.logging}:
 * <pre>{@code java.util.logging.manager=io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager}</pre>
 * {@link LogManager#getLogManager()} resolves this property exactly once, via a static
 * bootstrap, so calling {@link System#setProperty(String, String)} after the JVM has
 * started is too late; see this class's own package for why that forces this module's own
 * tests into dedicated, non-reused-fork JVMs.
 *
 * @see RainbowGumJULLogger
 */
public final class RainbowGumLogManager extends LogManager {

	private final Map<String, Logger> loggers = new ConcurrentHashMap<>();

	// Guards against a Logger vended by this LogManager (or something Rainbow Gum
	// itself does while resolving a route) triggering a reentrant getLogger() call for
	// the same name before the first call has finished constructing it.
	@SuppressWarnings("nullness") // checker's ThreadLocal<T> stub requires a @Nullable
									// T, which Set<String> genuinely never is here
	private final ThreadLocal<Set<String>> activeRequests = ThreadLocal.withInitial(HashSet::new);

	/**
	 * For {@link LogManager#getLogManager()}'s reflective instantiation, per the
	 * {@code java.util.logging.manager} system property contract. Do not call directly.
	 */
	public RainbowGumLogManager() {
	}

	@Override
	public boolean addLogger(Logger logger) {
		/*
		 * Always return false, the same as log4j-jul's LogManager: this forces every
		 * caller through getLogger(String) (which always returns a RainbowGumJULLogger)
		 * instead of allowing a non-bridged Logger to be registered directly.
		 */
		return false;
	}

	@Override
	public Logger getLogger(String name) {
		var requests = activeRequests.get();
		if (!requests.add(name)) {
			// Recursive call for the same name: return a fresh, throwaway logger
			// instead of recursing into computeIfAbsent again, which would deadlock
			// on ConcurrentHashMap's own re-entrancy guard.
			return new RainbowGumJULLogger(name);
		}
		try {
			return loggers.computeIfAbsent(name, RainbowGumJULLogger::new);
		}
		finally {
			requests.remove(name);
		}
	}

	@Override
	@SuppressWarnings("keyfor") // Collections.enumeration's Collection<String> parameter
								// does not need loggers.keySet()'s extra
								// @KeyFor("loggers")
								// refinement
	public Enumeration<String> getLoggerNames() {
		return Collections.enumeration(loggers.keySet());
	}

}
