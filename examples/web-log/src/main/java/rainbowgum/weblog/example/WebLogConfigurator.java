package rainbowgum.weblog.example;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Puts a {@link LogFeed} in the {@link io.jstach.rainbowgum.ServiceRegistry} and
 * registers the <code>web</code> output scheme, so an appender can use it from
 * properties alone: <code>logging.appender.web.output.uri=web://127.0.0.1:8080/</code>.
 * Found with the {@link java.util.ServiceLoader} through
 * <code>META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider</code>.
 */
public final class WebLogConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For the service loader.
	 */
	public WebLogConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var feed = LogFeed.of(config);
		config.serviceRegistry().put(LogFeed.class, feed);
		config.serviceRegistry().onClose(feed);
		config.outputRegistry().register(WebLogOutput.SCHEME, ref -> (name, c) -> WebLogOutput.of(ref.uri(), c));
		return true;
	}

}
