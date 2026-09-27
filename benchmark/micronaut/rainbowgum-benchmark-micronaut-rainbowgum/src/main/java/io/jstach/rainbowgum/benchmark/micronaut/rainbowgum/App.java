package io.jstach.rainbowgum.benchmark.micronaut.rainbowgum;

import io.micronaut.runtime.Micronaut;

/**
 * Entry point for the RainbowGum flavor of the Micronaut webapp benchmark.
 */
public class App {

	private App() {
	}

	/**
	 * Canonical entry point that will launch Micronaut.
	 * @param args the command line args
	 */
	public static void main(String[] args) {
		Micronaut.run(App.class, args);
	}

}
