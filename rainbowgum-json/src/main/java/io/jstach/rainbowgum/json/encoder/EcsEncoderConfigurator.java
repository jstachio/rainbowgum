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
 * Adds
 * <a href="https://www.elastic.co/guide/en/ecs-logging/java/current/index.html">Elastic
 * Common Schema (ECS)</a> JSON Encoder to encoder registry with
 * {@value EcsEncoder#ECS_SCHEME} URI scheme.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class EcsEncoderConfigurator implements Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public EcsEncoderConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(EcsEncoder.ECS_SCHEME, new EcsEncoderProvider());
		return true;
	}

	private static class EcsEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, c) -> {
				EcsEncoderBuilder b = new EcsEncoderBuilder(name);
				b.fromProperties(c.properties(), ref);
				return b.build();
			};
		}

	}

}
