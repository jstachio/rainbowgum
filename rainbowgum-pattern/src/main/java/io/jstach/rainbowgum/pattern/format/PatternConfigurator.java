package io.jstach.rainbowgum.pattern.format;

import java.net.URI;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Provider;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.KeyParameter;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.FormatterConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Configures Logback style pattern encoders.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class PatternConfigurator implements Configurator {

	/**
	 * For service loader to call.
	 */
	public PatternConfigurator() {
		// for service laoder.
	}

	/**
	 * Pattern encoder uri provider scheme.
	 */
	public static String PATTERN_SCHEME = "pattern";

	@Override
	public boolean configure(LogConfig config) {
		var compiler = compiler(config);
		config.encoderRegistry().register(PATTERN_SCHEME, new PatternEncoderProvider(compiler, config));
		return true;
	}

	static PatternCompiler compiler(LogConfig config) {
		var registry = config.serviceRegistry().putIfAbsent(PatternRegistry.class, () -> PatternRegistry.of());
		var compiler = config.serviceRegistry()
			.putIfAbsent(PatternCompiler.class, () -> new Compiler(registry, FormatterConfig.empty()));
		return compiler;
	}

}

record PatternEncoderProvider(PatternCompiler compiler, LogConfig config) implements LogEncoder.EncoderProvider {

	@Override
	public LogEncoder provide(URI uri, String name, LogProperties properties) {
		PatternEncoderBuilder b = new PatternEncoderBuilder(name);
		String prefix = b.propertyPrefix();
		LogProperties combined = LogProperties.of(uri, prefix, properties);
		b.fromProperties(combined);
		return b.build().provide(name, config);
	}

	@LogConfigurable(name = "PatternEncoderBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static Provider<LogEncoder> provide(@KeyParameter String name, String pattern) {
		return (n, config) -> {
			var compiler = PatternConfigurator.compiler(config);
			return LogEncoder.of(compiler.compile(pattern));
		};
	}

}
