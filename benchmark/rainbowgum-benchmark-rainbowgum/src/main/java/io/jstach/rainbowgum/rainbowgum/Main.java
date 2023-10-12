package io.jstach.rainbowgum.rainbowgum;

import io.jstach.rainbowgum.Defaults;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.appender.SynchronizedLogAppender;
import io.jstach.rainbowgum.benchmark.SLF4JBenchmark;

public class Main {

	public static void main(String[] args) {
		Defaults.logAppender = (output, format) -> config -> SynchronizedLogAppender.of(output, LogEncoder.of(format));
		SLF4JBenchmark.main(args);
	}

}
