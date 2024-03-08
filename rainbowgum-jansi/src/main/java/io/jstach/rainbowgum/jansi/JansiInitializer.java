package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.AnsiConsole;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Jansi initializer.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JansiInitializer implements RainbowGumServiceProvider.Configurator {

	/**
	 * Jansi disable property.
	 */
	public static final String JANSI_DISABLE = LogProperties.ROOT_PREFIX + "jansi.disable";

	/**
	 * No Arg for service loader.
	 */
	public JansiInitializer() {
	}

	@Override
	public boolean configure(LogConfig config) {
		if (installJansi(config)) {
			AnsiConsole.systemInstall();
			config.encoderRegistry()
				.setEncoderForOutputType(OutputType.CONSOLE_OUT,
						() -> LogEncoder.of(JansiLogFormatter.builder().build()));
		}
		return true;
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
