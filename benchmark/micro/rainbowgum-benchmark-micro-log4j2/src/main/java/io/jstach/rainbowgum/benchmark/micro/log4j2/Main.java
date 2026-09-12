package io.jstach.rainbowgum.benchmark.micro.log4j2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.benchmark.micro.BenchmarkRunner;

public class Main {

	public static void main(String[] args) {
		Logger log = LoggerFactory.getLogger("bench.Log4j2");
		BenchmarkRunner.runAll("log4j2", log);
	}

}
