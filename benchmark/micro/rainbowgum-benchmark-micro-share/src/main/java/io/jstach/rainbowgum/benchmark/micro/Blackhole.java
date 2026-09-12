package io.jstach.rainbowgum.benchmark.micro;

import java.util.concurrent.atomic.LongAdder;

/**
 * Minimal stand-in for JMH's Blackhole: sinks a boolean result so the JIT can't prove a
 * level-check call is dead code and eliminate it.
 */
public final class Blackhole {

	private final LongAdder sink = new LongAdder();

	public void consume(boolean b) {
		if (b) {
			sink.increment();
		}
	}

	public long total() {
		return sink.sum();
	}

}
