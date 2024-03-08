package io.jstach.rainbowgum;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Formattes a LogEvent message.
 */
public sealed interface LogMessageFormatter {

	/**
	 * Formats and appends the results.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 */
	void format(StringBuilder builder, String message, @Nullable Object arg1);

	/**
	 * Formats and appends the results.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 * @param arg2 to use for replacement.
	 */
	void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2);

	/**
	 * Formats and appends the result.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param args array of args.
	 */
	void formatArray(StringBuilder builder, String message, @Nullable Object[] args);

	/**
	 * Builtin message formatters.
	 */
	@CaseChanging
	public enum StandardMessageFormatter implements LogMessageFormatter {

		/**
		 * SLF4J format style where "<code>{}</code>" is replaced with the parameters. The
		 * implementation largely comes from the SLF4J but optimized for StringBuilder.
		 */
		SLF4J() {
			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1) {
				SLF4JMessageFormatter.format(builder, message, arg1);
			}

			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2) {
				SLF4JMessageFormatter.format(builder, message, arg1, arg2);
			}

			@Override
			public void formatArray(StringBuilder builder, String message, @Nullable Object[] args) {
				SLF4JMessageFormatter.format(builder, message, args);
			}
		}

	}

}

class SLF4JMessageFormatter {

	static final char DELIM_START = '{';
	static final char DELIM_STOP = '}';
	static final String DELIM_STR = "{}";

	private static final char ESCAPE_CHAR = '\\';

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1) {
		format(sbuf, messagePattern, arg1, null, null, 1);
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1, final @Nullable Object arg2) {
		format(sbuf, messagePattern, arg1, arg2, null, 2);
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object @Nullable [] args) {
		if (args == null || args.length == 0) {
			format(sbuf, messagePattern, null, null, null, 0);
			return;
		}
		else if (args.length == 1) {
			format(sbuf, messagePattern, args[0], null, null, 1);
			return;
		}
		else if (args.length == 2) {
			format(sbuf, messagePattern, args[0], args[1], null, 2);
			return;
		}
		else {
			format(sbuf, messagePattern, null, null, args, args.length);
		}
	}

	private static void format(final StringBuilder sbuf, //
			final @Nullable String messagePattern, //
			final @Nullable Object arg1, //
			final @Nullable Object arg2, //
			final @Nullable Object @Nullable [] args, //
			final int argCount) {

		if (messagePattern == null) {
			return;
		}

		if (argCount == 0) {
			sbuf.append(messagePattern);
			return;
		}

		int i = 0;
		int j;
		// use string builder for better multicore performance

		int L;
		for (L = 0; L < argCount; L++) {

			j = messagePattern.indexOf(DELIM_STR, i);

			if (j == -1) {
				// no more variables
				if (i == 0) { // this is a simple string
					sbuf.append(messagePattern);
					return;
				}
				else { // add the tail string which contains no variables and return
						// the result.
					sbuf.append(messagePattern, i, messagePattern.length());
					return;
				}
			}
			else {
				if (isEscapedDelimeter(messagePattern, j)) {
					if (!isDoubleEscaped(messagePattern, j)) {
						L--; // DELIM_START was escaped, thus should not be incremented
						sbuf.append(messagePattern, i, j - 1);
						sbuf.append(DELIM_START);
						i = j + 1;
					}
					else {
						// The escape character preceding the delimiter start is
						// itself escaped: "abc x:\\{}"
						// we have to consume one backward slash
						sbuf.append(messagePattern, i, j - 1);
						Object arg = resolveArg(L, arg1, arg2, args, argCount);
						deeplyAppendParameter(sbuf, arg, null);
						i = j + 2;
					}
				}
				else {
					// normal case
					sbuf.append(messagePattern, i, j);
					Object arg = resolveArg(L, arg1, arg2, args, argCount);
					deeplyAppendParameter(sbuf, arg, null);
					i = j + 2;
				}
			}
		}
		// append the characters following the last {} pair.
		sbuf.append(messagePattern, i, messagePattern.length());
		return;
	}

	private static @Nullable Object resolveArg(int i, @Nullable Object arg1, @Nullable Object arg2,
			@Nullable Object @Nullable [] args, int argCount) {
		if (i >= argCount || argCount == 0) {
			throw new IndexOutOfBoundsException(i);
		}
		if (argCount > 2) {
			if (args == null) {
				throw new IndexOutOfBoundsException(i);
			}
			return args[i];
		}
		else if (i == 0) {
			return arg1;
		}
		else if (i == 1) {
			return arg2;
		}
		throw new IndexOutOfBoundsException(i);
	}

	final static boolean isEscapedDelimeter(String messagePattern, int delimeterStartIndex) {

		if (delimeterStartIndex == 0) {
			return false;
		}
		char potentialEscape = messagePattern.charAt(delimeterStartIndex - 1);
		if (potentialEscape == ESCAPE_CHAR) {
			return true;
		}
		else {
			return false;
		}
	}

	final static boolean isDoubleEscaped(String messagePattern, int delimeterStartIndex) {
		if (delimeterStartIndex >= 2 && messagePattern.charAt(delimeterStartIndex - 2) == ESCAPE_CHAR) {
			return true;
		}
		else {
			return false;
		}
	}

	/*
	 * The below is adapted code from SLF4J
	 */
	// special treatment of array values was suggested by 'lizongbo'
	private static void deeplyAppendParameter(StringBuilder sbuf, @Nullable Object o,
			@Nullable Map<@Nullable Object[], @Nullable Object> seenMap) {
		if (o == null) {
			sbuf.append("null");
			return;
		}
		if (!o.getClass().isArray()) {
			safeObjectAppend(sbuf, o);
		}
		else {
			// check for primitive array types because they
			// unfortunately cannot be cast to Object[]
			if (o instanceof boolean[]) {
				booleanArrayAppend(sbuf, (boolean[]) o);
			}
			else if (o instanceof byte[]) {
				byteArrayAppend(sbuf, (byte[]) o);
			}
			else if (o instanceof char[]) {
				charArrayAppend(sbuf, (char[]) o);
			}
			else if (o instanceof short[]) {
				shortArrayAppend(sbuf, (short[]) o);
			}
			else if (o instanceof int[]) {
				intArrayAppend(sbuf, (int[]) o);
			}
			else if (o instanceof long[]) {
				longArrayAppend(sbuf, (long[]) o);
			}
			else if (o instanceof float[]) {
				floatArrayAppend(sbuf, (float[]) o);
			}
			else if (o instanceof double[]) {
				doubleArrayAppend(sbuf, (double[]) o);
			}
			else {
				if (seenMap == null) {
					seenMap = new HashMap<>();
				}
				objectArrayAppend(sbuf, (@Nullable Object[]) o, seenMap);
			}
		}
	}

	private static void safeObjectAppend(StringBuilder sbuf, Object o) {
		try {
			String oAsString = o.toString();
			sbuf.append(oAsString);
		}
		catch (Throwable t) {
			// Util.report("SLF4J: Failed toString() invocation on an object of type [" +
			// o.getClass().getName() + "]", t);
			sbuf.append("[FAILED toString()]");
		}

	}

	private static void objectArrayAppend(StringBuilder sbuf, @Nullable Object[] a,
			Map<@Nullable Object[], @Nullable Object> seenMap) {
		sbuf.append('[');
		if (!seenMap.containsKey(a)) {
			seenMap.put(a, null);
			final int len = a.length;
			for (int i = 0; i < len; i++) {
				deeplyAppendParameter(sbuf, a[i], seenMap);
				if (i != len - 1)
					sbuf.append(", ");
			}
			// allow repeats in siblings
			seenMap.remove(a);
		}
		else {
			sbuf.append("...");
		}
		sbuf.append(']');
	}

	private static void booleanArrayAppend(StringBuilder sbuf, boolean[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void byteArrayAppend(StringBuilder sbuf, byte[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void charArrayAppend(StringBuilder sbuf, char[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void shortArrayAppend(StringBuilder sbuf, short[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void intArrayAppend(StringBuilder sbuf, int[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void longArrayAppend(StringBuilder sbuf, long[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void floatArrayAppend(StringBuilder sbuf, float[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

	private static void doubleArrayAppend(StringBuilder sbuf, double[] a) {
		sbuf.append('[');
		final int len = a.length;
		for (int i = 0; i < len; i++) {
			sbuf.append(a[i]);
			if (i != len - 1)
				sbuf.append(", ");
		}
		sbuf.append(']');
	}

}