package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Registers a {@link JfrAlertListener} with {@link io.jstach.rainbowgum.LogAlerts} if
 * {@value JfrAlertListenerBuilder#PROPERTY_enabled} is {@code true}.
 * <p>
 * Disabled by default: unlike {@link JfrConfigurator}'s output scheme (inert until an
 * appender explicitly selects {@code jfr:///}), a listener would otherwise start silently
 * committing every alert as a JFR event the moment this module is on the classpath, with
 * no explicit opt-in.
 *
 * @see JfrAlertListenerBuilder
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JfrAlertListenerConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public JfrAlertListenerConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var listener = new JfrAlertListenerBuilder().fromProperties(config.properties()).build();
		if (listener.enabled()) {
			config.alerts().addListener(listener);
		}
		return true;
	}

}
