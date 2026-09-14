package io.jstach.rainbowgum.signal;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * Installs a {@code sun.misc.Signal} handler that calls
 * {@link io.jstach.rainbowgum.LogOutputRegistry#reopen()} - the same call the HTTP based
 * reopen example in {@code doc/overview.html} makes - when the configured signal (default
 * {@value #DEFAULT_SIGNAL_NAME}) is received. Intended to be triggered from a logrotate
 * {@code postrotate} script with e.g. <code>kill -USR1 $(cat app.pid)</code> instead of
 * an HTTP callback.
 * <p>
 * <strong>Disabled by default</strong>, even with this module on the classpath -
 * installing a live signal handler is an ambient capability change (any process with
 * permission to signal this one could trigger it), so activation requires either
 * {@value #SIGNAL_ENABLE} set to <code>true</code>, or calling {@link #enabled(boolean)}
 * explicitly in code - constructing and configuring this class directly is itself the
 * opt-in.
 * <p>
 * Any previously installed handler for the same signal is chained (called after this
 * handler runs), not discarded, so this composes with other signal handlers in the same
 * process; {@link #close()} restores it.
 * <p>
 * Becomes a safe no-op - rather than failing Rainbow Gum startup - if the platform has no
 * such signal (e.g. Windows) or if the optional {@code jdk.unsupported} module is not
 * present (e.g. a trimmed jlink runtime image).
 *
 * @apiNote {@code sun.misc.Signal} dispatches handlers from native code; this has not
 * been verified under GraalVM native-image and this module does not currently ship a
 * {@code reflect-config.json}. Applications building a native-image should test this
 * specifically.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class SignalConfigurator implements Configurator, AutoCloseable {

	/**
	 * If <code>true</code> will install the signal handler when relying on auto-discovery
	 * (service loader) alone. Disabled by default since installing a signal handler is an
	 * ambient capability change - see this class's javadoc. Ignored if
	 * {@link #enabled(boolean)} was called explicitly, which always wins.
	 */
	public static final String SIGNAL_ENABLE = LogProperties.ROOT_PREFIX + "signal.enable";

	/**
	 * The signal name (without the <code>SIG</code> prefix, e.g. <code>USR1</code>,
	 * <code>HUP</code>) to listen on if not set explicitly with
	 * {@link #signalName(String)}. Defaults to {@value #DEFAULT_SIGNAL_NAME}.
	 */
	public static final String SIGNAL_NAME = LogProperties.ROOT_PREFIX + "signal.name";

	/**
	 * Default signal name: {@value}.
	 */
	public static final String DEFAULT_SIGNAL_NAME = "USR1";

	private volatile @Nullable Boolean enabled;

	private volatile @Nullable String signalName;

	private volatile @Nullable Signal signal;

	private volatile @Nullable SignalHandler previousHandler;

	/**
	 * No arg for service loader. Disabled by default - see {@link #SIGNAL_ENABLE}.
	 */
	public SignalConfigurator() {
	}

	/**
	 * Explicitly enables or disables installing the signal handler, overriding
	 * {@value #SIGNAL_ENABLE} regardless of its value. Calling this with
	 * <code>true</code> is itself an explicit opt-in.
	 * @param enabled <code>true</code> to install the handler.
	 * @return this.
	 */
	public SignalConfigurator enabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	/**
	 * Explicitly sets the signal name to listen on, overriding {@value #SIGNAL_NAME}.
	 * @param signalName signal name without the <code>SIG</code> prefix, e.g.
	 * <code>USR1</code>.
	 * @return this.
	 */
	public SignalConfigurator signalName(String signalName) {
		this.signalName = Objects.requireNonNull(signalName);
		return this;
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		boolean resolvedEnabled = enabled != null ? enabled
				: config.properties().forKey(SIGNAL_ENABLE).ofBoolean().or(false).value();
		if (!resolvedEnabled) {
			return true;
		}
		if (!isUnsupportedModuleAvailable()) {
			/*
			 * A trimmed jlink runtime image without jdk.unsupported - safe no-op rather
			 * than failing startup, same as JULConfigurator does for java.logging.
			 */
			return true;
		}
		String name = signalName != null ? signalName
				: config.properties().forKey(SIGNAL_NAME).ofString().or(DEFAULT_SIGNAL_NAME).value();
		install(config, name);
		return true;
	}

	private void install(LogConfig config, String name) {
		var outputRegistry = config.outputRegistry();
		var alerts = config.alerts();
		SignalHandler handler = raisedSignal -> {
			try {
				outputRegistry.reopen();
			}
			catch (RuntimeException e) {
				alerts.error(SignalConfigurator.class, e);
			}
			var previous = previousHandler;
			if (previous != null && previous != SignalHandler.SIG_DFL && previous != SignalHandler.SIG_IGN) {
				try {
					previous.handle(raisedSignal);
				}
				catch (RuntimeException e) {
					alerts.error(SignalConfigurator.class, e);
				}
			}
		};
		try {
			Signal sig = new Signal(name);
			this.previousHandler = Signal.handle(sig, handler);
			this.signal = sig;
		}
		catch (IllegalArgumentException e) {
			/*
			 * Platform does not support this signal (e.g. Windows) or the name is not
			 * recognized - safe no-op rather than failing startup.
			 */
			alerts.error(SignalConfigurator.class, e);
		}
	}

	private static boolean isUnsupportedModuleAvailable() {
		return ModuleLayer.boot().findModule("jdk.unsupported").isPresent();
	}

	@Override
	public void close() {
		var sig = signal;
		if (sig != null) {
			var previous = previousHandler;
			Signal.handle(sig, previous != null ? previous : SignalHandler.SIG_DFL);
		}
	}

}
