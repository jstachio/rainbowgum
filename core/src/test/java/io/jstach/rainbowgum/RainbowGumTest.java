package io.jstach.rainbowgum;


import java.lang.System.Logger.Level;
import java.net.URI;

import org.junit.jupiter.api.Test;

class RainbowGumTest {

	@Test
	void testBuilder() throws Exception {
		var gum = RainbowGum.builder().build();
		gum.start();
		gum.router().log("stuff", Level.INFO, "Stuff");
	}
	
	@Test
	public void testName()
			throws Exception {
		
	}
	
	@Test
	void testFormatterBuilder() throws Exception {
		var config = LogConfig.of();
		var formatter = LogFormatter.builder() //
				.add("HELLO") //
				.add(" ") //
				.level() //
				.add(" ")
				.loggerName() //
				.build();
		
		var sysout = LogAppender.builder() //
				.formatter(formatter)
				.build();
		
		var logFile = LogOutputProvider.of().of(URI.create("target/my.log"));
		
		var f = LogAppender.builder() //
				.formatter((StringBuilder sb, LogEvent e) -> {
					sb.append(e.level());
					sb.append(" ");
					sb.append(e.formattedMessage());
				})
				.output(logFile)
				.build();
		

		RainbowGum gum = RainbowGum.builder().router(
				LogRouter.SyncLogRouter.builder(config) //
						.appender(sysout) //
						.appender(f) 
						.build())
				.build();
		
		
		gum.start();
		gum.router().log("stuff", Level.INFO, "Stuff");
	}

}
