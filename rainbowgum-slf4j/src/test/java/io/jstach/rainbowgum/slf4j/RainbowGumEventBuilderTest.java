package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

class RainbowGumEventBuilderTest {

	@ParameterizedTest
	@EnumSource(value = _Test.class)
	void test(_Test test) {
		RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();
		test.mdc(mdc);

		var formatter = test.formatter();

		StringBuilder sb = new StringBuilder();
		LogEventLogger appender = e -> {
			formatter.format(sb, e);
		};

		var level = test.level();
		BaseLogger logger = test.logger(mdc, appender, level);
		RainbowGumEventBuilder builder = new RainbowGumEventBuilder(logger, mdc, level) {
			@Override
			protected Instant now() {
				return Instant.EPOCH;
			}
		};
		test.build(builder);
		test.log(builder);

		String expected = test.expected;
		String actual = sb.toString();
		assertEquals(expected, actual);

	}

	enum _Test {

		LEVEL_LOGGER("[main] INFO logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]\n") {
		},
		CHANGE_LOGGER("""
				[main] INFO logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]
				io.jstach.rainbowgum.slf4j.RainbowGumEventBuilderTest$_Test.log
				""") {

			@Override
			BaseLogger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, Level level) {
				return new ChangeableLogger(loggerName(), appender, mdc, Levels.toSlf4jInt(level), true);

			}
		},
		OVERRIDE_MDC("[main] INFO logger {mdcKey1=value2&key1=value1} - hello [arg0]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.addKeyValue("mdcKey1", "value2");
			}

		},
		TWO_ARG("[main] INFO logger {mdcKey1=mdcValue1&key1=value1} - hello two [arg0] [arg1]\n" + "") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello two {} {}");
				builder.addArgument("[arg0]");
				builder.addArgument("[arg1]");
				builder.addKeyValue("key1", "value1");
			}
		},
		THREE_ARG("[main] INFO logger {mdcKey1=mdcValue1&key1=value1} - hello three [arg0] [arg1] [arg2]\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello three {} {} {}");
				builder.addArgument("[arg0]");
				builder.addArgument("[arg1]");
				builder.addArgument("[arg2]");
				builder.addKeyValue("key1", "value1");
			}
		},
		NO_ARG("[main] INFO logger {mdcKey1=mdcValue1} - hello no arg\n") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				builder.setMessage("hello no arg");
			}
		},
		THROWABLE("[main] INFO logger {mdcKey1=mdcValue1&key1=value1} - hello [arg0]\n" + "fail") {
			@Override
			protected void build(LoggingEventBuilder builder) {
				super.build(builder);
				builder.setCause(new RuntimeException("fail"));
			}
		};

		final String expected;

		_Test(String expected) {
			this.expected = expected;
		}

		String loggerName() {
			return "logger";
		}

		BaseLogger logger(RainbowGumMDCAdapter mdc, LogEventLogger appender, System.Logger.Level level) {
			return LevelLogger.of(Levels.toSlf4jLevel(level), loggerName(), appender, mdc);
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
