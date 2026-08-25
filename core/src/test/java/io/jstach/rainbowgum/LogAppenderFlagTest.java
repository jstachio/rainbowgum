package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Exercises {@link LogAppender.AppenderFlag} end to end with real appenders/outputs (no
 * mocks) rather than just unit testing the enum in isolation.
 */
class LogAppenderFlagTest {

	ByteArrayOutputStream metaLogBytes = new ByteArrayOutputStream();

	PrintStream metaLogStream = new PrintStream(metaLogBytes);

	@BeforeEach
	void before() {
		MetaLog.output = () -> metaLogStream;
	}

	@AfterEach
	void after() {
		MetaLog.output = () -> System.err;
	}

	private static LogAppender appender(String name, ListLogOutput output, AppenderFlag... flags) {
		var builder = LogAppender.builder(name).encoder(LogFormatter.builder().message().encoder()).output(output);
		for (var flag : flags) {
			builder.flag(flag);
		}
		return builder.build().provide(name, LogConfig.builder().build());
	}

	@Test
	void appenderFlagParseEmptyListIsNoFlags() {
		assertEquals(Set.of(), AppenderFlag.parse(List.of()));
	}

	@Test
	void reentryDropFlagDropsReentrantAppend() {
		var output = new ListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.REENTRY_DROP);
		output.setConsumer((e, s) -> {
			// A naughty output that logs during its own write - should be dropped.
			testAppender.append(TestEventBuilder.of().build(b -> b.message("reentrant")));
		});
		testAppender.append(TestEventBuilder.of().build(b -> b.message("original")));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void reentryLogFlagDropsReentrantAppendAndLogsDiagnostic() {
		var output = new ListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.REENTRY_LOG);
		output.setConsumer((e, s) -> {
			testAppender.append(TestEventBuilder.of().build(b -> b.message("reentrant")));
		});
		testAppender.append(TestEventBuilder.of().build(b -> b.message("original")));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
		String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
		assertTrue(diagnostic.contains("reentrant appender"), () -> "expected reentry diagnostic, got: " + diagnostic);
	}

	@Test
	void immediateFlushIsDefault() {
		var output = new CountingListLogOutput();
		var testAppender = appender("test", output);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("single")));
		testAppender.append(new LogEvent[] { TestEventBuilder.of().build(b -> b.message("batch")) }, 1);
		assertEquals(2, output.flushCount);
	}

	@Test
	void disableImmediateFlushFlagPreventsFlush() {
		var output = new CountingListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.DISABLE_IMMEDIATE_FLUSH);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("single")));
		testAppender.append(new LogEvent[] { TestEventBuilder.of().build(b -> b.message("batch")) }, 1);
		assertEquals(0, output.flushCount);
	}

	@Test
	void immediateFlushFlagsRespectedWithReuseBuffer() {
		var output = new CountingListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.REUSE_BUFFER, AppenderFlag.DISABLE_IMMEDIATE_FLUSH);
		assertInstanceOf(ReuseBufferLogAppender.class, testAppender);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("single")));
		testAppender.append(new LogEvent[] { TestEventBuilder.of().build(b -> b.message("batch")) }, 1);
		assertEquals(0, output.flushCount);
	}

	@Test
	void immediateFlushFlagsRespectedWithThreadLocalBuffer() {
		var output = new CountingListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.THREAD_LOCAL_BUFFER,
				AppenderFlag.DISABLE_IMMEDIATE_FLUSH);
		assertInstanceOf(ThreadLocalBufferLogAppender.class, testAppender);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("single")));
		testAppender.append(new LogEvent[] { TestEventBuilder.of().build(b -> b.message("batch")) }, 1);
		assertEquals(0, output.flushCount);
	}

	@Test
	void threadLocalBufferFlagReusesBufferPerThread() {
		var output = new ListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.THREAD_LOCAL_BUFFER);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("one")));
		testAppender.append(TestEventBuilder.of().build(b -> b.message("two")));
		assertEquals(List.of("one", "two"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void immediateFlushFlagsRespectedWithSynchronizedThreadLocalBuffer() {
		var output = new CountingListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER,
				AppenderFlag.DISABLE_IMMEDIATE_FLUSH);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("single")));
		testAppender.append(new LogEvent[] { TestEventBuilder.of().build(b -> b.message("batch")) }, 1);
		assertEquals(0, output.flushCount);
	}

	@Test
	void synchronizedThreadLocalBufferFlagReusesBufferPerThread() {
		var output = new ListLogOutput();
		var testAppender = appender("test", output, AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER);
		testAppender.append(TestEventBuilder.of().build(b -> b.message("one")));
		testAppender.append(TestEventBuilder.of().build(b -> b.message("two")));
		assertEquals(List.of("one", "two"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void appenderLevelFlagsMergeOntoExistingAppenderWithoutReuseBuffer() {
		LogConfig config = LogConfig.builder().build();
		var output = new CountingListLogOutput();
		List<LogProvider<LogAppender>> providers = List
			.of(LogAppender.builder("test").encoder(LogFormatter.builder().message().encoder()).output(output).build());
		var appenders = new LogAppender.Appenders("test-route", config, providers);
		var result = appenders.flags(Set.of(AppenderFlag.DISABLE_IMMEDIATE_FLUSH)).asSingle();
		result.start(config);
		assertInstanceOf(DefaultLogAppender.class, result);
		result.append(TestEventBuilder.of().build(b -> b.message("hello")));
		assertEquals(0, output.flushCount);
	}

	@Test
	void appenderLevelFlagsAlreadyPresentIsNoop() {
		LogConfig config = LogConfig.builder().build();
		var output = new CountingListLogOutput();
		List<LogProvider<LogAppender>> providers = List.of(LogAppender.builder("test")
			.encoder(LogFormatter.builder().message().encoder())
			.output(output)
			.flag(AppenderFlag.DISABLE_IMMEDIATE_FLUSH)
			.build());
		var appenders = new LogAppender.Appenders("test-route", config, providers);
		var result = appenders.flags(Set.of(AppenderFlag.DISABLE_IMMEDIATE_FLUSH)).asSingle();
		result.start(config);
		result.append(TestEventBuilder.of().build(b -> b.message("hello")));
		assertEquals(0, output.flushCount);
	}

	@Test
	void appenderLevelFlagsMergeOntoMultiAppenderComposite() {
		LogConfig config = LogConfig.builder().build();
		var outputA = new CountingListLogOutput();
		var outputB = new CountingListLogOutput();
		List<LogProvider<LogAppender>> providers = List.of(
				LogAppender.builder("a").encoder(LogFormatter.builder().message().encoder()).output(outputA).build(),
				LogAppender.builder("b").encoder(LogFormatter.builder().message().encoder()).output(outputB).build());
		var appenders = new LogAppender.Appenders("test-route", config, providers)
			.flags(Set.of(AppenderFlag.DISABLE_IMMEDIATE_FLUSH));
		var result = appenders.asSingle();
		result.start(config);
		assertInstanceOf(CompositeLogAppender.class, result);
		result.append(TestEventBuilder.of().build(b -> b.message("hello")));
		assertEquals(0, outputA.flushCount);
		assertEquals(0, outputB.flushCount);
	}

	static class CountingListLogOutput extends ListLogOutput {

		int flushCount = 0;

		@Override
		public void flush() {
			flushCount++;
			super.flush();
		}

	}

}
