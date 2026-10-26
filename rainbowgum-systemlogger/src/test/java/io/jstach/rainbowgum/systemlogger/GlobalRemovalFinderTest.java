package io.jstach.rainbowgum.systemlogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder.InitOption;

@Isolated
class GlobalRemovalFinderTest {

	@Test
	void finderDropsItsInitialGumAndQueuesNewNamesUntilTheNextBinding() {
		var oldOutput = new ListLogOutput();
		var finder = new RainbowGumSystemLoggerFinder(() -> InitOption.REUSE) {
		};
		var module = getClass().getModule();
		/*
		 * Closed by the try block rather than an explicit close(), which crashes the
		 * Checker Framework (see doc/checker-framework-bugs.md).
		 */
		try (var first = gum(oldOutput)) {
			finder.getLogger("before.removal", module).log(System.Logger.Level.INFO, "before");
		}
		var during = finder.getLogger("during.removal", module);
		during.log(System.Logger.Level.INFO, "during");
		assertNull(RainbowGum.getOrNull(), "removal must not cause the finder to start another gum");
		assertEquals(1, oldOutput.events().size());
		var output = new ListLogOutput();
		try (var second = gum(output)) {
			assertEquals(1, output.events().size());
			during.log(System.Logger.Level.INFO, "after");
			finder.getLogger("after.removal", module).log(System.Logger.Level.INFO, "new name");
			assertEquals(3, output.events().size());
			assertEquals(1, oldOutput.events().size());
		}
	}

	@Test
	void initialStartupResolvesBoundPropertiesBeforeSystemFallbacks() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageformat.style=log4j2
				""").build()).build();
		var output = new ListLogOutput();
		try (var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).build()) {
			RainbowGum.set(() -> gum);
			String key = "logging.messageformat.style";
			String previous = System.setProperty(key, "invalid");
			try {
				var finder = new RainbowGumSystemLoggerFinder(() -> InitOption.TRUE) {
				};
				finder.getLogger("initial", getClass().getModule()).log(System.Logger.Level.INFO, "It's done", "x");
				var message = new StringBuilder();
				output.events().getFirst().getKey().formattedMessage(message);
				assertEquals("Its done", message.toString());
			}
			finally {
				if (previous == null) {
					System.getProperties().remove(key);
				}
				else {
					System.setProperty(key, previous);
				}
			}
		}
	}

	private static RainbowGum gum(ListLogOutput output) {
		return RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(output)))
			.set();
	}

}
