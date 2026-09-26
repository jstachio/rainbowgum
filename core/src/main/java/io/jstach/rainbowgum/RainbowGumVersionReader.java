package io.jstach.rainbowgum;

import io.jstach.rainbowgum.annotation.RainbowGumVersion;

/**
 * Defensive access to {@link RainbowGumVersion}, whose module
 * ({@code io.jstach.rainbowgum.annotation}) this module only <code>requires
 * static</code> (optional at runtime, see {@code module-info.java}). Nothing enforces
 * that optionality stays safe as new code is added, so anything that wants to print the
 * version (a diagnostic nicety, not something logging itself depends on) should go
 * through here rather than referencing {@link RainbowGumVersion} directly.
 *
 * @apiNote {@link RainbowGumVersion#VERSION} itself is a compile time constant and is
 * inlined by javac wherever it is referenced, so referencing it directly does not
 * actually risk a missing module today - but that is incidental, not a guarantee, and
 * does not extend to {@link RainbowGumVersion#documentBaseUrl()} or any other real method
 * call on that type. See todo.md for the broader plan to make the {@code requires static}
 * dependency itself safe, rather than working around it here.
 */
final class RainbowGumVersionReader {

	private RainbowGumVersionReader() {
	}

	private static final String UNKNOWN_VERSION = "unknown";

	/**
	 * Rainbow Gum's version, or {@value #UNKNOWN_VERSION} if
	 * {@code io.jstach.rainbowgum.annotation} is not present at runtime.
	 * @return version string, never <code>null</code>.
	 */
	static String version() {
		try {
			return RainbowGumVersion.VERSION;
		}
		catch (LinkageError e) {
			return UNKNOWN_VERSION;
		}
	}

}
