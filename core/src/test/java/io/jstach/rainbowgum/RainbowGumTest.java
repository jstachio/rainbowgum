package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogPublisher.PublisherProvider;

class RainbowGumTest {

	@Test
	public void testBuilder() throws Exception {
		var gum = RainbowGum.builder().build();
		gum.start();
		var logger = gum.router().getLogger("stuff");
		logger.log(Level.INFO, "Stuff");
		var router = gum.router();
		var level = router.levelResolver().resolveLevel("stuff");
		System.out.println(level);
		boolean actual = router.route("stuff", Level.WARNING).isEnabled();
		assertTrue(actual);
		assertFalse(gum.router().route("stuff", Level.DEBUG).isEnabled());

	}

	@Test
	public void testLevelConfig() throws Exception {
		Map<String, String> config = Map.of("logging.level.stuff", "" + Level.DEBUG.name());
		var gum = RainbowGum.builder(b -> b.properties(config::get)).build();

		var logger = gum.router().getLogger("stuff");

		logger.log(Level.DEBUG, "Stuff");
		assertFalse(logger.isLoggable(Level.TRACE));

		assertTrue(logger.isLoggable(Level.DEBUG));

	}

	@Test
	void testFormatterBuilder() throws Exception {
		// var config = LogConfig.of();
		var formatter = LogFormatter.builder() //
			.timeStamp() //
			.space() //
			.text("[") //
			.threadName() //
			.text("] ") //
			.level() //
			.space() //
			.loggerName() //
			.text(" ") //
			.message() //
			.newline() //
			.build();

		var sysout = LogAppender.builder("sysout") //
			.output(LogOutput.ofStandardOut())
			.formatter(formatter)
			.build();

		// var logFile = config.outputProvider().of(URI.create("target/my.log"));
		//
		// var f = LogAppender.builder() //
		// .formatter((StringBuilder buffer, LogEvent e) -> {
		// buffer.append(e.level());
		// buffer.append(" ");
		// buffer.append(e.formattedMessage());
		// })
		// .output(logFile)
		// .build();

		try (var gum = RainbowGum.builder().route(r -> {
			r.publisher(PublisherProvider.async().build());
			r.appender(sysout);
			r.level(Level.WARNING, "stuff");
		}).build().start()) {

			gum.router().log("stuff", Level.INFO, "Stuff", null);
			gum.router().log("stuff", Level.ERROR, "bad", null);

			boolean enabled = gum.router().route("stuff", Level.INFO).isEnabled();
			assertFalse(enabled);

			Thread.sleep(50);
		}

	}

}
