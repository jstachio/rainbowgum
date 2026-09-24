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
 * <p>
 * <b>GraalVM native image</b>: this module bundles a {@code native-image.properties} (at
 * {@code META-INF/native-image/io.jstach.rainbowgum/rainbowgum-jul-logmanager/}) with
 * {@code -Djava.util.logging.manager=...} plus a matching
 * {@code --initialize-at-build-time=io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager}
 * as native-image build arguments - the same technique <a href=
 * "https://github.com/helidon-io/helidon/blob/main/logging/log4j/src/main/resources/META-INF/native-image/io.helidon.logging/helidon-logging-log4j/native-image.properties">Helidon's
 * own {@code helidon-logging-log4j} module uses</a> for its equivalent
 * {@code LogManager}. Baking the property into the build lets {@code LogManager}'s static
 * bootstrap run once, during the build itself in a real JDK environment, so the
 * fully-initialized {@code RainbowGumLogManager} instance ends up frozen directly into
 * the image heap with nothing left to resolve at startup - verified with a real GraalVM
 * build, the resulting executable needs no runtime argument at all, not even the
 * {@code -D} above. The {@code --initialize-at-build-time} half is not optional: without
 * it, native-image's own build-time-heap-safety analysis refuses to embed a
 * {@code RainbowGumLogManager} instance whose type was not itself explicitly marked for
 * build-time initialization, even though the instance was already legitimately
 * constructed during the build.
 * <p>
 * An earlier attempt deferred {@code java.util.logging} initialization to runtime instead
 * ({@code --initialize-at-run-time=java.util.logging}) so the system property above could
 * be passed at native-image runtime exactly like on a normal JVM - that works, but costs
 * two runtime {@code -D} arguments instead of zero: a native-image executable has no JDK
 * installation directory behind it, and {@code LogManager}'s
 * primordial-configuration-file lookup throws {@code Error: Can't find java.home ??}
 * unconditionally once it actually runs at startup, regardless of whether a custom
 * {@code LogManager} is even in play - {@code -Djava.home=} <i>anything non-null</i>
 * would also have been required alongside the property above, for every consumer,
 * forever. Baking the property into the build avoids that problem entirely, since the
 * primordial-lookup code path it triggers never runs at native-image startup at all with
 * this approach.
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
