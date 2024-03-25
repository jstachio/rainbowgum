package io.jstach.rainbowgum.test.jdk;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class JDKSetupTest {

	@Test
	void testSystemLoggingFactory() {
		var logger = System.getLogger("before.load");
		logger.log(Level.INFO, "Hello {0}!", "Gum");
		assertNull(RainbowGum.getOrNull(), "Rainbow Gum should not be loaded yet.");
		ListLogOutput output = new ListLogOutput();
		try (var gum = JDKSetup.run(output)) {
			assertNotNull(RainbowGum.getOrNull());
			String actual = output.toString();
			String expected = """
					00:00:00.000 [main] INFO before.load - Hello Gum!
					""";
			assertEquals(expected, actual);
		}
		assertNull(RainbowGum.getOrNull(), "Rainbow Gum should not be loaded anymore.");

	}

}
