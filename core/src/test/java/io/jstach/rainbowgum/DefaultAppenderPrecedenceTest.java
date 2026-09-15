package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * {@code "console"} is one of the two well known appender names
 * {@link DefaultAppenderRegistry} gives a default output to when nothing is configured
 * (stdout), a default meant to be overridable by
 * {@link LogAppender#APPENDER_OUTPUT_PROPERTY}, unlike an ordinary explicit
 * {@link LogAppender.Builder#output} value, which always wins over a property outright.
 * This precedence had no test before the appender-construction refactor that unified
 * property-driven and programmatic appender construction onto one
 * {@link LogAppender.Builder} path; this class closes that gap. See
 * {@code FileOutputPropertiesTest} in {@code rainbowgum-file} for {@code "file"}'s own,
 * differently-ranked default ({@link LogProperties#FILE_PROPERTY} outranks the generic
 * property outright; the file module is not a dependency of core so that precedence
 * cannot be exercised from here).
 */
class DefaultAppenderPrecedenceTest {

	@Test
	void consolePropertyOverridesStandardOutDefault() {
		var props = LogProperties.builder().fromProperties("""
				logging.appenders=console
				logging.appender.console.output=list:///
				""").build();
		var config = LogConfig.builder().properties(props).build();
		try (var gum = RainbowGum.builder(config).build().start()) {
			var appender = assertInstanceOf(DirectLogAppender.class,
					config.serviceRegistry().findOrNull(LogAppender.class, "default.console"));
			assertInstanceOf(ListLogOutput.class, appender.output());
		}
	}

}
