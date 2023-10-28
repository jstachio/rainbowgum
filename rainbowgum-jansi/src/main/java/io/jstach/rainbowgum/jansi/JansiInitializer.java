package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.AnsiConsole;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Jansi initializer.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JansiInitializer implements RainbowGumServiceProvider.Initializer {

	/**
	 * Jansi disable property.
	 */
	public static String JANSI_DISABLE = "jansi.disable";

	/**
	 * No Arg for service loader.
	 */
	public JansiInitializer() {
	}

	@Override
	public void initialize(ServiceRegistry registry, LogConfig config) {
		if (installJansi(config)) {
			AnsiConsole.systemInstall();
			config.defaults()
				.setFormatterForOutputType(OutputType.CONSOLE_OUT, () -> JansiLogFormatter.builder().build());
		}
	}

	private boolean installJansi(LogConfig config) {
		if (!System.getProperty("surefire.real.class.path", "").isEmpty()) {
			return false;
		}
		var disableProperty = Boolean.parseBoolean(config.properties().valueOrNull(JANSI_DISABLE));
		if (disableProperty) {
			return true;
		}
		return true;
	}

}
