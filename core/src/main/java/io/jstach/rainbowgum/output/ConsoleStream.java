package io.jstach.rainbowgum.output;

import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * How a standard out or standard err output finds the stream it writes to.
 *
 * @see StdOutOutputBuilder
 * @see StdErrOutputBuilder
 */
@CaseChanging
public enum ConsoleStream {

	/**
	 * Uses whatever {@link System#out} or {@link System#err} is when the output is
	 * created. Rebinding it later with {@link System#setOut} or {@link System#setErr} is
	 * not seen. This is the default, and it is the safe choice when something later
	 * redirects standard out into logging, which would otherwise loop.
	 */
	CACHED,
	/**
	 * Reads {@link System#out} or {@link System#err} on every write, so output follows
	 * the stream if it is rebound, for example by a test capturing standard out. Do not
	 * use it if standard out is redirected into logging: the output would then log to
	 * itself.
	 */
	FOLLOW;

	static ConsoleStream parse(String value) {
		return LogProperty.enumValue(ConsoleStream.class, value);
	}

}
