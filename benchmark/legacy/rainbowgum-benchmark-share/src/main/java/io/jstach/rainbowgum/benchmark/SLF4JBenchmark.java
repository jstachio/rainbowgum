package io.jstach.rainbowgum.benchmark;

import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JBenchmark {

	private static boolean preinit = false;

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
		if (args.length == 0) {
			runSingleThread(SIZE);
		}
		else {
			runVirtualThreads(SIZE);
		}

		long duration = System.nanoTime() - start;

		System.out.println("DURATION: " + formatNano(duration));
	}

	static Logger log = LoggerFactory.getLogger("test.BENCHMARK");

	// fun fact ... chatgpt wrote this bs for me
	static String formatNano(long nano) {
		String input = String.valueOf(nano);

		int length = input.length();
		StringBuilder result = new StringBuilder();

		for (int i = length - 1; i >= 0; i--) {
			result.append(input.charAt(i));
			if ((length - i) % 3 == 0 && i != 0) {
				result.append('_');
			}
		}

		return result.reverse().toString();
	}

	public static void runSingleThread(final int SIZE) {

		for (int i = 0; i < SIZE; i++) {
			log.error("message");
			log.error("message {}", "one");
			log.error("message {} {}", "one", "two");
			IllegalStateException e = new IllegalStateException();
			log.error("message ex 1 {}", (Object) e);

			log.error("message ex 2 {} {}", null, e);

			log.error("message ex 3 {} {}", "one", "two", e);
			log.error("message ex 3 {} {} {}", "one", "two", e);

			// log.trace("message");
			// log.trace("message {}", "one");
			// log.trace("message {} {}", "one", "two");
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
