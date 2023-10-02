package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.Map;

import org.junit.jupiter.api.Test;

class RainbowGumTest {

	@Test
	public void testBuilder() throws Exception {
		var gum = RainbowGum.builder().build();
		gum.start();
		gum.router().log("stuff", Level.INFO, "Stuff");
		assertTrue(gum.router().isEnabled("stuff", Level.WARNING));
		assertFalse(gum.router().isEnabled("stuff", Level.DEBUG));

	}

	@Test
	public void testLevelConfig() throws Exception {
		Map<String, String> config = Map.of("rainbowgum.log.stuff", "" + Level.DEBUG.name());
		var gum = RainbowGum.builder(LogConfig.of(config::get)).build();

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
		// .formatter((StringBuilder sb, LogEvent e) -> {
		// sb.append(e.level());
		// sb.append(" ");
		// sb.append(e.formattedMessage());
		// })
		// .output(logFile)
		// .build();

		try (RainbowGum gum = RainbowGum.builder().async(r -> {
			r.appender(sysout);
			r.level("stuff", Level.WARNING);
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
