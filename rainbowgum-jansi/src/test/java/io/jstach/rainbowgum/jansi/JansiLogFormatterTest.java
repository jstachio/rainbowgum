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
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable, messageFormatter, args);
		var formatter = JansiLogFormatter.builder().build();
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		String actual = sb.toString();
		String levelString = switch (level) {
		case ALL, TRACE -> "[39mTRACE";
		case DEBUG -> "[2;36;39mDEBUG";
		case INFO -> "[1;34mINFO "; 
		case WARNING -> "[31mWARN ";
		case ERROR -> "[1;31mERROR";
		case OFF -> "[39mERROR";
		};
		String expected = """
				00:00:00.000[36;39m [2m[main][m %s[39;0m [35mloggerName[39m - message
				""".formatted(levelString);
		//String expected = "00:00:00.000[36;39m [2m[main][m "+ levelString + "[39;0m [35mloggerName[39m - message\n";
		assertEquals(expected, actual);
	}

}
