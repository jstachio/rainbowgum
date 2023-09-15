package io.jstach.rainbowgum;


import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

class RainbowGumTest {

	@Test
	void testBuilder() throws Exception {
		var gum = RainbowGum.builder().build();
		gum.start();
		gum.router().log("stuff", Level.INFO, "Stuff");
		Thread.sleep(100);
	}

}
