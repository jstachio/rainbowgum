package io.jstach.rainbowgum.jfr;

import io.jstach.rainbowgum.LogConfig;
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

		@Override
		public LogProvider<LogOutput> provide(LogProviderRef ref) {
			return (name, c) -> new JfrLogOutput();
		}

	}

}
