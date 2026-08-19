package io.jstach.rainbowgum.spring.boot4;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.pattern.format.PatternCompiler;
import io.jstach.rainbowgum.pattern.format.PatternConfig;
import io.jstach.rainbowgum.pattern.format.PatternRegistry;

class SpringBootKeywordFactoryTest {

	private static PatternCompiler compiler() {
		var registry = PatternRegistry.of();
		for (var kf : SpringBootKeywordFactory.values()) {
			registry.register(kf);
		}
		return PatternCompiler.builder().patternRegistry(registry).patternConfig(PatternConfig.ofUniversal()).build();
	}

	private static Throwable throwableWithFrames(String message, String... methodNames) {
		var throwable = new RuntimeException(message);
		var frames = new StackTraceElement[methodNames.length];
		for (int i = 0; i < methodNames.length; i++) {
			frames[i] = new StackTraceElement("com.example.App", methodNames[i], "App.java", i + 1);
		}
		throwable.setStackTrace(frames);
		return throwable;
	}

	private static String format(String pattern, Throwable throwable) {
		var event = LogEvent
			.of(System.Logger.Level.INFO, "io.jstach.logger", "hello", MutableKeyValues.of().freeze(), throwable)
			.freeze(Instant.EPOCH);
		StringBuilder sb = new StringBuilder();
		compiler().compile(pattern).format(sb, event);
		return sb.toString();
	}

	@Test
	void testWExPrefixesNewlineAndAppendsPackagingData() {
		var throwable = throwableWithFrames("boom", "a", "b");
		String actual = format("%wEx", throwable);
		assertEquals("\njava.lang.RuntimeException: boom\n" + "\tat com.example.App.a(App.java:1) [na:na]\n"
				+ "\tat com.example.App.b(App.java:2) [na:na]\n", actual);
	}

	@Test
	void testWexLowerCaseAliasHasNoPackagingData() {
		var throwable = throwableWithFrames("boom", "a", "b");
		String actual = format("%wex", throwable);
		assertEquals("\njava.lang.RuntimeException: boom\n" + "\tat com.example.App.a(App.java:1)\n"
				+ "\tat com.example.App.b(App.java:2)\n", actual);
	}

	@Test
	void testWExHonorsDepthOption() {
		var throwable = throwableWithFrames("boom", "a", "b", "c", "d");
		String actual = format("%wEx{2}", throwable);
		assertEquals("\njava.lang.RuntimeException: boom\n" + "\tat com.example.App.a(App.java:1) [na:na]\n"
				+ "\tat com.example.App.b(App.java:2) [na:na]\n" + "\t... 2 frames truncated\n", actual);
	}

	@Test
	void testWExOmitsNewlineWhenNoThrowable() {
		String actual = format("%wEx", null);
		assertEquals("", actual);
	}

}
