package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LogEventBuilderTest {

	@ParameterizedTest
	@MethodSource("args")
	void testBuilder(BuilderTest test) {
		var level = test.level();
		LogEvent.Builder builder = test.builder(level);
		String message = "hello {} {}";
		Supplier<?> argSupplier = test.argSupplier();
		@Nullable
		Object arg = test.arg();
		KeyValues keyValues = KeyValues.MutableKeyValues.of().add("k1", "v1").add("k2", "v2").add("k3", "v3");
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		long threadId = test.threadId();
		String threadName = test.threadName();
		Throwable throwable = test.throwable();
		Instant timestamp = Instant.EPOCH;
		if (threadName != null) {
			builder.threadName(threadName);
		}
		builder.threadId(threadId);
		builder.timestamp(timestamp);
		builder.message(message);
		builder.keyValues(keyValues);
		if (arg != null) {
			builder.arg(arg);
		}
		if (argSupplier != null) {
			builder.arg(argSupplier);
		}
		builder.messageFormatter(messageFormatter);
		builder.throwable(throwable);
		var event = builder.eventOrNull();

		LogFormatter keyValuesFormatter = test.keyValuesFormatter();

		var formatter = LogFormatter.builder() //
			.threadName() //
			.text(",") //
			.threadId() //
			.text(",") //
			.timeStamp() //
			.text(",") //
			.level() //
			.text(",")
			.loggerName() //
			.text(",") //
			.text("'") //
			.message() //
			.text("'") //
			.text(",") //
			.text("{") //
			.add(keyValuesFormatter) //
			.text("}") //
			.text(",") //
			.event((sb, e) -> {
				var t = e.throwableOrNull();
				if (t == null) {
					sb.append("null");
					return;
				}
				sb.append(t.getClass().getSimpleName()).append(":").append(t.getMessage());
			}) //
			.newline() //
			.build();
		String actual;
		if (event == null) {
			actual = "";
		}
		else {
			StringBuilder sb = new StringBuilder();
			formatter.format(sb, event);
			actual = sb.toString();
		}
		String expected = test.expected;
		assertEquals(expected, actual);
	}

	private static Stream<Arguments> args() {
		return EnumCombinations.args(BuilderTest.class);
	}

	enum BuilderTest {

		EVERYTHING(
				"""
						threadName,1,1970-01-01T00:00:00Z,INFO,test,'hello arg argSupplier',{k1=v1&k2=v2&k3=v3},RuntimeException:expected
						""") {
			@Override
			@Nullable
			Throwable throwable() {
				return new RuntimeException("expected");
			}
		},
		SINGLE_KEY_VALUE("""
				threadName,1,1970-01-01T00:00:00Z,INFO,test,'hello arg argSupplier',{k2=v2},null
				""") {
			@Override
			LogFormatter keyValuesFormatter() {
				return LogFormatter.builder().keyValue("k2", "blah").build();
			}
		},
		SELECT_KEY_VALUES("""
				threadName,1,1970-01-01T00:00:00Z,INFO,test,'hello arg argSupplier',{k2=v2&k3=v3},null
				""") {
			@Override
			LogFormatter keyValuesFormatter() {
				return LogFormatter.builder().keyValues(List.of("k2", "k3")).build();
			}
		},
		NO_KEY_VALUES_SELECTED("""
				threadName,1,1970-01-01T00:00:00Z,INFO,test,'hello arg argSupplier',{},null
				""") {
			@Override
			LogFormatter keyValuesFormatter() {
				return LogFormatter.builder().keyValues(List.of()).build();
			}
		},
		NOOP("") {
			@Override
			LogEvent.Builder builder(Level level) {
				return TestEventBuilder.of().loggerName("test").level(level).noop();

			}
		};

		private final String expected;

		private BuilderTest(String expected) {
			this.expected = expected;
		}

		LogFormatter keyValuesFormatter() {
			return LogFormatter.builder().keyValues().build();
		}

		@Nullable
		Throwable throwable() {
			return null;
		}

		@Nullable
		Supplier<?> argSupplier() {
			return () -> "argSupplier";
		}

		@Nullable
		Object arg() {
			return "arg";
		}

		@Nullable
		String threadName() {
			return "threadName";
		}

		Level level() {
			return Level.INFO;
		}

		long threadId() {
			return 1;
		}

		LogEvent.Builder builder(Level level) {
			return TestEventBuilder.of().loggerName("test").level(level).event();
		}

	}

}
