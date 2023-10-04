package io.jstach.rainbowgum.benchmark;

import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JBenchmark {

	private static boolean preinit = true;

	public static void main(String[] args) {

		final int SIZE;
		if (args.length == 0) {
			SIZE = 2;
		}
		else {
			SIZE = Integer.parseInt(args[0].replace("_", ""));
		}

		if (preinit) {
			LoggerFactory.getLogger("test.BENCHMARK").debug("asdfsaf");
		}

		long start = System.nanoTime();
		runVirtualThreads(SIZE);

		long duration = System.nanoTime() - start;
		System.out.println("DURATION: " + duration);
	}

	public static void runSingleThread(final int SIZE) {
		Logger log = LoggerFactory.getLogger("test.BENCHMARK");

		for (int i = 0; i < SIZE; i++) {
			log.error("message");
			log.error("message {}", "one");
			log.error("message {} {}", "one", "two");
		}
	}

	public static void runVirtualThreads(final int SIZE) {

		// force initialization

		try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < SIZE; i++) {
				exec.execute(() -> SLF4JBenchmark.runSingleThread(10));
			}
		}
	}

}
