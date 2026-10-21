package io.jstach.rainbowgum.test.nested;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogConfig.Globals.AnsiMode;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

/*
 * The same configuration in each format, which must resolve the same way.
 */
abstract class AbstractNestedLogPropertiesTest {

	static final String TESTCONTAINERS_LOGGER = "tc.floci/floci-gcp@sha256:405c128b...";

	/*
	 * Root warn, com.example and tc.floci... quoted, org.dotted.Unquoted not quoted.
	 */
	abstract LogProperties levels();

	/*
	 * logging.level as a value, with no other levels.
	 */
	abstract LogProperties rootLevelOnly();

	/*
	 * Change mode on, every logger may change level except com.example, and com.example
	 * records caller info.
	 */
	abstract LogProperties changes();

	/*
	 * Group jdbc of org.postgresql, com.example.odd_name and literal root at debug, ANSI
	 * off.
	 */
	abstract LogProperties groups();

	/*
	 * logging.global.ansi.mod misspelled, and levels, which are never reported.
	 */
	abstract LogProperties typo();

	abstract String format();

	static LogConfig config(LogProperties properties) {
		return LogConfig.builder().properties(properties).build();
	}

	@Test
	void levelsUseQuotedLoggerNames() {
		var levels = config(levels()).levelResolver();
		assertEquals(Level.WARNING, levels.resolveLevel("other"));
		assertEquals(Level.WARNING, levels.resolveLevel("com"));
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example"));
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example.Foo"));
		assertEquals(Level.ERROR, levels.resolveLevel("com.example.sub.Bar"));
		assertEquals(Level.TRACE, levels.resolveLevel(TESTCONTAINERS_LOGGER));
		assertEquals(Level.INFO, levels.resolveLevel("org.dotted.Unquoted.Baz"));
		assertEquals(Level.WARNING, levels.resolveLevel("org.dotted"));
	}

	@Test
	void rootLevelCanBeAValue() {
		var levels = config(rootLevelOnly()).levelResolver();
		assertEquals(Level.ERROR, levels.resolveLevel("com.example"));
		assertEquals(Level.ERROR, levels.resolveLevel(""));
	}

	@Test
	void changeAndCallerUseQuotedLoggerNames() {
		var changes = config(changes()).changePublisher();
		assertEquals(EnumSet.of(ChangeType.LEVEL), changes.allowedChanges("other"));
		assertEquals(Set.of(), changes.allowedChanges("com.example.Foo"));
		assertEquals(CallerType.NONE, changes.callerType("other"));
		assertEquals(CallerType.BASIC, changes.callerType("com.example.Foo"));
	}

	@Test
	void groupsAndOtherKeys() {
		var config = config(groups());
		assertEquals(AnsiMode.OFF, config.globals().ansiMode());
		var levels = config.levelResolver();
		assertEquals(Level.DEBUG, levels.resolveLevel("org.postgresql.Driver"));
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example.odd_name"));
		assertEquals(Level.DEBUG, levels.resolveLevel("root"));
		assertEquals(Level.DEBUG, levels.resolveLevel("root.child"));
		assertEquals(Level.INFO, levels.defaultLevel());
		assertEquals(Level.INFO, levels.resolveLevel("com.example"));
	}

	@Test
	void misspelledKeyIsReportedButLoggerKeysAreNot() {
		var config = LogConfig.builder()
			.properties(typo())
			.alertsFail(LogAlerts.FailLevel.WARNING)
			.alertsUnusedKey(LogAlerts.UnusedKeyLevel.WARNING)
			.build();
		var gum = RainbowGum.builder(config).build();
		var e = assertThrows(IllegalStateException.class, gum::start);
		assertEquals("""
				1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
				[WARNING] Property key 'logging.global.ansi.mod' from FORMAT[logging.global.ansi.mod] \
				was set but not read during startup. Did you mean 'logging.global.ansi.mode'?""".replace("FORMAT",
				format()), e.getMessage());
	}

}
