package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogProvider;

/**
 * Builds a {@link JfrAlertListener}.
 *
 * @see JfrAlertListenerConfigurator
 */
public final class JfrAlertListenerBuilder {

	private JfrAlertListenerBuilder() {
	}

	/**
	 * Creates a builder.
	 * @return builder.
	 */
	public static JfrAlertListenerBuilder of() {
		return new JfrAlertListenerBuilder();
	}

	/**
	 * Builds a JFR alert listener.
	 * @return provider.
	 */
	public LogProvider<LogAlerts.Listener> build() {
		return (name, config) -> new JfrAlertListener();
	}

}
