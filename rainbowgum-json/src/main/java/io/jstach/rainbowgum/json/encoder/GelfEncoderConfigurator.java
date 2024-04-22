package io.jstach.rainbowgum.json.encoder;

import java.net.URI;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Adds <a href="https://go2docs.graylog.org/5-2/getting_in_log_data/gelf.html">GELF
 * JSON</a> Encoder to encoder registry with {@value GelfEncoder#GELF_SCHEME} URI scheme.
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
		config.encoderRegistry().register(GelfEncoder.GELF_SCHEME, new GelfEncoderProvider());
		return true;
	}

	private static class GelfEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, c) -> {
				var uri = ref.uri();
				GelfEncoderBuilder b = new GelfEncoderBuilder(name);
				String prefix = b.propertyPrefix();
				LogProperties combined = LogProperties.of(uri, prefix, c.properties());
				String host = uri.getHost();
				if (host != null) {
					b.host(host);
				}
				b.fromProperties(combined);
				return b.build();
			};
		}

	}

}
