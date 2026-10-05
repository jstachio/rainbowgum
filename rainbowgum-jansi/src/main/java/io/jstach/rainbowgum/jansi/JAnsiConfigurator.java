package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.AnsiConsole;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * JAnsi Configurator which will install JAnsi. JAnsi will strip ANSI escape characters if
 * piped out to a terminal (console) that does not support ANSI escape sequences.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JAnsiConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * Jansi disable property.
	 */
	public static final String JANSI_DISABLE = LogProperties.ROOT_PREFIX + "jansi.disable";

	/**
	 * No Arg for service loader.
	 */
	public JAnsiConfigurator() {
	}

	@Override
	public int priority() {
		return -1 << 1; // internal group 1
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		if (!isGlobalAnsiDisabled(config) && installJansi(config)) {
			AnsiConsole.systemInstall();
		}
		return true;
	}

	boolean installJansi(LogConfig config) {
		/*
		 * Surefire seems to hate JANSI probably because maven uses. Regardless maven
		 * tests probably do not need ansi output anyway.
		 */
		if (!System.getProperty("surefire.real.class.path", "").isEmpty()) {
			return false;
		}
		var disableProperty = Boolean.parseBoolean(config.properties().valueOrNull(JANSI_DISABLE));
		if (disableProperty) {
			return false;
		}
		return true;
	}

	private boolean isGlobalAnsiDisabled(LogConfig config) {
		return config.properties()
			.forKey(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY)
			.ofBoolean() //
			.or(false)
			.validateNow(JAnsiConfigurator.class);
	}

}
