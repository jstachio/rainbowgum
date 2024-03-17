package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class IssuesTest {

	ListLogOutput list = new ListLogOutput();

	@Test
	void issue70() throws Exception {
		/*
		 * fun main() { logger.info { "Hello, World!" }
		 * logger.error(IllegalArgumentException()) { "Hello, World!" }
		 * logger.error("Hello, World!", IllegalArgumentException()) }
		 */
		try (var gum = gum().start()) {
			RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(gum, new RainbowGumMDCAdapter());
			var logger = factory.getLogger("issue70");
			logger.info("Hello, World!");
			logger.error("Hello, World!", new IllegalArgumentException());
		}
		String actual = list.toString();
		assertTrue(actual.contains(IllegalArgumentException.class.getSimpleName()));
	}

	private RainbowGum gum() {
		LogConfig config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(route -> {
			route.appender("list", a -> {
				a.output(list);
			});
		});
		var rainbowgum = gum.build();
		return rainbowgum;
	}

}
