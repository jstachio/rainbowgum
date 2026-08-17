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
 * Adds a <a href=
 * "https://github.com/logfellow/logstash-logback-encoder">logstash-logback-encoder</a>
 * style JSON Encoder to encoder registry with {@value LogstashEncoder#LOGSTASH_SCHEME}
 * URI scheme.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class LogstashEncoderConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public LogstashEncoderConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(LogstashEncoder.LOGSTASH_SCHEME, new LogstashEncoderProvider());
		return true;
	}

	private static class LogstashEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, c) -> {
				LogstashEncoderBuilder b = new LogstashEncoderBuilder(name);
				b.fromProperties(c.properties(), ref);
				return b.build();
			};
		}

	}

}
