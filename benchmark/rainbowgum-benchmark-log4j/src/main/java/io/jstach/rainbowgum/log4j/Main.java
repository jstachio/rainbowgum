package io.jstach.rainbowgum.log4j;

import org.apache.logging.log4j.util.Constants;

import io.jstach.rainbowgum.benchmark.SLF4JBenchmark;

public class Main {

	public static void main(String[] args) {
		SLF4JBenchmark.main(args);
		System.out.println("thread locals:" + Constants.isThreadLocalsEnabled());
	}

}
