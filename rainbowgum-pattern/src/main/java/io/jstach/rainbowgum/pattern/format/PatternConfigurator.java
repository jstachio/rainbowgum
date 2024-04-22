package io.jstach.rainbowgum.pattern.format;

import java.time.ZoneId;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.annotation.LogConfigurable.KeyParameter;
import io.jstach.rainbowgum.annotation.LogConfigurable.PassThroughParameter;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Configures Logback style pattern encoders and offers provision with the URI scheme
 * {@value PatternEncoder#PATTERN_SCHEME}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class PatternConfigurator implements Configurator {

	/**
	 * For service loader to call.
	 */
	public PatternConfigurator() {
		// for service loader.
	}

	@Override
	public boolean configure(LogConfig config) {
		var compiler = compiler(config);
		config.encoderRegistry().register(PatternEncoder.PATTERN_SCHEME, new PatternEncoderProvider(compiler));
		return true;
	}

	static PatternCompiler compiler(LogConfig config) {
		var registry = config.serviceRegistry().putIfAbsent(PatternRegistry.class, () -> PatternRegistry.of());
		boolean ansiDisable = LogProperty.Property.builder() //
			.toBoolean() //
			.orElse(false) //
			.build(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY) //
			.get(config.properties()) //
			.value();
		var patternConfig = config.serviceRegistry().putIfAbsent(PatternConfig.class, () -> {
			var b = PatternConfig.builder().fromProperties(config.properties());
			if (ansiDisable) {
				b.ansiDisabled(true);
			}
			return b.build();
		});
		var compiler = config.serviceRegistry()
			.putIfAbsent(PatternCompiler.class, () -> new Compiler(registry, patternConfig));
		return compiler;
	}

	@LogConfigurable(name = "PatternEncoderBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogProvider<LogEncoder> provideEncoder(@KeyParameter String name, String pattern,
			@PassThroughParameter @Nullable PatternCompiler patternCompiler) {
		return (n, config) -> {
			var compiler = patternCompiler;
			if (compiler == null) {
				compiler = PatternConfigurator.compiler(config);
			}
			return LogEncoder.of(compiler.compile(pattern));
		};
	}

	@LogConfigurable(name = "PatternConfigBuilder", prefix = PatternConfig.PATTERN_CONFIG_PREFIX)
	static PatternConfig provideFormatterConfig(@ConvertParameter("convertZoneId") @Nullable ZoneId zoneId,
			@Nullable String lineSeparator, @Nullable Boolean ansiDisabled) {
		PatternConfig dc = PatternConfig.of();
		ansiDisabled = ansiDisabled == null ? dc.ansiDisabled() : ansiDisabled;
		lineSeparator = lineSeparator == null ? dc.lineSeparator() : lineSeparator;
		return new SimpleFormatterConfig(zoneId, lineSeparator, ansiDisabled);

	}

	static ZoneId convertZoneId(@Nullable String zoneId) {
		var dc = PatternConfig.of();
		ZoneId zoneId_ = zoneId == null ? dc.zoneId() : ZoneId.of(zoneId);
		return zoneId_;
	}

}

record PatternEncoderProvider(PatternCompiler compiler) implements LogEncoder.EncoderProvider {

	@Override
	public LogProvider<LogEncoder> provide(LogProviderRef ref) {
		return (name, config) -> {
			var uri = ref.uri();
			var properties = config.properties();
			PatternEncoderBuilder b = new PatternEncoderBuilder(name);
			String prefix = b.propertyPrefix();
			LogProperties combined = LogProperties.of(uri, prefix, properties);
			b.fromProperties(combined);
			return b.build().provide(name, config);
		};
	}

}
