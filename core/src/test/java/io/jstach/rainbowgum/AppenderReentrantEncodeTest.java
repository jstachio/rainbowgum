package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * An argument's {@code toString()} that logs runs while the appender encodes the outer
 * event, which for the thread local buffer appenders is outside the lock. The nested
 * event must not clear the buffer the outer event is being encoded into.
 */
class AppenderReentrantEncodeTest {

	private static final LogConfig CONFIG = LogConfig.builder().build();

	private static final LogEncoder ENCODER = LogFormatter.builder()
		.message()
		.encoder()
		.build()
		.provide("test", CONFIG);

	static Stream<Arguments> types() {
		List<Arguments> args = new ArrayList<>();
		for (var type : AppenderType.values()) {
			args.add(Arguments.of(type, Set.of(), List.of("inner", "outer arg")));
			/*
			 * LOCK_NEW_BUFFER keeps no thread state, so it cannot see reentry while it
			 * encodes outside the lock and the nested event is kept. Its buffer is fresh,
			 * so neither event is damaged.
			 */
			var dropped = type == AppenderType.LOCK_NEW_BUFFER ? List.of("inner", "outer arg") : List.of("outer arg");
			args.add(Arguments.of(type, EnumSet.of(AppenderFlag.REENTRY_DROP), dropped));
		}
		return args.stream();
	}

	@ParameterizedTest
	@MethodSource("types")
	void argumentThatLogsWhileEncodingKeepsBothEvents(AppenderType type, Set<AppenderFlag> flags,
			List<String> expected) {
		var output = new ListLogOutput();
		var appender = DirectLogAppender.of("test", output, ENCODER, type, flags, CONFIG);
		Object logsWhenFormatted = new Object() {
			@Override
			public String toString() {
				appender.append(event("inner", null));
				return "arg";
			}
		};
		appender.append(event("outer {}", logsWhenFormatted));
		assertEquals(expected, output.events().stream().map(Entry::getValue).toList());
	}

	private static LogEvent event(String message, @Nullable Object arg) {
		return LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(),
				StandardMessageFormatter.SLF4J, arg);
	}

}
