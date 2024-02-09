package io.jstach.rainbowgum.json.encoder;

import java.net.URI;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Adds Gelf Encoder to encoder registry.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class GelfEncoderConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public GelfEncoderConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config) {
		config.encoderRegistry().register("gelf", new GelfEncoderProvider());
		return true;
	}

	private static class GelfEncoderProvider implements EncoderProvider {

		@Override
		public LogEncoder provide(URI uri, String name, LogProperties properties) {
			GelfEncoderBuilder b = new GelfEncoderBuilder(name);
			String prefix = b.propertyPrefix();
			LogProperties combined = LogProperties.of(uri, prefix, properties);
			String host = uri.getHost();
			if (host != null) {
				b.host(host);
			}
			b.fromProperties(combined);
			return b.build();
		}

	}

}
