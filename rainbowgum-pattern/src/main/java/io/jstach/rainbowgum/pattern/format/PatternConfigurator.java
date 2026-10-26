package io.jstach.rainbowgum.pattern.format;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.annotation.CaseChanging;
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
 *
 * @see PatternEncoderBuilder
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class PatternConfigurator implements Configurator {

	/**
	 * Where the pattern encoder is used: see {@link Mode}. Default is
	 * {@link Mode#CONSOLE}.
	 */
	public static final String LOGGING_PATTERN_MODE_PROPERTY = LogProperties.ROOT_PREFIX + "pattern.mode";

	/**
	 * Where the pattern encoder is used, from {@value #LOGGING_PATTERN_MODE_PROPERTY}.
	 */
	@CaseChanging
	public enum Mode {

		/**
		 * The pattern encoder, with a default pattern, replaces TTLL as the encoder of
		 * console output that does not name one, and is used wherever an encoder names
		 * the {@value PatternEncoder#PATTERN_SCHEME} scheme. The default.
		 */
		CONSOLE,
		/**
		 * The pattern encoder is used only where an encoder names the
		 * {@value PatternEncoder#PATTERN_SCHEME} scheme, so console output that does not
		 * name one keeps the TTLL encoder.
		 */
		EXPLICIT;

		static Mode parse(String value) {
			return LogProperty.enumValue(Mode.class, value);
		}

	}

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

	static final String DEFAULT_PATTERN = """
			%cyan(%date{HH:mm:ss.SSS}) %clr([%thread]){faint} %highlight(%-5level){ERROR=red bold, INFO=blue bold, WARN=red bold} %magenta(%logger{36}) - %msg%n%ex""";

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var services = config.serviceRegistry();
		String n = ServiceRegistry.DEFAULT_SERVICE_NAME;
		services.putIfAbsent(PatternRegistry.class, n, PatternRegistry::of);
		services.putIfAbsent(PatternCompiler.class, n, () -> PatternCompiler.of(b -> {
		}).provide(n, config));
		config.encoderRegistry().register(PatternEncoder.PATTERN_SCHEME, new PatternEncoderProvider());
		config.reporter().addProperty(PatternConfigurator.class, LOGGING_PATTERN_MODE_PROPERTY);
		var mode = config.properties()
			.forKey(LOGGING_PATTERN_MODE_PROPERTY)
			.ofString()
			.map(Mode::parse)
			.or(Mode.CONSOLE)
			.validateNow(PatternConfigurator.class);
		if (mode == Mode.CONSOLE) {
			config.encoderRegistry().setEncoderForOutputType(OutputType.CONSOLE_OUT, (name, c) -> {
				PatternEncoderBuilder b = new PatternEncoderBuilder(name);
				b.pattern(DEFAULT_PATTERN);
				b.fromProperties(c.properties());
				return b.build().provide(name, config);
			});
		}
		return true;
	}

	@LogConfigurable(name = "PatternEncoderBuilder", prefix = LogProperties.ENCODER_PREFIX + "pattern.")
	static LogProvider<LogEncoder> provideEncoder(@KeyParameter String name, String pattern,
			@PassThroughParameter @Nullable PatternCompiler patternCompiler,
			@ConvertParameter("convertCharset") @Nullable Charset charset, @Nullable Integer maxBufferSize) {
		return (n, config) -> {
			var compiler = patternCompiler;
			if (compiler == null) {
				compiler = PatternCompiler.of(b -> {
				}).provide(name, config);
			}
			var formatter = compiler.compile(pattern);
			var builder = LogEncoder.builder(formatter);
			builder.description(pattern);
			if (charset != null) {
				builder.charset(charset);
			}
			if (maxBufferSize != null) {
				builder.maxBufferSize(maxBufferSize);
			}
			return builder.build().provide(n, config);
		};
	}

	static Charset convertCharset(String charset) {
		return Charset.forName(charset);
	}

	@LogConfigurable(name = "PatternConfigBuilder", prefix = PatternConfig.PATTERN_CONFIG_PREFIX)
	static PatternConfig provideFormatterConfig(@KeyParameter String name, //
			@ConvertParameter("convertZoneId") @Nullable ZoneId zoneId, //
			@Nullable String lineSeparator, //
			@ConvertParameter("convertAnsi") LogConfig.Globals.@Nullable AnsiMode ansi, //
			@PassThroughParameter @Nullable Function<String, @Nullable String> propertyFunction, //
			@PassThroughParameter @Nullable Instant startTime, //
			@ConvertParameter("convertSequenceNumberStart") @Nullable Long sequenceNumberStart, //
			@ConvertParameter("convertAbbreviatorCache") PatternConfig.@Nullable CacheType abbreviatorCache) {
		PatternConfig dc = PatternConfig.of();
		ansi = ansi == null ? dc.ansi() : ansi;
		lineSeparator = lineSeparator == null ? dc.lineSeparator() : lineSeparator;
		zoneId = zoneId == null ? dc.zoneId() : zoneId;
		propertyFunction = propertyFunction == null ? StandardPropertyFunction.INSTANCE : propertyFunction;
		startTime = startTime == null ? Instant.now() : startTime;
		long sequenceNumberStart_ = sequenceNumberStart == null ? dc.sequenceNumberStart() : sequenceNumberStart;
		abbreviatorCache = abbreviatorCache == null ? dc.abbreviatorCache() : abbreviatorCache;
		return new SimpleFormatterConfig(zoneId, lineSeparator, ansi, propertyFunction, startTime, sequenceNumberStart_,
				abbreviatorCache);

	}

	/*
	 * The same values as logging.global.ansi.mode, including its true and false aliases.
	 */
	static LogConfig.Globals.AnsiMode convertAnsi(String s) {
		return switch (s.strip().toUpperCase(Locale.ROOT)) {
			case "TRUE" -> LogConfig.Globals.AnsiMode.ON;
			case "FALSE" -> LogConfig.Globals.AnsiMode.OFF;
			default -> LogProperty.enumValue(LogConfig.Globals.AnsiMode.class, s, "true", "false");
		};
	}

	static Long convertSequenceNumberStart(String s) {
		return Long.valueOf(s);
	}

	static PatternConfig.CacheType convertAbbreviatorCache(String s) {
		return PatternConfig.CacheType.parse(s);
	}

	static ZoneId convertZoneId(String zoneId) {
		return ZoneId.of(zoneId);
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
