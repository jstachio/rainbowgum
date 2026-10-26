package io.jstach.rainbowgum.jul.logmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalRainbowGumTest {

	@Test
	void boundPropertiesOverrideInvalidSystemPropertyDuringSubscription() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageformat.style=log4j2
				""").build()).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
			.set()) {
			String key = "logging.messageformat.style";
			String previous = System.setProperty(key, "invalid");
			try {
				var global = GlobalRainbowGum.of();
				assertSame(gum, requireGum(global.gum()));
				var message = new StringBuilder();
				global.messageFormatter().format(message, "It's done", "unused");
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

	@Test
	void alertListenerCanReplaceTheGumDuringInitialPropertyRead() throws Exception {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageformat.style=invalid
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

	enum Removal {

		CLOSE, UNSET, FAILING_SUPPLIER;

	}

	/*
	 * Each gum is closed by its try block rather than an explicit close(), which crashes
	 * the Checker Framework (see doc/checker-framework-bugs.md). Closing a gum that is no
	 * longer bound changes nothing.
	 */
	@ParameterizedTest
	@EnumSource(Removal.class)
	void removalDropsTheGumAndRestoresSystemFormatting(Removal removal) {
		String key = "logging.messageformat.style";
		String previous = System.setProperty(key, "log4j2");
		try {
			var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
					logging.messageformat.style=jdk
					""").build()).build();
			GlobalRainbowGum global;
			try (var first = RainbowGum.builder(config)
				.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
				.set()) {
				global = GlobalRainbowGum.of();
				assertEquals("It's done", format(global));
				switch (removal) {
					case CLOSE -> {
					}
					case UNSET -> RainbowGum.builder(LogConfig.builder().build()).unset();
					case FAILING_SUPPLIER -> RainbowGum.set(() -> {
						throw new IllegalStateException("not started");
					});
				}
			}
			assertNull(global.gum());
			assertEquals("Its done", format(global));
			var next = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
					logging.messageformat.style=jdk
					""").build()).build();
			try (var second = RainbowGum.builder(next)
				.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
				.set()) {
				assertSame(second, requireGum(global.gum()));
				assertEquals("It's done", format(global));
			}
		}
		finally {
			restore(key, previous);
		}
	}

	@Test
	void removalDropsTheGumEvenWhenSystemFormattingIsInvalid() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.messageformat.style=log4j2
				""").build()).build();
		String key = "logging.messageformat.style";
		String previous = System.setProperty(key, "invalid");
		try {
			GlobalRainbowGum global;
			try (var gum = RainbowGum.builder(config)
				.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
				.set()) {
				global = GlobalRainbowGum.of();
			}
			assertNull(global.gum());
			assertEquals("It's done", format(global));
		}
		finally {
			restore(key, previous);
		}
	}

	private static void restore(String key, @Nullable String previous) {
		if (previous == null) {
			System.getProperties().remove(key);
		}
		else {
			System.setProperty(key, previous);
		}
	}

	private static String format(GlobalRainbowGum global) {
		var message = new StringBuilder();
		global.messageFormatter().format(message, "It's done", "unused");
		return message.toString();
	}

	private static RainbowGum requireGum(@Nullable RainbowGum gum) {
		if (gum == null) {
			throw new AssertionError("expected a bound RainbowGum");
		}
		return gum;
	}

}
