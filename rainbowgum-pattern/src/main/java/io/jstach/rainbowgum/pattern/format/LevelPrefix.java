package io.jstach.rainbowgum.pattern.format;

import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * A severity prefix the pattern encoder puts at the start of every line it writes.
 *
 * @see PatternEncoderBuilder#levelPrefix(LevelPrefix)
 */
@CaseChanging
public enum LevelPrefix {

	/**
	 * No prefix. The default.
	 */
	NONE,
	/**
	 * The syslog severity of the event as <code>&lt;N&gt;</code> on every line, which
	 * systemd's journal reads as the line's priority when the process's standard out or
	 * standard err is connected to it (see <code>sd-daemon(3)</code>). Error is 3,
	 * warning 4, info 6, and debug and trace 7. Every line of an event, including stack
	 * trace lines, gets the prefix because the journal records each line as its own
	 * entry.
	 */
	JOURNALD;

	static LevelPrefix parse(String value) {
		return LogProperty.enumValue(LevelPrefix.class, value);
	}

}
