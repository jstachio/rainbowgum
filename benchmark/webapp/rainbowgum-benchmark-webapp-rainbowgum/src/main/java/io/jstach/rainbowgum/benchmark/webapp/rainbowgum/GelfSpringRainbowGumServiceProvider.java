package io.jstach.rainbowgum.benchmark.webapp.rainbowgum;

import java.util.Optional;

import org.springframework.core.env.Environment;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.json.encoder.GelfEncoder;
import io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;

/**
 * Swaps the file and/or console encoder for GELF JSON instead of the pattern-based one
 * {@code RainbowGumLoggingSystemFactory} installs by default, when
 * {@code logging.structured.format.file=gelf} and/or
 * {@code logging.structured.format.console=gelf} is set - the same property keys Spring
 * Boot's own built-in structured logging support reads for Logback/Log4j2 (there's no
 * equivalent property support for RainbowGum's Spring integration yet, so this SPI -
 * designed for exactly this kind of customization - does it in code instead, but keyed
 * off the same properties so one env var toggles all three apps the same way). The two
 * properties are independent, matching Spring Boot's own behavior - e.g. the k8s/12factor
 * scenario sets only {@code .format.console} (console-only deployment, no file output at
 * all).
 */
public class GelfSpringRainbowGumServiceProvider implements SpringRainbowGumServiceProvider {

	private static final String STRUCTURED_FORMAT_FILE_KEY = "logging.structured.format.file";

	private static final String STRUCTURED_FORMAT_CONSOLE_KEY = "logging.structured.format.console";

	private static final String GELF = "gelf";

	/*
	 * Same property key Spring Boot's own GELF formatter reads (confirmed empirically -
	 * see benchmark/webapp/FINDINGS.md), so run-all.sh can pass one --logging.structured.
	 * gelf.host value that all three apps honor identically, keeping the "host" field
	 * (required by the GELF spec but omitted by Spring Boot's formatter unless set)
	 * consistent across frameworks rather than only appearing for this app.
	 */
	private static final String GELF_HOST_KEY = "logging.structured.gelf.host";

	private static final String DEFAULT_HOST = "rainbowgum-benchmark";

	/**
	 * For the Spring factories loader.
	 */
	public GelfSpringRainbowGumServiceProvider() {
	}

	@Override
	public Optional<RainbowGum> provide(LogConfig config, ClassLoader classLoader, Environment environment) {
		boolean file = GELF.equalsIgnoreCase(environment.getProperty(STRUCTURED_FORMAT_FILE_KEY));
		boolean console = GELF.equalsIgnoreCase(environment.getProperty(STRUCTURED_FORMAT_CONSOLE_KEY));
		if (!file && !console) {
			return Optional.empty();
		}
		String host = environment.getProperty(GELF_HOST_KEY, DEFAULT_HOST);
		if (file) {
			config.encoderRegistry().setEncoderForOutputType(OutputType.FILE, GelfEncoder.of(b -> b.host(host)));
		}
		if (console) {
			config.encoderRegistry().setEncoderForOutputType(OutputType.CONSOLE_OUT, GelfEncoder.of(b -> b.host(host)));
		}
		return Optional.of(RainbowGum.builder(config).build());
	}

}
