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
		gum.router().log("stuff", Level.INFO, "Stuff");
		var router = gum.router();
		var level = router.levelResolver().resolveLevel("stuff");
		System.out.println(level);
		boolean actual = router.isEnabled("stuff", Level.WARNING);
		assertTrue(actual);
		assertFalse(gum.router().isEnabled("stuff", Level.DEBUG));

	}

	@Test
	public void testLevelConfig() throws Exception {
		Map<String, String> config = Map.of("logging.level.stuff", "" + Level.DEBUG.name());
		var gum = RainbowGum.builder(LogConfig.of(ServiceRegistry.of(), config::get)).build();

		gum.router().log("stuff", Level.DEBUG, "Stuff");
		gum.router().log("stuff", Level.DEBUG, "Stuff");
		assertFalse(gum.router().isEnabled("stuff", Level.TRACE));
		assertTrue(gum.router().isEnabled("stuff", Level.DEBUG));

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

		var sysout = LogAppender.builder() //
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
		}).build()) {

			gum.start();
			gum.router().log("stuff", Level.INFO, "Stuff");
			gum.router().log("stuff", Level.ERROR, "bad");

			boolean enabled = gum.router().isEnabled("stuff", Level.INFO);
			assertFalse(enabled);

			Thread.sleep(50);
		}

	}

}
