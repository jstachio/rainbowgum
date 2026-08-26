package io.jstach.rainbowgum.spring.boot4;

import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.core.env.Environment;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.json.encoder.EcsEncoder;
import io.jstach.rainbowgum.json.encoder.GelfEncoder;
import io.jstach.rainbowgum.json.encoder.LogstashEncoder;

/**
 * Bridges Spring Boot's {@code logging.structured.format.console}/
 * {@code logging.structured.format.file} (and the per-format
 * {@code logging.structured.<format>.*} fields) to RainbowGum's own
 * {@code rainbowgum-json} encoders. The two output properties are independent, matching
 * Spring Boot's own behavior - e.g. GELF-to-console with no file output at all (a
 * 12factor/k8s-style deployment) works the same way it would for Logback/Log4j2.
 * <p>
 * Only {@code ecs}, {@code gelf}, and {@code logstash} are supported - Spring Boot also
 * allows a fully-qualified {@code StructuredLogFormatter} class name for a fully custom
 * format, which has no RainbowGum equivalent and is silently ignored (falls back to
 * whatever pattern encoder is already installed for that output type).
 */
final class StructuredLogging {

	private static final String FORMAT_CONSOLE_PROPERTY = "logging.structured.format.console";

	private static final String FORMAT_FILE_PROPERTY = "logging.structured.format.file";

	private static final String ECS = "ecs";

	private static final String GELF = "gelf";

	private static final String LOGSTASH = "logstash";

	private StructuredLogging() {
	}

	static void apply(LogConfig config, Environment environment) {
		apply(config, environment, FORMAT_CONSOLE_PROPERTY, OutputType.CONSOLE_OUT);
		apply(config, environment, FORMAT_FILE_PROPERTY, OutputType.FILE);
	}

	private static void apply(LogConfig config, Environment environment, String formatProperty, OutputType outputType) {
		String format = environment.getProperty(formatProperty);
		if (format == null) {
			return;
		}
		LogProvider<? extends LogEncoder> encoder = encoderFor(format, environment);
		if (encoder != null) {
			config.encoderRegistry().setEncoderForOutputType(outputType, encoder);
		}
	}

	private static @Nullable LogProvider<? extends LogEncoder> encoderFor(String format, Environment environment) {
		return switch (format.toLowerCase(Locale.ROOT)) {
			case ECS -> EcsEncoder.of(b -> b
				.serviceName(
						environment.getProperty("logging.structured.ecs.service.name", applicationName(environment)))
				.serviceVersion(environment.getProperty("logging.structured.ecs.service.version",
						applicationVersion(environment)))
				.serviceEnvironment(environment.getProperty("logging.structured.ecs.service.environment"))
				.serviceNodeName(environment.getProperty("logging.structured.ecs.service.node-name")));
			case GELF -> GelfEncoder.of(b -> {
				String host = environment.getProperty("logging.structured.gelf.host", applicationName(environment));
				b.host(host != null ? host : "application");
				String serviceVersion = environment.getProperty("logging.structured.gelf.service.version",
						applicationVersion(environment));
				if (serviceVersion != null) {
					// GELF additional field names can't contain dots - matches the
					// underscore-joined convention Spring Boot's own GELF formatter
					// uses for the same field.
					b.headers(Map.of("service_version", serviceVersion));
				}
			});
			case LOGSTASH -> LogstashEncoder.of(b -> {
			});
			default -> null;
		};
	}

	private static @Nullable String applicationName(Environment environment) {
		return environment.getProperty("spring.application.name");
	}

	private static @Nullable String applicationVersion(Environment environment) {
		return environment.getProperty("spring.application.version");
	}

}
