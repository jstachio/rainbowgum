package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogPublisher.PublisherFactory;

class RainbowGumTest {

	// @Test
	// public void testBuilder() throws Exception {
	// var gum = RainbowGum.builder().build();
	// gum.start();
	// var logger = gum.router().getLogger("stuff");
	// logger.log(Level.INFO, "Stuff");
	// var router = gum.router();
	// var level = router.levelResolver().resolveLevel("stuff");
	// var out = Objects.requireNonNull(System.out);
	// out.println(level);
	// boolean actual = router.route("stuff", Level.WARNING).isEnabled();
	// assertTrue(actual);
	// assertFalse(gum.router().route("stuff", Level.DEBUG).isEnabled());
	//
	// }
	//
	// @Test
	// public void testLevelConfig() throws Exception {
	// Map<String, String> config = Map.of("logging.level.stuff", "" +
	// Level.DEBUG.name());
	// var gum = RainbowGum.builder(b -> b.properties(config::get)).build();
	//
	// var logger = gum.router().getLogger("stuff");
	//
	// logger.log(Level.DEBUG, "Stuff");
	// assertFalse(logger.isLoggable(Level.TRACE));
	//
	// assertTrue(logger.isLoggable(Level.DEBUG));
	//
	// }

	@Test
	void testAsyncPublisher() throws Exception {
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

		// GlobalLogRouter.INSTANCE.log("stuff", Level.WARNING, "first");
		GlobalLogRouter.INSTANCE.eventBuilder("stuff", Level.WARNING).message("first").log();
		try (var gum = RainbowGum.builder().route(r -> {
			r.publisher(PublisherFactory.async().build());
			r.appender(sysout);
			r.level(Level.WARNING, "stuff");
		}).set()) {

			assertEquals(1, ShutdownManager.shutdownHooks().size());

			var router = gum.router();
			router.eventBuilder("stuff", Level.INFO).message("Stuff").log();
			router.eventBuilder("stuff", Level.ERROR).message("bad").log();

			router.eventBuilder("stuff", Level.WARNING) //
				.message("builder info - {}") //
				.arg("hello") //
				.log();

			boolean enabled = router.route("stuff", Level.INFO).isEnabled();
			assertFalse(enabled);

			Thread.sleep(50);
		}

		assertEquals(0, ShutdownManager.shutdownHooks().size());

	}

}
