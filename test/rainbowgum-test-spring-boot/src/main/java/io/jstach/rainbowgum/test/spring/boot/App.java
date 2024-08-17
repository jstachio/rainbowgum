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

		SpringApplication.run(App.class, args);
		java.util.logging.Logger jul = java.util.logging.Logger.getLogger("blah");
		jul.log(Level.INFO, "hello jul");
		jul.log(Level.INFO, "hello again jul");
		Logger log = LoggerFactory.getLogger("blah");
		log.info("Hello Spring Boot");
	}

}
