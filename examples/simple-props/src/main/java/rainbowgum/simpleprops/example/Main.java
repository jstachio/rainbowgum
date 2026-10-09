package rainbowgum.simpleprops.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs through SLF4J with Rainbow Gum configured only by
 * <code>src/main/resources/logging.properties</code>.
 */
public final class Main {

	private static final Logger log = LoggerFactory.getLogger(Main.class);

	private Main() {
	}

	/**
	 * Logs a few events.
	 * @param args ignored.
	 */
	public static void main(String[] args) {
		log.info("Hello from Rainbow Gum");
		log.debug("Debug is enabled for this package in logging.properties");
		log.atInfo().addKeyValue("user", "ada").addKeyValue("requestId", 42).log("Request handled");
		log.trace("Trace is not enabled, so this is not logged");
		try {
			throw new IllegalStateException("boom");
		}
		catch (IllegalStateException e) {
			log.error("Something failed", e);
		}
	}

}
