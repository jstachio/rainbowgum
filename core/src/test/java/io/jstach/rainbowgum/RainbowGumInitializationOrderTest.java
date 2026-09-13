package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/*
 * Roughly proves the "Initialization order" documented on RainbowGum's own class
 * javadoc by wiring in custom components (a PropertiesProvider, a Configurator, a
 * LogProvider<LogOutput>, and the output's own start()) that each append a marker to a
 * shared mutable list the instant they are invoked, then asserting the list matches the
 * documented order at each checkpoint. Not exhaustive - just enough to catch the
 * documentation drifting from the actual order (or vice versa) on a future refactor.
 */
class RainbowGumInitializationOrderTest {

	@Test
	void testInitializationOrder() {
		List<String> order = new ArrayList<>();

		var output = new ListLogOutput() {
			@Override
			public void start(LogConfig config) {
				order.add("output.start");
				super.start(config);
			}
		};

		LogProvider<LogOutput> outputProvider = (name, config) -> {
			order.add("appender.output.provider");
			return output;
		};

		RainbowGumServiceProvider.PropertiesProvider propertiesProvider = registry -> {
			order.add("logConfig.propertiesProvider");
			return List.of();
		};

		RainbowGumServiceProvider.Configurator configurator = (config, pass) -> {
			order.add("logConfig.configurator");
			return true;
		};

		/*
		 * Step 1 of the documented order: LogConfig.Builder.build() resolves properties
		 * (PropertiesProvider) and runs Configurators before returning.
		 */
		var config = LogConfig.builder().propertiesProvider(propertiesProvider).configurator(configurator).build();

		assertEquals(List.of("logConfig.propertiesProvider", "logConfig.configurator"), order);

		/*
		 * Step 2: building a route resolves its appenders - here a single
		 * programmatically added one, so LogAppenderRegistry's own property-based
		 * fallback chain is skipped, but the output's LogProvider is still invoked
		 * synchronously as part of route building, strictly after LogConfig (and so every
		 * Configurator) is already fully built.
		 */
		var builder = RainbowGum.builder(config);
		builder.route(r -> r.appender("custom", a -> a.output(outputProvider)));

		assertEquals(List.of("logConfig.propertiesProvider", "logConfig.configurator", "appender.output.provider"),
				order);

		try (var gum = builder.build()) {
			// build() itself only assembles the already-built routes - no new markers.
			assertEquals(List.of("logConfig.propertiesProvider", "logConfig.configurator", "appender.output.provider"),
					order);

			/*
			 * Step 3: start() cascades router -> publisher -> appender -> output,
			 * starting the output last of all.
			 */
			gum.start();

			assertEquals(List.of("logConfig.propertiesProvider", "logConfig.configurator", "appender.output.provider",
					"output.start"), order);
		}
	}

}
