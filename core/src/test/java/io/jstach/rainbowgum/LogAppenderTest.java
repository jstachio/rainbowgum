package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.output.ListLogOutput;

class LogAppenderTest {

	@Test
	void test() {
		var out = Objects.requireNonNull(System.out);
		ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
		AppenderLock.lockFactoryFunction = flags -> new AppenderLock(new ReentrantLock()) {
			@Override
			boolean tryLock() {
				if (realLock.isHeldByCurrentThread()) {
					out.println("RENTRY!");
					messages.add("RENTRY!");
					return false;
				}
				realLock.lock();
				return true;
			}

		};
		var output = new ListLogOutput();
		LogConfig config = LogConfig.builder().build();
		var testAppender = LogAppender.builder("test")
			.encoder(LogFormatter.builder().message().encoder())
			.output(output)
			.build()
			.provide("test", config);

		output.setConsumer((e, m) -> {
			// Now we do something naughty here and cause reentry.
			testAppender.append(TestEventBuilder.of().build());
		});
		testAppender.append(TestEventBuilder.of().build());
		String expected = """
				[RENTRY!]
				""".trim();
		String actual = messages.toString();
		assertEquals(expected, actual);
	}

}
