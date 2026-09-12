package io.jstach.rainbowgum.benchmark.webapp.rainbowgum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import io.jstach.rainbowgum.benchmark.webapp.BenchmarkWebConfig;

/**
 * Entry point for the RainbowGum flavor of the webapp benchmark.
 */
@SpringBootApplication
@Import(BenchmarkWebConfig.class)
public class App {

	/**
	 * For Spring.
	 */
	public App() {
	}

	/**
	 * Canonical entry point that will launch Spring.
	 * @param args the command line args
	 */
	public static void main(String[] args) {
		SpringApplication.run(App.class, args);
	}

}
