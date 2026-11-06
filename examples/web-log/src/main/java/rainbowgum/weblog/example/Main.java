package rainbowgum.weblog.example;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.RainbowGum;

/**
 * A small always-on service that logs to the console and to a page served by
 * {@link WebLogOutput}. The output is set up only by
 * <code>src/main/resources/logging.properties</code>; this class just logs.
 */
public final class Main {

	private static final Logger log = LoggerFactory.getLogger(Main.class);

	private static final Logger backup = LoggerFactory.getLogger("rainbowgum.weblog.example.Backup");

	private static final Logger sensors = LoggerFactory.getLogger("rainbowgum.weblog.example.Sensors");

	private Main() {
	}

	/**
	 * Logs a few events every second until stopped.
	 * @param args ignored.
	 * @throws InterruptedException if interrupted.
	 */
	public static void main(String[] args) throws InterruptedException {
		// The output registry finds the output by the appender's name.
		RainbowGum.of()
			.config()
			.outputRegistry()
			.output("web")
			.ifPresent(o -> log.info("Log page at {}", ((WebLogOutput) o).pageUri()));
		var random = ThreadLocalRandom.current();
		for (int tick = 1;; tick++) {
			sensors.atDebug()
				.addKeyValue("room", random.nextBoolean() ? "garage" : "office")
				.log("Temperature {} C", 18 + random.nextInt(8));
			if (tick % 5 == 0) {
				backup.info("Backed up {} files", random.nextInt(50, 500));
			}
			if (tick % 7 == 0) {
				log.warn("Request for /search?q=<b>bold</b> took {} ms", random.nextInt(500, 2000));
			}
			if (tick % 13 == 0) {
				backup.error("Backup failed", new IllegalStateException("disk full"));
			}
			Thread.sleep(1000);
		}
	}

}
