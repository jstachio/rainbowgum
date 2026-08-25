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

	final java.util.function.IntSupplier originalJdkFeatureVersionSupplier = AppenderLock.jdkFeatureVersionSupplier;

	@AfterEach
	void after() {
		/*
		 * test() below replaces this static factory but never restored it, silently
		 * corrupting the real REENTRY_DROP/REENTRY_LOG factory logic for every other test
		 * that runs afterward in the same JVM.
		 */
		AppenderLock.lockFactoryFunction = originalLockFactoryFunction;
		AppenderLock.jdkFeatureVersionSupplier = originalJdkFeatureVersionSupplier;
	}

	@Test
	void test() {
		var out = Objects.requireNonNull(System.out);
		ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();
		/*
		 * No AppenderFlag is set below, so this exercises the default appender selection.
		 * Force a pre-JDK-24 version so it resolves to the AppenderLock-based
		 * ThreadLocalBufferLogAppender (which actually calls lock.tryLock()) rather than
		 * SynchronizedThreadLocalBufferLogAppender (which does not use AppenderLock at
		 * all, so the custom reentry-drop lock installed below would never be consulted
		 * and the reentrant append() call two lines down would recurse forever).
		 */
		AppenderLock.jdkFeatureVersionSupplier = () -> 21;
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
