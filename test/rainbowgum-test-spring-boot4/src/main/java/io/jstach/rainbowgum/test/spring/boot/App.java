package io.jstach.rainbowgum.test.spring.boot;

import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point.
 *
 * @author agentgt
 */
@SpringBootApplication
public class App {

	/**
	 * To placate JDK 18 javadoc.
	 */
	public App() {
	}

	/**
	 * Canonical entry point that will launch Spring
	 * @param args the command line args
	 */
	public static void main(String[] args) {

		// Obtained before Spring finishes booting: this is a replaceable logger bound
		// to the bootstrap RainbowGum's queued router - RouteChangePublisher rebinds
		// it to the real router once Spring's LoggingSystem sets one, so the same
		// instance keeps working correctly (rather than continuing to dispatch into
		// the now-abandoned queue) before and after the swap below.
		Logger log = LoggerFactory.getLogger("blah");
		java.util.logging.Logger jul = java.util.logging.Logger.getLogger("blah");
		jul.log(Level.INFO, "hello jul before boot");
		log.info("Hello before Spring Boot");

		SpringApplication.run(App.class, args);

		jul.log(Level.INFO, "hello jul after boot");
		log.info("Hello after Spring Boot");
		log.info("Logger: {}", log.getClass());
	}

}
