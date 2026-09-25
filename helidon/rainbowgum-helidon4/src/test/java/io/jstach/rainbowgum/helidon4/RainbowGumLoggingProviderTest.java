package io.jstach.rainbowgum.helidon4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * A real RainbowGum with a ListLogOutput is bound before Helidon's
 * io.helidon.logging.common.LogConfig is ever touched (see setRainbowGum below),
 * matching io.helidon.Main's own convention (LogConfig.initClass() as the very first
 * thing a Helidon app does). LogConfig selects its LoggingProvider exactly once, in
 * its own static initializer. reuseForks is disabled in this module's pom.xml so
 * this test class gets that moment fresh, uncontaminated by anything else in the JVM.
 */
class RainbowGumLoggingProviderTest {

	static final ListLogOutput list = new ListLogOutput();

	static {
		setRainbowGum();
		io.helidon.logging.common.LogConfig.initClass();
	}

	private static void setRainbowGum() {
		String properties = """
				logging.level=INFO
				""";
		var config = io.jstach.rainbowgum.LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			// Without this, Configurators (including rainbowgum-jul's own
			// JULConfigurator, which installs the bridge Handler) are never
			// discovered via ServiceLoader at all for a hand-built LogConfig.
			.serviceLoader()
			.build();
		RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.formatter((output, event) -> {
					output.append(event.level()).append(" ");
					event.formattedMessage(output);
					output.append("\n");
				});
				a.output(list);
			});
		}).set();
	}

	@Test
	void testProviderIsPickedUpWithNoSystemPropertyRequired() {
		Logger log = Logger.getLogger("helidon4.provider.selection");
		log.info("hello");
		assertEquals("INFO hello\n", list.toString());
	}

	@Test
	void testDebugIsGatedByRoute() {
		Logger log = Logger.getLogger("helidon4.native.gated");
		assertFalse(log.isLoggable(Level.FINE));
	}

}
