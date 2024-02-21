package io.jstach.rainbowgum.pattern;

import io.jstach.rainbowgum.LogFormatter;

/**
 * FormattingInfo instances contain the information obtained when parsing formatting
 * modifiers in conversion modifiers.
 *
 */
public record FormatInfo(int min, int max, boolean leftPad, boolean leftTruncate) {

	/**
	 * Create padding info.
	 * @param min if min is less than zero and not Integer.MIN_VALUE left pad will be
	 * disabled.
	 * @param max if max is less than zero left truncate will be disabled.
	 * @return
	 */
	public static FormatInfo of(int min, int max) {
		boolean leftPad = true;
		boolean leftTruncate = true;
		if (min != Integer.MIN_VALUE && min < 0) {
			min = -min;
			leftPad = false;
		}
		if (max < 0) {
			max = -max;
			leftTruncate = false;
		}
		return new FormatInfo(min, max, leftPad, leftTruncate);
	}

	/**
	 * This method is used to parse a string such as "5", ".7", "5.7" or "-5.7" into a
	 * FormatInfo.
	 * @param str A String to convert into a FormatInfo object
	 * @return A newly created and appropriately initialized FormatInfo object.
	 * @throws IllegalArgumentException
	 */
	public static FormatInfo valueOf(String str) throws IllegalArgumentException {
		if (str == null) {
			throw new NullPointerException("Argument cannot be null");
		}

		int min = Integer.MIN_VALUE;
		int max = Integer.MAX_VALUE;
		boolean leftPad = true;
		boolean leftTruncate = true;

		int indexOfDot = str.indexOf('.');
		String minPart = null;
		String maxPart = null;
		if (indexOfDot != -1) {
			minPart = str.substring(0, indexOfDot);
			if (indexOfDot + 1 == str.length()) {
				throw new IllegalArgumentException("Formatting string [" + str + "] should not end with '.'");
			}
			else {
				maxPart = str.substring(indexOfDot + 1);
			}
		}
		else {
			minPart = str;
		}

		if (minPart != null && minPart.length() > 0) {
			int _min = Integer.parseInt(minPart);
			if (_min >= 0) {
				min = _min;
			}
			else {
				min = -_min;
				leftPad = false;
			}
		}

		if (maxPart != null && maxPart.length() > 0) {
			int _max = Integer.parseInt(maxPart);
			if (_max >= 0) {
				max = _max;
			}
			else {
				max = -_max;
				leftTruncate = false;
			}
		}

		return new FormatInfo(min, max, leftPad, leftTruncate);
	}

	public void format(StringBuilder buf, CharSequence s) {
		int len = s.length();
		if (len == 0) {
			return;
		}
		if (len > max) {
			if (leftTruncate) {
				buf.append(s, len - max, len);
			}
			else {
				buf.append(s, 0, max);
			}
		}
		else if (len < min) {
			if (leftPad) {
				LogFormatter.padLeft(buf, s, min);
			}
			else {
				LogFormatter.padRight(buf, s, min);
			}
		}
		else {
			buf.append(s);
		}
	}

	// @Override
	// final public void write(StringBuilder buf, E event) {
	// String s = convert(event);
	//
	// if (formattingInfo == null) {
	// buf.append(s);
	// return;
	// }
	//
	// int min = formattingInfo.getMin();
	// int max = formattingInfo.getMax();
	//
	// if (s == null) {
	// if (0 < min)
	// SpacePadder.spacePad(buf, min);
	// return;
	// }
	//
	// int len = s.length();
	//
	// if (len > max) {
	// if (formattingInfo.isLeftTruncate()) {
	// buf.append(s.substring(len - max));
	// } else {
	// buf.append(s.substring(0, max));
	// }
	// } else if (len < min) {
	// if (formattingInfo.isLeftPad()) {
	// SpacePadder.leftPad(buf, s, min);
	// } else {
	// SpacePadder.rightPad(buf, s, min);
	// }
	// } else {
	// buf.append(s);
	// }
	// }

}
