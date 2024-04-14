package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.LogOutput.OutputType;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

class LogEncoderRegistryTest {

	ListLogOutput output = new ListLogOutput();

	@Test
	void testProvide() {
		var config = LogConfig.builder()
			.configurator(new EncoderConfigurator())
			.configurator(new OutputConfigurator())
			.properties(LogProperties.builder().fromProperties("""
					logging.appenders=list
					logging.appender.list.encoder=custom
					logging.appender.list.output=custom
					""").build())
			.build();

		var gum = RainbowGum.builder(config).build();
		try (var g = gum.start()) {
			g.router().eventBuilder("stuff", Level.INFO).message("hello").log();
		}
		String actual = output.toString();
		String expected = """
				CUSTOM hello
				""";
		assertEquals(expected, actual);
	}

	@Test
	void testSetEncoderForOutputType() {
		var config = LogConfig.builder()
			.configurator(new EncoderConfigurator())
			.configurator(new OutputConfigurator())
			.properties(LogProperties.builder().fromProperties("""
					logging.appenders=list
					logging.appender.list.output=custom
					""").build())
			.build();

		var gum = RainbowGum.builder(config).build();
		try (var g = gum.start()) {
			g.router().eventBuilder("stuff", Level.INFO).message("hello").log();
		}
		String actual = output.toString();
		String expected = """
				OUTPUT_TYPE hello
				""";
		assertEquals(expected, actual);
	}

	static class EncoderConfigurator implements RainbowGumServiceProvider.Configurator {

		@Override
		public boolean configure(LogConfig config) {
			EncoderProvider provider = new EncoderProvider() {

				@Override
				public LogEncoder provide(URI uri, String name, LogProperties properties) {
					return LogFormatter.builder().text("CUSTOM ").message().newline().encoder();
				}
			};
			config.encoderRegistry().register("custom", provider);
			config.encoderRegistry()
				.setEncoderForOutputType(OutputType.MEMORY,
						() -> LogFormatter.builder().text("OUTPUT_TYPE ").message().newline().encoder());
			return true;
		}

	}

	class OutputConfigurator implements RainbowGumServiceProvider.Configurator {

		@Override
		public boolean configure(LogConfig config) {
			OutputProvider provider = new OutputProvider() {

				@Override
				public LogConfig.Provider<LogOutput> provide(LogProviderRef ref) {
					return LogConfig.Provider.of(output);
				}
			};
			config.outputRegistry().register("custom", provider);
			return true;
		}

	}

}
