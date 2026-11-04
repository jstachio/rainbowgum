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
	 * line that starts with a tab is never an event: an empty first positional column is
	 * written as the reserved escape <code>\e</code>. A literal <code>\e</code> value is
	 * escaped as <code>\\e</code>, so the two remain distinct. Removing the leading tab
	 * from continuation lines gives back the stack trace, with an exception's message
	 * escaped like any other value so it stays on its line.
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
