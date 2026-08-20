package io.jstach.rainbowgum.format;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Detects whether the current process is attached to an ANSI capable terminal so that
 * formatters (e.g. the pattern module's color keywords) can decide whether to emit ANSI
 * escape codes in the first place instead of emitting and later stripping them.
 * <p>
 * This uses only {@link System#console()} and well known environment variables and thus
 * requires no native code or third party terminal library. On JDK versions prior to 22
 * {@link System#console()} returns <code>null</code> whenever either standard in or
 * standard out is redirected which is a safe (if slightly conservative) signal to disable
 * ANSI output.
 */
public final class AnsiSupport {

	private AnsiSupport() {
	}

	/**
	 * Determines if ANSI escape codes are likely supported by the current process's
	 * standard output.
	 * @return true if ANSI output should be emitted.
	 */
	@SuppressWarnings("SystemConsoleNull")
	public static boolean isAnsiSupported() {
		if (System.console() == null) {
			return false;
		}
		return isAnsiSupported(System.getenv("NO_COLOR"), System.getenv("TERM"));
	}

	static boolean isAnsiSupported(@Nullable String noColor, @Nullable String term) {
		if (noColor != null) {
			/*
			 * https://no-color.org/ - presence of the variable disables color regardless
			 * of value.
			 */
			return false;
		}
		if ("dumb".equals(term)) {
			return false;
		}
		return true;
	}

}
