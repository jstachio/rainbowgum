package io.jstach.rainbowgum.pattern.format;

import java.time.ZoneId;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.annotation.LogConfigurable.KeyParameter;
import io.jstach.rainbowgum.annotation.LogConfigurable.PassThroughParameter;
import io.jstach.rainbowgum.pattern.format.DefaultFormatterConfig.StandardPropertyFunction;
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
	public int priority() {
		return -1 << 2; // internal group 2
	}

	private static final String DEFAULT_PATTERN = """
			%cyan(%date{HH:mm:ss.SSS}) %clr([%thread]){faint} %highlight(%-5level){ERROR=red bold, INFO=blue bold, WARN=red} %magenta(%logger{36}) - %msg%n%ex""";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var services = config.serviceRegistry();
		String n = ServiceRegistry.DEFAULT_SERVICE_NAME;
		services.putIfAbsent(PatternRegistry.class, n, PatternRegistry::of);
		services.putIfAbsent(PatternCompiler.class, n, () -> PatternCompiler.of(b -> {
		}).provide(n, config));
		config.encoderRegistry().register(PatternEncoder.PATTERN_SCHEME, new PatternEncoderProvider());
		var disable = LogProperty.builder()
			.ofBoolean()
			.build("logging.pattern.disable")
			.get(config.properties())
			.value(false);
		if (!disable) {
			config.encoderRegistry().setEncoderForOutputType(OutputType.CONSOLE_OUT, (name, c) -> {
				PatternEncoderBuilder b = new PatternEncoderBuilder(name);
				b.pattern(DEFAULT_PATTERN);
				b.fromProperties(c.properties());
				return b.build().provide(name, config);
			});
		}
		return true;
	}

	@LogConfigurable(name = "PatternEncoderBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogProvider<LogEncoder> provideEncoder(@KeyParameter String name, String pattern,
			@PassThroughParameter @Nullable PatternCompiler patternCompiler) {
		return (n, config) -> {
			var compiler = patternCompiler;
			if (compiler == null) {
				compiler = PatternCompiler.of(b -> {
				}).provide(name, config);
			}
			return LogEncoder.of(compiler.compile(pattern));
		};
	}

	@LogConfigurable(name = "PatternConfigBuilder", prefix = PatternConfig.PATTERN_CONFIG_PREFIX)
	static PatternConfig provideFormatterConfig(@KeyParameter String name, //
			@ConvertParameter("convertZoneId") @Nullable ZoneId zoneId, //
			@Nullable String lineSeparator, //
			@Nullable Boolean ansiDisabled, //
			@PassThroughParameter @Nullable Function<String, @Nullable String> propertyFunction) {
		PatternConfig dc = PatternConfig.of();
		ansiDisabled = ansiDisabled == null ? dc.ansiDisabled() : ansiDisabled;
		lineSeparator = lineSeparator == null ? dc.lineSeparator() : lineSeparator;
		zoneId = zoneId == null ? dc.zoneId() : zoneId;
		propertyFunction = propertyFunction == null ? StandardPropertyFunction.INSTANCE : propertyFunction;
		return new SimpleFormatterConfig(zoneId, lineSeparator, ansiDisabled, propertyFunction);

	}

	static ZoneId convertZoneId(@Nullable String zoneId) {
		var dc = PatternConfig.of();
		ZoneId zoneId_ = zoneId == null ? dc.zoneId() : ZoneId.of(zoneId);
		return zoneId_;
	}

}

record PatternEncoderProvider() implements LogEncoder.EncoderProvider {

	@Override
	public LogProvider<LogEncoder> provide(LogProviderRef ref) {
		return (name, config) -> {
			PatternEncoderBuilder b = new PatternEncoderBuilder(name);
			b.fromProperties(config.properties(), ref);
			return b.build().provide(name, config);
		};
	}

}
