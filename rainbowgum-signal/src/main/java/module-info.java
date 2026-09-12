/**
 * Rainbow Gum Unix signal integration. Installs a {@code sun.misc.Signal} handler that
 * calls {@link io.jstach.rainbowgum.LogOutputRegistry#reopen()} - the same call the HTTP
 * based reopen example in {@code doc/overview.html} makes - when a configurable signal
 * (default {@code SIGUSR1}) is received. Intended for a logrotate {@code postrotate}
 * script to use (e.g. <code>kill -USR1 $(cat app.pid)</code>) instead of an HTTP
 * callback, on Linux/Docker deployments.
 * <p>
 * <strong>Disabled by default</strong> even when this module is on the classpath -
 * installing a live signal handler is an ambient capability change, so activating it
 * requires either the property
 * {@value io.jstach.rainbowgum.signal.SignalConfigurator#SIGNAL_ENABLE} set to
 * <code>true</code>, or explicit programmatic configuration (constructing a
 * {@link io.jstach.rainbowgum.signal.SignalConfigurator} and enabling it in code is
 * itself the opt-in).
 * <p>
 * This module is not usable on Windows (there are no POSIX signals) or on a custom
 * jlink runtime image that excludes the optional <code>jdk.unsupported</code> module -
 * in both cases it detects this and safely becomes a no-op rather than failing to
 * start. <strong>The module <code>jdk.unsupported</code> is not required and thus
 * jlink might not automatically include it as it is <code>requires static</code>.</strong>
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.signal {

	exports io.jstach.rainbowgum.signal;

	requires io.jstach.rainbowgum;

	requires static jdk.unsupported;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with io.jstach.rainbowgum.signal.SignalConfigurator;

}
