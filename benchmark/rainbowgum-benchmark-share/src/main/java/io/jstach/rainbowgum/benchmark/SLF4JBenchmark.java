package io.jstach.rainbowgum.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JBenchmark {

	public static void main(String[] args) {

		final int SIZE;
		if (args.length == 0) {
			SIZE = 2;
		}
		else {
			SIZE = Integer.parseInt(args[0]);
		}

		long start = System.nanoTime();
		Logger log = LoggerFactory.getLogger("test.BENCHMARK");

		for (int i = 0; i < SIZE; i++) {
			log.info("message");
			log.info("message {}", "one");
			log.info("message {} {}", "one", "two");
			log.debug("message");
			log.debug("message {}", "one");
			log.debug("message {} {}", "one", "two");
		}

		long duration = System.nanoTime() - start;
		System.out.println("DURATION: " + duration);
	}

}
