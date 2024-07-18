package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

class RainbowGumEventBuilderTest {

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

		LEVEL_LOGGER("logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]\n") {
		},
		REPLACEABLE_LOGGER("""
				ERROR logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]
				io.jstach.rainbowgum.slf4j.RainbowGumEventBuilderTest$_Test.log
				""") {

			@Override
			Logger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, Level level) {
				var handler = LogEventHandler.ofCallerInfo(loggerName(), appender, mdc, 0);
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
		OVERRIDE_MDC("logger {mdcKey1=value2&key1=value1} - hello [arg0]\n") {
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
		TWO_ARG("logger {mdcKey1=mdcValue1&key1=value1} - hello two [arg0] [arg1]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello two {} {}");
				builder.addArgument("[arg0]");
				builder.addArgument("[arg1]");
				builder.addKeyValue("key1", () -> "value1");
			}
		},
		TWO_ARG_LOG("logger {mdcKey1=mdcValue1&key1=value1} - hello two [arg0] [arg1]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello two {} {}", "[arg0]", "[arg1]");
			}
		},
		ONE_ARG_LOG("logger {mdcKey1=mdcValue1&key1=value1} - hello one [arg0]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello one {}", "[arg0]");
			}
		},
		THREE_ARG("logger {mdcKey1=mdcValue1&key1=value1} - hello three [arg0] [arg1] [arg2]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello three {} {} {}");
				builder.addArgument("[arg0]");
				builder.addArgument("[arg1]");
				builder.addArgument(() -> "[arg2]");
				builder.addKeyValue("key1", "value1");
			}
		},
		THREE_ARG_LOG("logger {mdcKey1=mdcValue1&key1=value1} - hello three [arg0] [arg1] [arg2]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.addKeyValue("key1", "value1");
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				builder.log("hello three {} {} {}", "[arg0]", "[arg1]", "[arg2]");
			}
		},
		SUPPLIER_MESSAGE_LOG("logger {mdcKey1=mdcValue1&key1=value1} - hello supplier\n") {
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
		THROWABLE("logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]\n" + "fail") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.setCause(new RuntimeException("fail"));
			}
		},
		CALLER_INFO("""
				logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]
				io.jstach.rainbowgum.slf4j.RainbowGumEventBuilderTest$_Test$13.callerLog
								""") {
			@Override
			LevelLogger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, System.Logger.Level level) {
				var handler = LogEventHandler.ofCallerInfo(loggerName(), appender, mdc, 0);
				return LevelLogger.of(Levels.toSlf4jLevel(level), handler);
			}

			@Override
			protected void log(LoggingEventBuilder builder) {
				callerLog(builder);
			}

			private void callerLog(LoggingEventBuilder builder) {
				builder.log();
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
			var handler = LogEventHandler.of(loggerName(), appender, mdc);
			return LevelLogger.of(Levels.toSlf4jLevel(level), handler);
		}

		Level level() {
			return System.Logger.Level.INFO;
		}

		protected void mdc(MDCAdapter mdc) {
			mdc.put("mdcKey1", "mdcValue1");
		}

		protected void build(LoggingEventBuilder builder) {
			builder.setMessage("hello {}");
			builder.addArgument("[arg0]");
			builder.addKeyValue("key1", "value1");
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
