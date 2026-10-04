package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.output.ListLogOutput;

class AppenderClearThreadLocalsTest {

	/*
	 * Counts buffer creation: a thread local buffer appender creates one buffer per
	 * thread, so a new one after clearThreadLocals() proves the old one was removed.
	 */
	static final class CountingEncoder implements LogEncoder {

		final LogEncoder delegate = LogEncoder.of(LogFormatter.builder().message().build())
			.provide("counting", LogConfig.builder().build());

		final AtomicInteger buffers = new AtomicInteger();

		@Override
		public Buffer buffer(BufferHints hints) {
			buffers.incrementAndGet();
			return delegate.buffer(hints);
		}

		@Override
		public void encode(LogEvent event, Buffer buffer) {
			delegate.encode(event, buffer);
		}

	}

	@ParameterizedTest
	@EnumSource(value = AppenderType.class, names = { "LOCK_THREAD_LOCAL_BUFFER", "SYNCHRONIZED_THREAD_LOCAL_BUFFER" })
	void clearThreadLocalsRemovesTheCurrentThreadsBuffer(AppenderType type) {
		var encoder = new CountingEncoder();
		var output = new ListLogOutput();
		List<Integer> buffersAfterEachStep = new ArrayList<>();
		try (var gum = RainbowGum.builder()
			.route(r -> r.appender("list", a -> a.output(output).encoder(encoder).appenderType(type)))
			.set()) {
			gum.log(TestLogEventFactory.of().event("one"));
			buffersAfterEachStep.add(encoder.buffers.get());
			gum.log(TestLogEventFactory.of().event("two"));
			buffersAfterEachStep.add(encoder.buffers.get());
			LogAppender.clearThreadLocals();
			gum.log(TestLogEventFactory.of().event("three"));
			buffersAfterEachStep.add(encoder.buffers.get());
		}
		assertEquals(List.of(1, 1, 2), buffersAfterEachStep);
		assertEquals(List.of("one", "two", "three"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void clearThreadLocalsWithNoBoundGumDoesNothing() {
		LogAppender.clearThreadLocals();
	}

}
