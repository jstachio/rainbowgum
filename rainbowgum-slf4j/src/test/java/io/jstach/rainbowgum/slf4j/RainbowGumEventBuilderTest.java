package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService;
import io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService.DepthAwareEventBuilder;

/*
 * LoggingEventBuilder mutates itself and returns this, so a discarded return value here
 * is never actually a bug - this whole class exists to exercise that mutation, and the
 * real mistake to guard against (forgetting the terminal log() call) isn't something
 * CheckReturnValue catches anyway.
 */
@SuppressWarnings("CheckReturnValue")
class RainbowGumEventBuilderTest {

	/*
	 * setLogger (part of the DepthAwareEventBuilder contract, used by decorator authors
	 * to redirect where a built event ultimately goes) had no caller anywhere - nothing
	 * redirects output today. Doesn't fit the shared enum-matrix harness below, since
	 * that always expects output in the same StringBuilder the logger itself was
	 * constructed with.
	 */
	@Test
	void testSetLoggerRedirectsOutput() {
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		StringBuilder original = new StringBuilder();
		StringBuilder redirected = new StringBuilder();
		LogEventHandler handler = LogEventHandler.of("logger", e -> e.formattedMessage(original), mdc,
				NoopLogEventFactory.INSTANCE);
		var builder = LevelLogger.of(org.slf4j.event.Level.INFO, handler)
			.makeLoggingEventBuilder(org.slf4j.event.Level.INFO);
		assertInstanceOf(LoggerDecoratorService.DepthAwareEventBuilder.class, builder);
		var depthAware = (LoggerDecoratorService.DepthAwareEventBuilder) builder;
		depthAware.setLogger(e -> e.formattedMessage(redirected));
		builder.log("hello");
		assertEquals("", original.toString());
		assertEquals("hello", redirected.toString());
	}

	/*
	 * throwable()/arguments()/keyValues() (the DepthAwareEventBuilder accessors added
	 * alongside message() for decorator read-modify-write use cases) each need their own
	 * unset-vs-set-and-a-snapshot-not-a-live-view coverage - none of that fits the
	 * expected-formatted-output matrix below, which only ever checks the final logged
	 * line, not what a decorator sees mid-build.
	 */
	private static DepthAwareEventBuilder newBuilder() {
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		LogEventHandler handler = LogEventHandler.of("logger", e -> {
		}, mdc, NoopLogEventFactory.INSTANCE);
		var builder = LevelLogger.of(org.slf4j.event.Level.INFO, handler)
			.makeLoggingEventBuilder(org.slf4j.event.Level.INFO);
		assertInstanceOf(DepthAwareEventBuilder.class, builder);
		return (DepthAwareEventBuilder) builder;
	}

	@Test
	void testThrowableUnsetIsNull() {
		assertNull(newBuilder().throwable());
	}

	@Test
	void testThrowableReturnsWhatWasSet() {
		var builder = newBuilder();
		var cause = new RuntimeException("fail");
		builder.setCause(cause);
		assertSame(cause, builder.throwable());
	}

	@Test
	void testArgumentsUnsetIsEmpty() {
		assertEquals(List.of(), newBuilder().arguments());
	}

	@Test
	void testArgumentsReturnsWhatWasAddedInOrder() {
		var builder = newBuilder();
		builder.addArgument("a").addArgument("b");
		assertEquals(List.of("a", "b"), builder.arguments());
	}

	@Test
	void testArgumentsSnapshotIsNotALiveView() {
		var builder = newBuilder();
		builder.addArgument("a");
		var snapshot = builder.arguments();
		builder.addArgument("b");
		assertEquals(List.of("a"), snapshot, "a snapshot taken before addArgument(\"b\") must not see it");
		assertThrows(UnsupportedOperationException.class, () -> snapshot.add("c"));
	}

	@Test
	void testKeyValuesUnsetFallsBackToMdc() {
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		mdc.put("mdcKey", "mdcValue");
		LogEventHandler handler = LogEventHandler.of("logger", e -> {
		}, mdc, NoopLogEventFactory.INSTANCE);
		var builder = (DepthAwareEventBuilder) LevelLogger.of(org.slf4j.event.Level.INFO, handler)
			.makeLoggingEventBuilder(org.slf4j.event.Level.INFO);
		assertEquals("mdcValue", builder.keyValues().getValueOrNull("mdcKey"));
	}

	@Test
	void testKeyValuesReflectsAddedKeyValue() {
		var builder = newBuilder();
		builder.addKeyValue("key1", "value1");
		assertEquals("value1", builder.keyValues().getValueOrNull("key1"));
	}

	@Test
	void testStaticHelpersReturnDefaultsForAPlainLoggingEventBuilder() {
		LoggingEventBuilder plain = org.slf4j.spi.NOPLoggingEventBuilder.singleton();
		assertNull(DepthAwareEventBuilder.throwable(plain));
		assertEquals(List.of(), DepthAwareEventBuilder.arguments(plain));
		assertTrue(DepthAwareEventBuilder.keyValues(plain).isEmpty());
	}

	@ParameterizedTest
	@MethodSource("args")
	void test(_Test test, System.Logger.Level level) {
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();

		test.mdc(mdc);

		var formatter = test.formatter();

		StringBuilder sb = new StringBuilder();
		LogEventLogger appender = e -> {
			formatter.format(sb, e);
		};

		var logger = test.logger(mdc, appender, level);
		var _b = logger.makeLoggingEventBuilder(Levels.toSlf4jLevel(level));
		test.build(_b);
		test.log(_b);
		if (_b instanceof RainbowGumEventBuilder) {
			String expected = test.expected;
			String actual = sb.toString();
			assertEquals(expected, actual);
		}
		else {
			assertEquals("", sb.toString());
		}

	}

	private static Stream<Arguments> args() {
		return EnumCombinations.args(_Test.class, System.Logger.Level.class);
	}

	enum _Test {

		LEVEL_LOGGER("logger {mdcKey1=mdcValue1, key1=value1} - hello [arg0]\n") {
		},
		REPLACEABLE_LOGGER("""
				ERROR logger {mdcKey1=mdcValue1, key1=value1} - hello [arg0]
				io.jstach.rainbowgum.slf4j.RainbowGumEventBuilderTest$_Test.log
				""") {

			@Override
			Logger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, Level level) {
				var handler = LogEventHandler.ofCallerInfo(loggerName(), appender, mdc, 0,
						NoopLogEventFactory.INSTANCE);
				var logger = ReplaceableLogger.of(Levels.toSlf4jLevel(level), handler);
				logger.setLevel(org.slf4j.event.Level.ERROR);
				return logger;
			}

			@Override
			LogFormatter formatter() {
				var formatter = super.formatter();
				return LogFormatter.builder().level().space().add(formatter).build();
			}
		},
		OVERRIDE_MDC("logger {mdcKey1=value2, key1=value1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.addKeyValue("mdcKey1", "value2");
			}

		},
		EMPTY_MDC("logger {key1=value1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void mdc(MDCAdapter mdc) {
			}

		},
		TWO_ARG("logger {mdcKey1=mdcValue1, key1=value1} - hello two [arg0] [arg1]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello two {} {}")
					.addArgument("[arg0]")
					.addArgument("[arg1]")
					.addKeyValue("key1", () -> "value1");
			}
		},
		TWO_ARG_LOG("logger {mdcKey1=mdcValue1, key1=value1} - hello two [arg0] [arg1]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello two {} {}", "[arg0]", "[arg1]");
			}
		},
		ONE_ARG_LOG("logger {mdcKey1=mdcValue1, key1=value1} - hello one [arg0]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello one {}", "[arg0]");
			}
		},
		THREE_ARG("logger {mdcKey1=mdcValue1, key1=value1} - hello three [arg0] [arg1] [arg2]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello three {} {} {}")
					.addArgument("[arg0]")
					.addArgument("[arg1]")
					.addArgument(() -> "[arg2]")
					.addKeyValue("key1", "value1");
			}
		},
		THREE_ARG_LOG("logger {mdcKey1=mdcValue1, key1=value1} - hello three [arg0] [arg1] [arg2]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello three {} {} {}", "[arg0]", "[arg1]", "[arg2]");
			}
		},
		SUPPLIER_MESSAGE_LOG("logger {mdcKey1=mdcValue1, key1=value1} - hello supplier\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log(() -> "hello supplier");
			}
		},
		NO_ARG("logger {mdcKey1=mdcValue1} - hello no arg\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello no arg");
			}
		},
		THROWABLE("logger {mdcKey1=mdcValue1, key1=value1} - hello [arg0]\n" + "fail") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.setCause(new RuntimeException("fail"));
			}
		},
		CALLER_INFO("""
				logger {mdcKey1=mdcValue1, key1=value1} - hello [arg0]
				io.jstach.rainbowgum.slf4j.RainbowGumEventBuilderTest$_Test$13.callerLog
								""") {
			@Override
			LevelLogger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, System.Logger.Level level) {
				var handler = LogEventHandler.ofCallerInfo(loggerName(), appender, mdc, 0,
						NoopLogEventFactory.INSTANCE);
				return LevelLogger.of(Levels.toSlf4jLevel(level), handler);
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				callerLog(builder);
			}

			private void callerLog(LoggingEventBuilder builder) {
				builder.log();
			}
		},
		NULL_KEY_VALUE("logger {mdcKey1=mdcValue1, key1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello {}").addArgument("[arg0]").addKeyValue("key1", (Object) null);
			}
		},
		NULL_KEY_VALUE_SUPPLIER("logger {mdcKey1=mdcValue1, key1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello {}").addArgument("[arg0]").addKeyValue("key1", () -> null);
			}
		},
		/*
		 * addMarker is a no-op that just returns this (RainbowGumEventBuilder has no
		 * marker support of its own - see LoggerDecoratorService's javadoc example for
		 * how a decorator can surface a marker as a key value instead), but the call
		 * itself was never exercised anywhere.
		 */
		ADD_MARKER("logger {mdcKey1=mdcValue1, key1=value1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.addMarker(org.slf4j.MarkerFactory.getMarker("M"));
			}
		},
		/*
		 * log(String, Object...) skips the arg-copy loop entirely when the varargs array
		 * itself is null (as opposed to empty), e.g. logger.info(msg, (Object[]) null) -
		 * distinct from the array simply having zero elements.
		 */
		NULL_ARGS_LOG("logger {mdcKey1=mdcValue1, key1=value1} - hello null args\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello null args", (Object[]) null);
			}
		};

		final String expected;

		_Test(String expected) {
			this.expected = expected;
		}

		String loggerName() {
			return "logger";
		}

		Logger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, System.Logger.Level level) {
			var handler = LogEventHandler.of(loggerName(), appender, mdc, NoopLogEventFactory.INSTANCE);
			return LevelLogger.of(Levels.toSlf4jLevel(level), handler);
		}

		Level level() {
			return System.Logger.Level.INFO;
		}

		protected void mdc(MDCAdapter mdc) {
			mdc.put("mdcKey1", "mdcValue1");
		}

		protected void build(LoggingEventBuilder builder) {
			builder.setMessage("hello {}").addArgument("[arg0]").addKeyValue("key1", "value1");
		}

		protected void log(LoggingEventBuilder builder) {
			builder.log();
		}

		LogFormatter formatter() {
			var f = StandardEventFormatter.builder()
				.threadFormatter(LogFormatter.noop())
				.levelFormatter(LogFormatter.noop())
				.timestampFormatter(LogFormatter.noop())
				.keyValuesFormatter(LogFormatter.builder().keyValues().build())
				.throwableFormatter((sb, t) -> {
					sb.append(t.getMessage());
				})
				.build();
			var callerInfo = LogFormatter.of((sb, e) -> {
				var info = e.callerOrNull();
				if (info != null) {
					sb.append(info.className());
					sb.append(".");
					sb.append(info.methodName());
					sb.append("\n");
				}
			});
			return LogFormatter.builder().add(f).add(callerInfo).build();
		}

	}

}
