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

	/*
	 * https://github.com/jstachio/rainbowgum/issues/752 Testcontainers shortens image
	 * names with a digest and ends them with ..., which is not a valid property key.
	 */
	@Test
	void issue752() throws Exception {
		try (var gum = gum().start()) {
			@SuppressWarnings("unused")
			RainbowGumLoggerFactory factory = new RainbowGumLoggerFactory(gum, new RainbowGumMDCAdapter());
			// TODO #752 getLogger throws IllegalArgumentException: Property key should
			// not
			// start or end with '.'
			// var logger = factory.getLogger("tc.floci/floci-gcp@sha256:405c128b...");
			// logger.warn("Hello, World!");
		}
		// String actual = list.toString();
		// assertTrue(actual.contains("Hello, World!"), actual);
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
