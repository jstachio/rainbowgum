package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Adds {@link JfrLogOutput} to the output registry, and a near zero-cost encoder (a
 * {@linkplain LogFormatter#builder() formatter} with no formatters, which
 * {@linkplain LogFormatter.Builder#build() is a documented noop}) to the encoder
 * registry, both with URI scheme {@value JfrLogOutput#JFR_SCHEME}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class JfrConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public JfrConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.outputRegistry().register(JfrLogOutput.JFR_SCHEME, new JfrOutputProvider());
		config.encoderRegistry()
			.register(JfrLogOutput.JFR_SCHEME, EncoderProvider.of(LogEncoder.of(LogFormatter.builder().build())));
		return true;
	}

	private static class JfrOutputProvider implements OutputProvider {

		@Override
		public LogProvider<LogOutput> provide(LogProviderRef ref) {
			return (name, c) -> new JfrLogOutput();
		}

	}

}
