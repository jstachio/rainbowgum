package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeMode;
import io.jstach.rainbowgum.LogConfig.Globals.AnsiMode;
import io.jstach.rainbowgum.LogConfig.Globals.AppenderLock;
import io.jstach.rainbowgum.LogConfig.Globals.ThreadLocalMode;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor.Source;

class GlobalsTest {

	@Test
	void defaults() {
		var globals = LogConfig.builder().build().globals();
		assertEquals(ChangeMode.OFF, globals.changeMode());
		assertEquals(AnsiMode.AUTO, globals.ansiMode());
		assertEquals(AppenderLock.AUTO, globals.appenderLock());
		assertEquals(ThreadLocalMode.ALLOW, globals.threadLocal());
		assertFalse(globals.optimize());
		assertEquals(Set.of(Source.Standard.values()), globals.keyValueSources());
	}

	@Test
	void builderSetsGlobals() {
		var globals = LogConfig.builder()
			.changeMode(ChangeMode.ON)
			.ansiMode(AnsiMode.OFF)
			.appenderLock(AppenderLock.REENTRANT)
			.threadLocal(ThreadLocalMode.FORBID)
			.optimize(true)
			.keyValueSources(Set.of(Source.Standard.SLF4J))
			.build()
			.globals();
		assertEquals(ChangeMode.ON, globals.changeMode());
		assertEquals(AnsiMode.OFF, globals.ansiMode());
		assertEquals(AppenderLock.REENTRANT, globals.appenderLock());
		assertEquals(ThreadLocalMode.FORBID, globals.threadLocal());
		assertTrue(globals.optimize());
		assertEquals(Set.of(Source.Standard.SLF4J), globals.keyValueSources());
	}

	@Test
	void propertiesOverrideTheBuilder() {
		var props = LogProperties.builder().fromProperties("""
				logging.global.ansi.mode=on
				logging.global.optimize=false
				""").build();
		var globals = LogConfig.builder().ansiMode(AnsiMode.OFF).optimize(true).properties(props).build().globals();
		assertEquals(AnsiMode.ON, globals.ansiMode());
		assertFalse(globals.optimize());
	}

	@ParameterizedTest
	@CsvSource({ "auto, AUTO", "on, ON", "off, OFF", "true, ON", "false, OFF", "ON, ON" })
	void ansiModeValues(String value, AnsiMode expected) {
		var props = LogProperties.builder().fromProperties("logging.global.ansi.mode=" + value).build();
		assertEquals(expected, LogConfig.builder().properties(props).build().globals().ansiMode());
	}

	@Test
	void everyBadGlobalIsReportedTogether() {
		var props = LogProperties.builder().fromProperties("""
				logging.global.ansi.mode=sometimes
				logging.global.optimize=yes
				logging.global.threadLocal=never
				logging.global.appender.lock=sync
				""").build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder().properties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogConfig:
						Error for property. key: 'logging.global.ansi.mode' from PROPERTIES_STRING[logging.global.ansi.mode], 'sometimes' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.AnsiMode. Valid values: 'auto', 'on', 'off', 'true', 'false'
						Error for property. key: 'logging.global.appender.lock' from PROPERTIES_STRING[logging.global.appender.lock], 'sync' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.AppenderLock. Valid values: 'auto', 'reentrant', 'synchronized'
						Error for property. key: 'logging.global.threadLocal' from PROPERTIES_STRING[logging.global.threadLocal], 'never' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.ThreadLocalMode. Valid values: 'allow', 'forbid'
						Error for property. key: 'logging.global.optimize' from PROPERTIES_STRING[logging.global.optimize], 'yes' is not a valid value for java.lang.Boolean. Valid values: 'true', 'false'""",
				e.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "AUTO, 'ttll color=default'", "ON, 'ttll color=force'", "OFF, 'ttll color=off'" })
	void ansiModeSetsTheDefaultTextColor(AnsiMode mode, String description) {
		var config = LogConfig.builder().ansiMode(mode).build();
		var encoder = config.encoderRegistry()
			.encoderForOutputType(LogOutput.OutputType.CONSOLE_OUT)
			.provide("test", config);
		String report = LogReporter.Reportable.toString(encoder, "");
		assertTrue(report.contains(description), () -> report);
	}

}
