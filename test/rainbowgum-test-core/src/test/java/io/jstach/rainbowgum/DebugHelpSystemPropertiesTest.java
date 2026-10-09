package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig.DebugModeType;

/*
 * Core alone, no property files: someone trying things with -D on the command line.
 * Sets real system properties, so it must not run alongside other tests.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DebugHelpSystemPropertiesTest {

	@Test
	void misspelledSystemPropertiesFailWithTheKeyTheyProbablyMeant() {
		var properties = Map.of( //
				"logging.appendrs", "console", // typo of a key read
				"logging.appender.console.encodr", "json5"); // typo of a key read
		properties.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			String expected = """
					2 alert(s) at warning or above were recorded while starting and logging.debug=help:
					[WARNING] Property key 'logging.appender.console.encodr' from SYSTEM_PROPERTIES[logging.appender.console.encodr] was set but not read during startup. Did you mean 'logging.appender.console.encoder'?
					[WARNING] Property key 'logging.appendrs' from SYSTEM_PROPERTIES[logging.appendrs] was set but not read during startup. Did you mean 'logging.appenders'?""";
			assertEquals(expected, e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
		}
	}

}
