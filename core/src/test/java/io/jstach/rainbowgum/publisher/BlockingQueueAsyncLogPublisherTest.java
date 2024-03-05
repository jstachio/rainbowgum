package io.jstach.rainbowgum.publisher;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.TestEventBuilder;
import io.jstach.rainbowgum.output.ListLogOutput;

class BlockingQueueAsyncLogPublisherTest {

	@Test
	void testOf() throws InterruptedException {
		int count = 20;
		CountDownLatch latch = new CountDownLatch(count);
		ListLogOutput output = new ListLogOutput();
		output.setConsumer((e, s) -> latch.countDown());
		var appender = LogAppender.builder("blah").output(output).build().provide("blah", LogConfig.builder().build());
		var b = BlockingQueueAsyncLogPublisher.of(List.of(appender), 10);
		b.start(LogConfig.builder().build());

		try (BlockingQueueAsyncLogPublisher pub = b) {
			for (int i = 0; i < count; i++) {
				TestEventBuilder.of().to(pub).event().message("hello").log();
			}
			latch.await();
		}

	}

}
