package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.lang.System.Logger.Level;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.RainbowGum;

/*
 * Swaps the real System.out/System.err, so every test restores them in finally.
 */
class ConsoleStreamTest {

	enum Std {

		OUT("stdout") {
			@Override
			PrintStream get() {
				return System.out;
			}

			@Override
			void set(PrintStream stream) {
				System.setOut(stream);
			}
		},
		ERR("stderr") {
			@Override
			PrintStream get() {
				return System.err;
			}

			@Override
			void set(PrintStream stream) {
				System.setErr(stream);
			}
		};

		final String scheme;

		Std(String scheme) {
			this.scheme = scheme;
		}

		abstract PrintStream get();

		abstract void set(PrintStream stream);

	}

	/*
	 * How the output was configured: URI query, output property, or the builder.
	 */
	enum Config {

		DEFAULT, URI_QUERY, PROPERTY, BUILDER

	}

	record Captured(String before, String after) {
	}

	/*
	 * Builds a gum whose console appender writes to the configured output, rebinds the
	 * standard stream, then logs. Returns what the original stream and the rebound one
	 * each received.
	 */
	private static Captured logAfterRebind(Std std, Config config, boolean follow) {
		var original = new ByteArrayOutputStream();
		var rebound = new ByteArrayOutputStream();
		PrintStream saved = std.get();
		std.set(new PrintStream(original, true, StandardCharsets.UTF_8));
		try {
			var props = new StringBuilder();
			if (config == Config.PROPERTY && follow) {
				props.append("logging.output." + std.scheme + ".console.stream=follow\n");
			}
			var logConfig = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties(props.toString()).build())
				.build();
			Consumer<io.jstach.rainbowgum.LogAppender.Builder> output = a -> {
				switch (config) {
					case DEFAULT, PROPERTY ->
						a.output(LogOutput.of(LogProviderRef.of(URI.create(std.scheme + ":///"))));
					case URI_QUERY -> a.output(LogOutput.of(LogProviderRef
						.of(URI.create(std.scheme + ":///" + (follow ? "?stream=follow" : "?stream=cached")))));
					case BUILDER -> {
						var stream = follow ? ConsoleStream.FOLLOW : ConsoleStream.CACHED;
						a.output(std == Std.OUT ? new StdOutOutputBuilder("console").stream(stream).build()
								: new StdErrOutputBuilder("console").stream(stream).build());
					}
				}
			};
			try (var gum = RainbowGum.builder(logConfig).route(r -> r.appender("console", a -> {
				output.accept(a);
				a.formatter(LogFormatter.builder().message().newline().build());
			})).set()) {
				std.set(new PrintStream(rebound, true, StandardCharsets.UTF_8));
				gum.log(LogEventFactory.of("test").eventNoArg(Level.INFO, "hello", (Throwable) null));
			}
		}
		finally {
			std.set(saved);
		}
		return new Captured(original.toString(StandardCharsets.UTF_8), rebound.toString(StandardCharsets.UTF_8));
	}

	@ParameterizedTest
	@EnumSource(Std.class)
	void defaultIsCached(Std std) {
		assertEquals(new Captured("hello\n", ""), logAfterRebind(std, Config.DEFAULT, false));
	}

	@ParameterizedTest
	@EnumSource(value = Config.class, names = { "URI_QUERY", "BUILDER" })
	void explicitCachedIsCached(Config config) {
		for (var std : Std.values()) {
			assertEquals(new Captured("hello\n", ""), logAfterRebind(std, config, false), std + " " + config);
		}
	}

	@ParameterizedTest
	@EnumSource(value = Config.class, names = { "URI_QUERY", "PROPERTY", "BUILDER" })
	void followSeesTheReboundStream(Config config) {
		for (var std : Std.values()) {
			assertEquals(new Captured("", "hello\n"), logAfterRebind(std, config, true), std + " " + config);
		}
	}

	@ParameterizedTest
	@EnumSource(Std.class)
	void invalidStreamValueFails(Std std) {
		var props = LogProperties.builder()
			.fromProperties("logging.output." + std.scheme + ".console.stream=sometimes")
			.build();
		var e = assertThrows(LogProperty.ValidationException.class, () -> {
			if (std == Std.OUT) {
				new StdOutOutputBuilder("console").fromProperties(props);
			}
			else {
				new StdErrOutputBuilder("console").fromProperties(props);
			}
		});
		String builder = std == Std.OUT ? "StdOutOutputBuilder" : "StdErrOutputBuilder";
		String expected = """
				Validation failed for io.jstach.rainbowgum.output.%s:
				Error for property. key: 'logging.output.%s.console.stream' from PROPERTIES_STRING[logging.output.%s.console.stream], \
				'sometimes' is not a valid value for io.jstach.rainbowgum.output.ConsoleStream. Valid values: 'cached', 'follow'"""
			.formatted(builder, std.scheme, std.scheme);
		assertEquals(expected, e.getMessage());
	}

}
