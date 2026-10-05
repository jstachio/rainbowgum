package io.jstach.rainbowgum.pattern.format;

import java.lang.System.Logger.Level;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/*
 * Wraps a formatter so every line it writes for an event starts with the event's
 * severity prefix.
 */
record LevelPrefixFormatter(LogFormatter formatter) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String prefix = journaldPrefix(event.level());
		int start = output.length();
		formatter.format(output, event);
		int end = output.length();
		if (end == start) {
			return;
		}
		/*
		 * Insert after each line break that is followed by more text, working backwards
		 * so earlier indexes stay valid. A trailing line separator gets no prefix.
		 */
		for (int i = end - 2; i >= start; i--) {
			if (output.charAt(i) == '\n') {
				output.insert(i + 1, prefix);
			}
		}
		output.insert(start, prefix);
	}

	/*
	 * Syslog severities. OFF is not a real event level; it maps to info rather than 0
	 * (emergency), which journald broadcasts to every logged in terminal.
	 */
	static String journaldPrefix(Level level) {
		return switch (level) {
			case ERROR -> "<3>";
			case WARNING -> "<4>";
			case INFO, OFF -> "<6>";
			case DEBUG, TRACE, ALL -> "<7>";
		};
	}

}
