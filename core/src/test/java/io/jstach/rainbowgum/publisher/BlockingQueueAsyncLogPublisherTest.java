package io.jstach.rainbowgum.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogRouter.Router.RouterFactory;
import io.jstach.rainbowgum.RainbowGum;
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
		var b = BlockingQueueAsyncLogPublisher.of(appender, 10);
		b.start(LogConfig.builder().build());

		try (BlockingQueueAsyncLogPublisher pub = b) {
			for (int i = 0; i < count; i++) {
				TestEventBuilder.of().to(pub).event().message("hello").log();
			}
			latch.await();
		}

	}

	@Test
	void testAsyncFromBuilder() throws Exception {
		int count = 20;
		CountDownLatch latch = new CountDownLatch(count);
		ListLogOutput output = new ListLogOutput();
		var gum = RainbowGum.builder().route(b -> {
			b.appender("list", a -> {
				a.output(output);
				a.encoder(LogFormatter.builder().message().newline().encoder());
			});
			b.appender("console", a -> {
				a.output(LogOutput.ofStandardOut());
				a.encoder(LogFormatter.builder().message().newline().encoder());
			});
			b.publisher(PublisherFactory.ofAsync(100));
			b.factory(RouterFactory.of(e -> {
				latch.countDown();
				return e;
			}));
		}).build();
		try (var g = gum.start()) {
			for (int i = 0; i < count; i++) {
				TestEventBuilder.of().to(gum).event().message("" + i).log();
			}
			latch.await();
			Objects.requireNonNull(System.out).println("done");
		}
		List<String> lines = output.events().stream().map(e -> e.getValue().trim()).toList();
		int i = 0;
		for (var line : lines) {
			String actual = line;
			String expected = "" + i;
			assertEquals(expected, actual);
			i++;
		}

	}

}
