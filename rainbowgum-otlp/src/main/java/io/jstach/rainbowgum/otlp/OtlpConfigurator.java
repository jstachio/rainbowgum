package io.jstach.rainbowgum.otlp;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Registers the {@value OtlpJsonEncoder#OTLP_SCHEME} encoder scheme
 * ({@link OtlpJsonEncoder}) and output scheme ({@link OtlpOutput}).
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class OtlpConfigurator implements Configurator {

	/**
	 * For service loader.
	 */
	public OtlpConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.encoderRegistry().register(OtlpJsonEncoder.OTLP_SCHEME, new JsonEncoderProvider());
		config.outputRegistry().register(OtlpOutput.OTLP_SCHEME, new HttpOutputProvider());
		return true;
	}

	private static final class JsonEncoderProvider implements EncoderProvider {

		@Override
		public LogProvider<LogEncoder> provide(LogProviderRef ref) {
			return (name, config) -> {
				var b = new OtlpJsonEncoderBuilder(name);
				b.fromProperties(config.properties(), ref);
				return b.build();
			};
		}

	}

	private static final class HttpOutputProvider implements OutputProvider {

		@Override
		public LogProvider<LogOutput> provide(LogProviderRef ref) {
			return (name, config) -> {
				var b = new OtlpOutputBuilder(name);
				b.fromProperties(config.properties(), ref);
				return b.build().provide(name, config);
			};
		}

	}

}
