package io.jstach.rainbowgum.json.encoder;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Adds a <a href="https://logback.qos.ch/manual/encoders.html#JsonEncoder">Logback style
 * JSON encoder</a> to encoder registry with {@value LogbackJsonEncoder#LOGBACK_SCHEME}
 * URI scheme.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class LogbackJsonEncoderConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public LogbackJsonEncoderConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(LogbackJsonEncoder.LOGBACK_SCHEME, new LogbackJsonEncoderProvider());
		return true;
	}

	private static class LogbackJsonEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, c) -> {
				LogbackJsonEncoderBuilder b = new LogbackJsonEncoderBuilder(name);
				b.fromProperties(c.properties(), ref);
				return b.build();
			};
		}

	}

}
