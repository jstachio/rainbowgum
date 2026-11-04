package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * How the core <code>ltsv</code> encoder writes a throwable's stack trace.
 */
@CaseChanging
public enum LtsvStackTrace {

	/**
	 * The default. The stack trace is the <code>stacktrace</code> value, escaped like any
	 * other, so every event is one line.
	 */
	@EnumAlias({ "default", "false" })
	INLINE,
	/**
	 * <code>stacktrace:</code> ends the event's line and each line of the stack trace
	 * follows on a line of its own, after a tab. This is no longer strict LTSV, but a
	 * line that starts with a tab is never an event, so the stack trace is still easy to
	 * tell apart, and removing that tab gives back the stack trace as Java prints it. An
	 * exception's message is escaped like any other value, so it stays on its line.
	 */
	@EnumAlias("true")
	LINES;

	static LtsvStackTrace parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "default", "false" -> INLINE;
			case "true" -> LINES;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(LtsvStackTrace.class, value, "default", "true", "false");
		};
	}

}
