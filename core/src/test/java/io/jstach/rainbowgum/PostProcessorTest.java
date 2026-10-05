package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogFormatter.PostProcessor;
import io.jstach.rainbowgum.LogFormatter.StandardPostProcessor;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

class PostProcessorTest {

	static final LogFormatter FORMATTER = LogFormatter.builder()
		.level()
		.space()
		.message()
		.newline()
		.throwable()
		.build();

	private static String log(String properties, Consumer<LogConfig> setup, Level level, String message,
			@Nullable Throwable throwable) {
		var output = new ListLogOutput();
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		setup.accept(config);
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output).formatter(FORMATTER)))
			.build()
			.start()) {
			gum.log(LogEventFactory.of("test").eventNoArg(level, message, KeyValues.of(), throwable));
		}
		return output.toString();
	}

	private static String log(String properties, Level level, String message, @Nullable Throwable throwable) {
		return log(properties, c -> {
		}, level, message, throwable);
	}

	private static IllegalStateException exception() {
		var ex = new IllegalStateException("boom");
		ex.setStackTrace(new StackTraceElement[] { new StackTraceElement("app.Main", "run", "Main.java", 42) });
		return ex;
	}

	@Test
	void noPostProcessorsByDefault() {
		assertEquals("ERROR first\nsecond\n", log("", Level.ERROR, "first\nsecond", null));
	}

	@Test
	void journaldPrefixesEveryLine() {
		String actual = log("logging.encoder.app.postProcessors=journald", Level.ERROR, "first\nsecond", exception());
		String expected = """
				<3>ERROR first
				<3>second
				<3>java.lang.IllegalStateException: boom
				<3>\tat app.Main.run(Main.java:42)
				""";
		assertEquals(expected, actual);
	}

	@Test
	void crlfKeepsTheEventOnOneLine() {
		assertEquals("WARN first\\nsecond\\r\\nthird\n",
				log("logging.encoder.app.postProcessors=crlf", Level.WARNING, "first\nsecond\r\nthird", null));
	}

	@Test
	void postProcessorsApplyInOrder() {
		assertEquals("<3>ERROR first\\nsecond\n",
				log("logging.encoder.app.postProcessors=crlf,journald", Level.ERROR, "first\nsecond", null));
		assertEquals("<3>ERROR first\\n<3>second\n",
				log("logging.encoder.app.postProcessors=journald, crlf", Level.ERROR, "first\nsecond", null));
	}

	@Test
	void registeredPostProcessorGetsTheEncoderNameAndConfig() {
		String actual = log("""
				logging.encoder.app.postProcessors=tag
				greeting=hi
				""", config -> PostProcessor.register(config.serviceRegistry(), "tag", (name, c) -> {
			String greeting = c.properties().valueOrNull("greeting");
			return (event, output, start) -> output.insert(start, "[" + name + " " + greeting + "] ");
		}), Level.INFO, "hello", null);
		assertEquals("[app hi] INFO hello\n", actual);
	}

	@Test
	void registeredPostProcessorTakesPrecedenceOverAStandardName() {
		String actual = log("logging.encoder.app.postProcessors=journald",
				config -> PostProcessor.register(config.serviceRegistry(), "journald",
						(name, c) -> (event, output, start) -> output.insert(start, "custom ")),
				Level.INFO, "hello", null);
		assertEquals("custom INFO hello\n", actual);
	}

	/*
	 * A custom post processor as a plugin would ship it: its own class, registered by a
	 * configurator, and selected purely by property alongside a standard one.
	 */
	static final class MaskDigits implements PostProcessor {

		@Override
		public void process(LogEvent event, StringBuilder output, int start) {
			for (int i = start; i < output.length(); i++) {
				if (Character.isDigit(output.charAt(i))) {
					output.setCharAt(i, '#');
				}
			}
		}

	}

	static final class MaskDigitsConfigurator implements RainbowGumServiceProvider.Configurator {

		@Override
		public boolean configure(LogConfig config, Pass pass) {
			PostProcessor.register(config.serviceRegistry(), "maskDigits", (name, c) -> new MaskDigits());
			return true;
		}

	}

	@Test
	void customPostProcessorRegisteredByAConfigurator() {
		var output = new ListLogOutput();
		var config = LogConfig.builder()
			.properties(LogProperties.builder()
				.fromProperties("logging.encoder.app.postProcessors=maskDigits,journald")
				.build())
			.configurator(new MaskDigitsConfigurator())
			.build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("app", a -> a.output(output).formatter(FORMATTER)))
			.build()
			.start()) {
			gum.log(LogEventFactory.of("test")
				.eventNoArg(Level.ERROR, "card 4111 1111\nexpires 12/30", KeyValues.of(), (Throwable) null));
		}
		assertEquals("<3>ERROR card #### ####\n<3>expires ##/##\n", output.toString());
	}

	@Test
	void unknownPostProcessorFails() {
		var e = assertThrows(LogProvider.ProvisionException.class,
				() -> log("logging.encoder.app.postProcessors=crlf,nope", Level.INFO, "hello", null));
		String expected = """
				Unknown post processor 'nope' for encoder 'app'. Available post processors: crlf, journald
				  ↳ Failure providing Appenders for route: 'default'.""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void decorateWrapsAFormatterProgrammatically() {
		var sb = new StringBuilder();
		StandardPostProcessor.CRLF.decorate(FORMATTER)
			.format(sb, LogEventFactory.of("test").eventNoArg(Level.INFO, "a\nb", KeyValues.of(), (Throwable) null));
		assertEquals("INFO a\\nb\n", sb.toString());
	}

}
