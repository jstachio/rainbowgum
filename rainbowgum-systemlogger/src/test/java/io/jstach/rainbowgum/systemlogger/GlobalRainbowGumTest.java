package io.jstach.rainbowgum.systemlogger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalRainbowGumTest {

	@Test
	void boundPropertiesOverrideInvalidSystemPropertyDuringSubscription() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageFormat.apply=index
				""").build()).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
			.set()) {
			String key = "logging.messageFormat.apply";
			String previous = System.setProperty(key, "invalid");
			try {
				var global = GlobalRainbowGum.of();
				assertSame(gum, requireGum(global.gum()));
				var message = new StringBuilder();
				global.messageFormatter().format(message, "It's done", "unused");
				assertEquals("It's done", message.toString());
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

	@Test
	void alertListenerCanReplaceTheGumDuringInitialPropertyRead() throws Exception {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageFormat.apply=invalid
				""").build()).build();
		try (var first = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
			.set();
				var second = RainbowGum.builder(LogConfig.builder().build())
					.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
					.build();
				var listener = config.alerts().addListener(event -> {
					if (RainbowGum.getOrNull() == first) {
						RainbowGum.set(() -> second);
						RainbowGum.of();
					}
				})) {
			var global = GlobalRainbowGum.of();
			assertSame(second, requireGum(RainbowGum.getOrNull()));
			assertSame(second, requireGum(global.gum()));
		}
	}

	@Test
	void removalFallsBackToSystemProperties() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageFormat.apply=index
				""").build()).build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(new ListLogOutput()))).set();
		var global = GlobalRainbowGum.of();
		try (gum) {
			assertSame(gum, requireGum(global.gum()));
			assertNotSame(StandardMessageFormatter.JUL, global.messageFormatter());
		}
		assertNull(global.gum());
		assertSame(StandardMessageFormatter.JUL, global.messageFormatter());
	}

	private static RainbowGum requireGum(@Nullable RainbowGum gum) {
		if (gum == null) {
			throw new AssertionError("expected a bound RainbowGum");
		}
		return gum;
	}

}
