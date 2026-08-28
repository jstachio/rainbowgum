package io.jstach.rainbowgum;

import java.text.MessageFormat;
import java.util.IdentityHashMap;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Formats a LogEvent message.
 *
 * @apiNote This is sealed at the moment and maybe unsealed in the future so exhaustive
 * pattern matching should not be done.
 */
@CaseChanging
public sealed interface LogMessageFormatter {

	/**
	 * Formats and appends the results.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 */
	void format(StringBuilder builder, String message, @Nullable Object arg1);

	/**
	 * Formats and appends the results, stopping early once {@code maxSize} characters
	 * have been appended to {@code builder} (counted from {@code builder}'s length when
	 * this call started).
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 * @param maxSize maximum number of characters this call may append to
	 * {@code builder}, or a non-positive value for unbounded (equivalent to
	 * {@link #format(StringBuilder, String, Object)}).
	 * @apiNote The default implementation is a safe fallback that formats unbounded first
	 * and truncates after - it does not avoid the cost of formatting an oversized
	 * message. {@link StandardMessageFormatter#SLF4J} overrides this to stop appending as
	 * soon as the cap is reached instead, though even there a single argument's own
	 * {@code toString()} is not itself bounded - see {@code SLF4JMessageFormatter}'s own
	 * javadoc for that limitation.
	 */
	default void format(StringBuilder builder, String message, @Nullable Object arg1, int maxSize) {
		int start = builder.length();
		format(builder, message, arg1);
		capAfter(builder, start, maxSize);
	}

	/**
	 * Formats and appends the results.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 * @param arg2 to use for replacement.
	 */
	void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2);

	/**
	 * Formats and appends the results, stopping early once {@code maxSize} characters
	 * have been appended to {@code builder} (counted from {@code builder}'s length when
	 * this call started).
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param arg1 to use for replacement.
	 * @param arg2 to use for replacement.
	 * @param maxSize maximum number of characters this call may append to
	 * {@code builder}, or a non-positive value for unbounded.
	 * @apiNote see {@link #format(StringBuilder, String, Object, int)} for the fallback
	 * vs early-exit distinction between formatters.
	 */
	default void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2,
			int maxSize) {
		int start = builder.length();
		format(builder, message, arg1, arg2);
		capAfter(builder, start, maxSize);
	}

	/**
	 * Formats and appends the result.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param args array of args.
	 */
	default void formatArray(StringBuilder builder, String message, @Nullable Object[] args) {
		formatArray(builder, message, args, args.length);
	}

	/**
	 * Formats and appends the result.
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param args array of args.
	 * @param length of args array which will be used even if args is larger.
	 */
	void formatArray(StringBuilder builder, String message, @Nullable Object[] args, int length);

	/**
	 * Formats and appends the result, stopping early once {@code maxSize} characters have
	 * been appended to {@code builder} (counted from {@code builder}'s length when this
	 * call started).
	 * @param builder output.
	 * @param message message usually with formatting delimiters for replacement.
	 * @param args array of args.
	 * @param length of args array which will be used even if args is larger.
	 * @param maxSize maximum number of characters this call may append to
	 * {@code builder}, or a non-positive value for unbounded.
	 * @apiNote see {@link #format(StringBuilder, String, Object, int)} for the fallback
	 * vs early-exit distinction between formatters.
	 */
	default void formatArray(StringBuilder builder, String message, @Nullable Object[] args, int length, int maxSize) {
		int start = builder.length();
		formatArray(builder, message, args, length);
		capAfter(builder, start, maxSize);
	}

	/**
	 * Truncates {@code builder} back to {@code start + maxSize} if it grew past that
	 * while formatting, unless {@code maxSize} is non-positive (unbounded).
	 */
	private static void capAfter(StringBuilder builder, int start, int maxSize) {
		if (maxSize > 0 && builder.length() - start > maxSize) {
			builder.setLength(start + maxSize);
		}
	}

	/**
	 * Built-in message formatters.
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
			public void format(StringBuilder builder, String message, @Nullable Object arg1, int maxSize) {
				SLF4JMessageFormatter.format(builder, message, arg1, maxSize);
			}

			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2) {
				SLF4JMessageFormatter.format(builder, message, arg1, arg2);
			}

			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2,
					int maxSize) {
				SLF4JMessageFormatter.format(builder, message, arg1, arg2, maxSize);
			}

			@Override
			public void formatArray(StringBuilder builder, String message, @Nullable Object[] args, int length) {
				SLF4JMessageFormatter.format(builder, message, args, length);
			}

			@Override
			public void formatArray(StringBuilder builder, String message, @Nullable Object[] args, int length,
					int maxSize) {
				SLF4JMessageFormatter.format(builder, message, args, length, maxSize);
			}
		},
		/**
		 * java.util.logging MessageFormat style.
		 */
		JUL() {

			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1) {
				try {
					String result = MessageFormat.format(message, arg1);
					builder.append(result);
				}
				catch (RuntimeException e) {
					formatBadPattern(builder, message, e);
				}
			}

			private static void formatBadPattern(StringBuilder builder, String message, RuntimeException e) {
				builder.append(message)
					.append(" ")
					.append("[MessageFormat failed: ")
					.append(e.getMessage())
					.append("]");
			}

			@Override
			public void format(StringBuilder builder, String message, @Nullable Object arg1, @Nullable Object arg2) {
				try {
					String result = MessageFormat.format(message, arg1, arg2);
					builder.append(result);
				}
				catch (RuntimeException e) {
					formatBadPattern(builder, message, e);
				}

			}

			@Override
			public void formatArray(StringBuilder builder, String message, @Nullable Object[] args, int length) {
				try {
					String result = MessageFormat.format(message, args);
					builder.append(result);
				}
				catch (RuntimeException e) {
					formatBadPattern(builder, message, e);
				}
			}

		}

	}

}

final class SLF4JMessageFormatter {

	private static final char DELIM_START = '{';

	// private static final char DELIM_STOP = '}';
	private static final String DELIM_STR = "{}";

	private static final char ESCAPE_CHAR = '\\';

	/**
	 * Sentinel passed as the internal absolute {@code limit} to mean "unbounded" - not a
	 * valid {@link StringBuilder#length()} value to target, so safe to distinguish from
	 * every real (always {@code >= 0}) limit.
	 */
	private static final int UNBOUNDED = -1;

	private SLF4JMessageFormatter() {
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1) {
		format(sbuf, messagePattern, arg1, null, null, 1, UNBOUNDED);
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1, int maxSize) {
		format(sbuf, messagePattern, arg1, null, null, 1, absoluteLimit(sbuf, maxSize));
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1, final @Nullable Object arg2) {
		format(sbuf, messagePattern, arg1, arg2, null, 2, UNBOUNDED);
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object arg1, final @Nullable Object arg2, int maxSize) {
		format(sbuf, messagePattern, arg1, arg2, null, 2, absoluteLimit(sbuf, maxSize));
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object @Nullable [] args, int length) {
		formatArray(sbuf, messagePattern, args, length, UNBOUNDED);
	}

	public static void format(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object @Nullable [] args, int length, int maxSize) {
		formatArray(sbuf, messagePattern, args, length, absoluteLimit(sbuf, maxSize));
	}

	private static void formatArray(final StringBuilder sbuf, final @Nullable String messagePattern,
			final @Nullable Object @Nullable [] args, int length, int limit) {
		if (args == null || length == 0) {
			format(sbuf, messagePattern, null, null, null, 0, limit);
		}
		else if (length == 1) {
			format(sbuf, messagePattern, args[0], null, null, 1, limit);
		}
		else if (length == 2) {
			format(sbuf, messagePattern, args[0], args[1], null, 2, limit);
		}
		else {
			format(sbuf, messagePattern, null, null, args, length, limit);
		}
	}

	/**
	 * Converts a caller-supplied {@code maxSize} (max characters this call may append,
	 * counted from {@code sbuf}'s length at the start of the call) into an absolute
	 * target {@link StringBuilder#length()} to stop at, or {@link #UNBOUNDED}.
	 */
	private static int absoluteLimit(StringBuilder sbuf, int maxSize) {
		return maxSize <= 0 ? UNBOUNDED : sbuf.length() + maxSize;
	}

	/**
	 * If {@code sbuf} has reached or passed {@code limit}, truncates it to exactly
	 * {@code limit} and reports that the caller should stop. A single call to
	 * {@link #deeplyAppendParameter(StringBuilder, Object, IdentityHashMap)} can still
	 * push {@code sbuf} arbitrarily far past {@code limit} in one step (an argument's own
	 * {@code toString()} is not itself bounded) - this only guarantees the check runs
	 * (and the loop stops) between segments/arguments, not that any single append is
	 * capped mid-flight.
	 * @return {@code true} if {@code sbuf} was capped and the caller should stop.
	 */
	private static boolean capped(StringBuilder sbuf, int limit) {
		if (limit != UNBOUNDED && sbuf.length() >= limit) {
			sbuf.setLength(limit);
			return true;
		}
		return false;
	}

	private static void format(final StringBuilder sbuf, //
			final @Nullable String messagePattern, //
			final @Nullable Object arg1, //
			final @Nullable Object arg2, //
			final @Nullable Object @Nullable [] args, //
			final int argCount, //
			final int limit) {

		if (messagePattern == null) {
			return;
		}

		if (argCount == 0) {
			sbuf.append(messagePattern);
			capped(sbuf, limit);
			return;
		}

		int i = 0;
		int j;
		int L;
		for (L = 0; L < argCount; L++) {

			j = messagePattern.indexOf(DELIM_STR, i);

			if (j == -1) {
				// no more variables
				if (i == 0) { // this is a simple string
					sbuf.append(messagePattern);
					capped(sbuf, limit);
					return;
				}
				else { // add the tail string which contains no variables and return
						// the result.
					sbuf.append(messagePattern, i, messagePattern.length());
					capped(sbuf, limit);
					return;
				}
			}
			else {
				if (isEscapedDelimeter(messagePattern, j)) {
					if (!isDoubleEscaped(messagePattern, j)) {
						L--; // DELIM_START was escaped, thus should not be incremented
						sbuf.append(messagePattern, i, j - 1);
						sbuf.append(DELIM_START);
						if (capped(sbuf, limit)) {
							return;
						}
						i = j + 1;
					}
					else {
						// The escape character preceding the delimiter start is
						// itself escaped: "abc x:\\{}"
						// we have to consume one backward slash
						sbuf.append(messagePattern, i, j - 1);
						Object arg = resolveArg(L, arg1, arg2, args, argCount);
						deeplyAppendParameter(sbuf, arg, null);
						if (capped(sbuf, limit)) {
							return;
						}
						i = j + 2;
					}
				}
				else {
					// normal case
					sbuf.append(messagePattern, i, j);
					Object arg = resolveArg(L, arg1, arg2, args, argCount);
					deeplyAppendParameter(sbuf, arg, null);
					if (capped(sbuf, limit)) {
						return;
					}
					i = j + 2;
				}
			}
		}
		// append the characters following the last {} pair.
		sbuf.append(messagePattern, i, messagePattern.length());
		capped(sbuf, limit);
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
			@Nullable IdentityHashMap<@Nullable Object[], @Nullable Object> seenMap) {
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
			if (o instanceof boolean[] array) {
				booleanArrayAppend(sbuf, array);
			}
			else if (o instanceof byte[] array) {
				byteArrayAppend(sbuf, array);
			}
			else if (o instanceof char[] array) {
				charArrayAppend(sbuf, array);
			}
			else if (o instanceof short[] array) {
				shortArrayAppend(sbuf, array);
			}
			else if (o instanceof int[] array) {
				intArrayAppend(sbuf, array);
			}
			else if (o instanceof long[] array) {
				longArrayAppend(sbuf, array);
			}
			else if (o instanceof float[] array) {
				floatArrayAppend(sbuf, array);
			}
			else if (o instanceof double[] array) {
				doubleArrayAppend(sbuf, array);
			}
			else {
				if (seenMap == null) {
					seenMap = new IdentityHashMap<>();
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
			IdentityHashMap<@Nullable Object[], @Nullable Object> seenMap) {
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