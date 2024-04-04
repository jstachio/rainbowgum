package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.AnsiConsole;
import org.fusesource.jansi.AnsiMode;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.Property;
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
	public boolean configure(LogConfig config) {
		boolean ansi = installJansi(config);
		if (ansi) {
			AnsiConsole.systemInstall();
			var stream = AnsiConsole.out();
			boolean disableAnsi = stream.getMode() == AnsiMode.Strip;
			config.encoderRegistry()
				.setEncoderForOutputType(OutputType.CONSOLE_OUT,
						() -> LogEncoder.of(JansiLogFormatter.builder().disableAnsi(disableAnsi).build()));
		}
		return true;
	}

	private boolean installJansi(LogConfig config) {
		/*
		 * Surefire seems to hate JANSI probably because maven uses. Regardless maven
		 * tests probably do not need ansi output anyway.
		 */
		if (!System.getProperty("surefire.real.class.path", "").isEmpty()) {
			return false;
		}
		boolean globalDisable = Property.builder() //
			.toBoolean() //
			.orElse(false) //
			.build(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY) //
			.get(config.properties())
			.value();
		if (globalDisable) {
			return false;
		}
		var disableProperty = Boolean.parseBoolean(config.properties().valueOrNull(JANSI_DISABLE));
		if (disableProperty) {
			return false;
		}
		return true;
	}

}
