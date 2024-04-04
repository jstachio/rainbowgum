package io.jstach.rainbowgum.jansi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMessageFormatter;

class JansiLogFormatterTest {

	@ParameterizedTest
	@EnumSource(Level.class)
	void testFormat(Level level) {
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 1;
		String loggerName = "loggerName";
		String message = "message";
		KeyValues keyValues = KeyValues.MutableKeyValues.of().add("k1", "v1");
		@Nullable
		Throwable throwable = null;
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		@Nullable
		List<@Nullable Object> args = List.of();
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, args);
		var formatter = JansiLogFormatter.builder()
			.keyValuesFormatter(LogFormatter.builder().keyValues().build())
			.build();
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		String actual = sb.toString();

		String levelString = switch (level) {
			case ALL, TRACE -> "[39mTRACE";
			case DEBUG -> "[39mDEBUG";
			case INFO -> "[1;34mINFO ";
			case WARNING -> "[31mWARN ";
			case ERROR -> "[1;31mERROR";
			case OFF -> "[39mERROR";
		};
		// 00:00:00.000[36;39m [2m[main][m [39mTRACE[39;0m [35mloggerName
		// [37;2m{k1=v1}[39;0;39m - message
		// String expected = "00:00:00.000[36;39m [2m[main][m [39mTRACE[39;0m
		// [35mloggerName \u001B[37;2m{k1=v1}[39;0;39m - message\n";

		/*
		 * BEWARE There are \u001B escape sequences in these strings. TODO put the literal
		 * code in.
		 */
		String expected = """
				[36m00:00:00.000[39m [2m[main][m \u001B%s[39;0m [35mloggerName [37;2m{k1=v1}[39m - message
				""".formatted(levelString);
		// String expected = "00:00:00.000[36;39m [2m[main][m "+ levelString +
		// "[39;0m [35mloggerName [39m - message\n";
		// if (level == Level.DEBUG) {
		System.out.print(actual);
		// }
		assertEquals(expected, actual);
	}

}
