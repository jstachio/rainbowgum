package io.jstach.rainbowgum.helidon4;

import io.helidon.logging.common.spi.LoggingProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.svc.ServiceProvider;

/**
 * Bootstraps Rainbow Gum at the exact point Helidon itself bootstraps logging.
 * <p>
 * Registered via {@link java.util.ServiceLoader}
 * ({@code io.helidon.logging.common.LogConfig}'s own static initializer calls
 * {@link java.util.ServiceLoader#load(Class)} for {@link LoggingProvider}, then
 * {@link LoggingProvider#initialization()} on whichever candidate sorts first). Confirmed
 * by decompiling {@code LogConfig}: candidates are ordered by
 * {@code io.helidon.common.Weighted} weight, descending, and the very first call any
 * Helidon-generated {@code Main} class makes is {@code LogConfig.initClass()}, forcing
 * that static initializer (and therefore this class's {@link #initialization()}) to run
 * before config, the service registry, or the webserver ever starts, and well before any
 * application log line.
 * <p>
 * No {@code @Weight} annotation needed here: the default weight
 * ({@value io.helidon.common.Weighted#DEFAULT_WEIGHT}) already beats
 * {@code io.helidon.logging.jul.JulProvider}'s own {@code @Weight(1)} (confirmed by
 * decompiling it), so this provider is picked automatically whenever both are on the
 * classpath, no exclusion or configuration required.
 * <p>
 * Depends on {@code rainbowgum-jul} at runtime (see this module's {@code pom.xml}):
 * calling {@link RainbowGum#of()} here only bootstraps Rainbow Gum itself. Actually
 * routing {@code java.util.logging.Logger} calls (Helidon's own facade) through it is a
 * separate step, {@code io.jstach.rainbowgum.jul.JULConfigurator}, which runs
 * automatically as part of that same bootstrap sequence as long as {@code rainbowgum-jul}
 * is present.
 */
@ServiceProvider(LoggingProvider.class)
public final class RainbowGumLoggingProvider implements LoggingProvider {

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public RainbowGumLoggingProvider() {
	}

	@Override
	public void initialization() {
		RainbowGum.of();
	}

	@Override
	public void runTime() {
		/*
		 * Only called under GraalVM native-image, after the image has actually started
		 * (LogConfig.configureRuntime() no-ops on a regular JVM). Not attempted yet:
		 * whether a native-image build of a Helidon app needs anything different here
		 * (re-resolving properties that were frozen at build time, for example) is real,
		 * separate work. See rainbowgum-jul-logmanager's own javadoc for the kind of
		 * build-time/run-time split GraalVM support in this project usually needs, and
		 * treat this as a placeholder until that's been verified against a real
		 * native-image build of this module.
		 */
	}

}
