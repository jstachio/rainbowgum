package io.jstach.rainbowgum.test.avaje;

import org.slf4j.LoggerFactory;

public class Main {

	public static void main(String[] args) {
		var logger = LoggerFactory.getLogger(Main.class);
		logger.info("Hello from Avaje");
		logger.debug("Debug from Avaje");

	}

}
