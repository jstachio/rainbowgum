package io.jstach.rainbowgum.test.avaje;

import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;

public class Main {

	public static void main(String[] args) {
		var logger = LoggerFactory.getLogger(Main.class);

		logger.info("Hello from Avaje");
		ScopedKeyValues.builder().add("someKey", "someValue").add("Another Key", "Another Value").run(() -> {
			logger.debug("Debug from Avaje");
			logger.warn("Warn from Avaje");
			logger.error("Error from Avaje");
			logger.info("java.util.logging loaded: {}", isJulModuleAvailable());
		});

	}

	private static boolean isJulModuleAvailable() {
		ModuleLayer bootLayer = ModuleLayer.boot();
		return bootLayer.findModule("java.logging").isPresent();
	}

}
