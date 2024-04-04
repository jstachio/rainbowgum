package io.jstach.rainbowgum.jansi;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

class JAnsiConfiguratorTest {

	@Test
	void testInstallJansiLogFormatter() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				%s=true
				""".formatted(JAnsiConfigurator.JANSI_DISABLE)).build()).configurator(new JAnsiConfigurator()).build();
		var gum = RainbowGum.builder(config).build();
		try (var g = gum.start()) {
			g.router().eventBuilder("test", Level.INFO).message("hello").log();
		}
	}

}
