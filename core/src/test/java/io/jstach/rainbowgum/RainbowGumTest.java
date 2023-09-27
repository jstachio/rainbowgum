package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

class RainbowGumTest {

	@Test
	void testBuilder() throws Exception {
		var gum = RainbowGum.builder().build();
		gum.start();
		gum.router().log("stuff", Level.INFO, "Stuff");
	}

	@Test
	public void testName() throws Exception {

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

		try (RainbowGum gum = RainbowGum.builder().asynchronous(r -> {
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
