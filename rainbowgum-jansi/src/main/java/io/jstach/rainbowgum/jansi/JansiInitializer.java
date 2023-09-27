package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.AnsiConsole;

import io.jstach.rainbowgum.Defaults;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(RainbowGumServiceProvider.class)
public class JansiInitializer implements RainbowGumServiceProvider.Initializer {

	public static String JANSI_DISABLE = "jansi.disable";

	@Override
	public void initialize(LogConfig config) {
		if (installJansi(config)) {
			AnsiConsole.systemInstall();
			Defaults.CONSOLE.setDefaultFormatter(() -> JansiLogFormatter.builder().build());
		}
	}

	private boolean installJansi(LogConfig config) {
		if (!System.getProperty("surefire.real.class.path", "").isEmpty()) {
			return false;
		}
		var disableProperty = Boolean.parseBoolean(config.properties().property(JANSI_DISABLE));
		if (disableProperty) {
			return true;
		}
		return true;
	}

}
