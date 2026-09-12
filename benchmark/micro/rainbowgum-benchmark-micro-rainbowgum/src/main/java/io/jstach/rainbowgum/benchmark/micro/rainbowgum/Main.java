package io.jstach.rainbowgum.benchmark.micro.rainbowgum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jstach.rainbowgum.benchmark.micro.BenchmarkRunner;

public class Main {

	public static void main(String[] args) {
		Logger log = LoggerFactory.getLogger("bench.RainbowGum");
		BenchmarkRunner.runAll("rainbowgum", log);
	}

}
