package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.output.ListLogOutput;

class LogAppenderTest {

	final Function<Set<LogAppender.AppenderFlag>, AppenderLock> originalLockFactoryFunction = AppenderLock.lockFactoryFunction;

	@AfterEach
	void after() {
		/*
		 * test() below replaces this static factory but never restored it, silently
		 * corrupting the real REENTRY_DROP/REENTRY_LOG factory logic for every other test
		 * that runs afterward in the same JVM.
		 */
		AppenderLock.lockFactoryFunction = originalLockFactoryFunction;
	}

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
