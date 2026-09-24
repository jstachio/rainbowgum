package io.jstach.rainbowgum.jfr;

import java.util.Locale;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Registers a {@link JfrAlertListener} with {@link io.jstach.rainbowgum.LogAlerts} if
 * {@value #JFR_ALERTS_PROPERTY} is {@code TRUE}.
 * <p>
 * Disabled by default: unlike {@link JfrConfigurator}'s output scheme (inert until an
 * appender explicitly selects {@code jfr:///}), a listener would otherwise start silently
 * committing every alert as a JFR event the moment this module is on the classpath, with
 * no explicit opt-in.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JfrAlertListenerConfigurator implements Configurator {

	/**
	 * Enables the JFR alert listener. {@code TRUE}/{@code FALSE}, default {@code FALSE}.
	 */
	public static final String JFR_ALERTS_PROPERTY = LogProperties.ROOT_PREFIX + "jfr.alerts";

	/**
	 * Default constructor for service loader.
	 */
	public JfrAlertListenerConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var enabled = config.properties()
			.forKey(JFR_ALERTS_PROPERTY)
			.ofString()
			.map(Enabled::parse)
			.or(Enabled.FALSE)
			.validateNow(JfrAlertListenerConfigurator.class);
		if (enabled == Enabled.TRUE) {
			var listener = JfrAlertListenerBuilder.of().build().provide(JFR_ALERTS_PROPERTY, config);
			config.alerts().addListener(listener);
		}
		return true;
	}

	/*
	 * An enum (TRUE/FALSE) rather than .ofBoolean() deliberately - a typo'd value here
	 * fails loudly instead of silently resolving to "not enabled".
	 */
	private enum Enabled {

		TRUE, FALSE;

		static Enabled parse(String value) {
			return Enabled.valueOf(value.toUpperCase(Locale.ROOT));
		}

	}

}
