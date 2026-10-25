package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.RainbowGumVersion;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/*
 * The pattern module is on this test's path, so whether its encoder is found depends only
 * on whether the config was built with the service loader.
 */
class ServiceLoaderDiscoveryTest {

	static final String PROPERTIES = """
			logging.appenders=list
			logging.appender.list.output.uri=list
			logging.appender.list.encoder.uri=pattern
			logging.appender.list.encoder.pattern.pattern=%level %msg%n
			""";

	static Configurator listOutput(ListLogOutput output) {
		return (config, pass) -> {
			config.outputRegistry().register("list", ref -> LogProvider.of(output));
			return true;
		};
	}

	@Test
	void builderWithConsumerDiscoversInstalledModules() {
		var output = new ListLogOutput();
		var gum = RainbowGum
			.builder(c -> c.properties(LogProperties.builder().fromProperties(PROPERTIES).build())
				.configurator(listOutput(output)))
			.build();
		try (var g = gum.start()) {
			g.log(LogEventFactory.of("test").eventNoArg(Level.INFO, "hello", KeyValues.of(), (Throwable) null));
		}
		assertEquals("INFO hello\n", output.toString());
	}

	@Test
	void configWithoutTheServiceLoaderSaysThePresentModuleWasNotLoaded() {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(PROPERTIES).build())
			.configurator(listOutput(new ListLogOutput()))
			.build();
		var e = assertThrows(LogProvider.ProvisionException.class, () -> RainbowGum.builder(config).build());
		String expected = """
				Error for property. key: 'logging.appender.list.encoder.uri' from PROPERTIES_STRING[logging.appender.list.encoder.uri], NotFoundException No encoder found. Scheme not registered. scheme: 'pattern', URI: 'pattern:///'. Scheme 'pattern' is provided by module 'io.jstach.rainbowgum.pattern', which is present but was not loaded. The LogConfig was probably built without the service loader: call LogConfig.Builder.serviceLoader() or add configurator(new io.jstach.rainbowgum.pattern.format.PatternConfigurator()). See %s/index.html#config_builder_service_loader"""
			.formatted(RainbowGumVersion.documentBaseUrl());
		assertEquals(expected, String.valueOf(e.getMessage()).lines().findFirst().orElseThrow());
	}

}
