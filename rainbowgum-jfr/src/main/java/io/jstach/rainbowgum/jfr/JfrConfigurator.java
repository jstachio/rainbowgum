package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Adds {@link JfrLogOutput} to the output registry with URI scheme
 * {@value JfrLogOutput#JFR_SCHEME}.
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
		return true;
	}

	private static class JfrOutputProvider implements OutputProvider {

		private static final LogProvider<LogEncoder> MESSAGE_ENCODER = LogEncoder
			.of(LogFormatter.builder().message().build());

		@Override
		public LogProvider<LogOutput> provide(LogProviderRef ref) {
			var uri = ref.uri();
			var destination = JfrLogOutput.destinationOrNull(uri);
			return (name, c) -> new JfrLogOutput(MESSAGE_ENCODER.provide(name, c), uri, destination);
		}

	}

}
