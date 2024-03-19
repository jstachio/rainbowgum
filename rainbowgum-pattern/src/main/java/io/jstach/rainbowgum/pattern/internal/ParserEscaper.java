package io.jstach.rainbowgum.pattern.internal;

interface ParserEscaper {

	void escape(String additionalEscapeChars, StringBuilder buf, char next, int pointer);

}

class AsIsEscaper implements ParserEscaper {

	/**
	 * Do not perform any character escaping.
	 * <p>
	 * Note that this method assumes that it is called after the escape character has been
	 * consumed.
	 */
	public void escape(String escapeChars, StringBuilder buf, char next, int pointer) {
		// restitute the escape char (because it was consumed
		// before this method was called).
		buf.append("\\");
		// restitute the next character
		buf.append(next);
	}

}

class RegularEscaper implements ParserEscaper {

	public void escape(String escapeChars, StringBuilder buf, char next, int pointer) {
		if (escapeChars.indexOf(next) >= 0) {
			buf.append(next);
		}
		else {
			switch (next) {
				case '_':
					// the \_ sequence is swallowed
					break;
				case '\\':
					buf.append(next);
					break;
				case 't':
					buf.append('\t');
					break;
				case 'r':
					buf.append('\r');
					break;
				case 'n':
					buf.append('\n');
					break;
				default:
					String commaSeperatedEscapeChars = formatEscapeCharsForListing(escapeChars);
					throw new IllegalArgumentException("Illegal char '" + next + " at column " + pointer
							+ ". Only \\\\, \\_" + commaSeperatedEscapeChars
							+ ", \\t, \\n, \\r combinations are allowed as escape characters.");
			}
		}
	}

	static String formatEscapeCharsForListing(String escapeChars) {
		StringBuilder commaSeperatedEscapeChars = new StringBuilder();
		for (int i = 0; i < escapeChars.length(); i++) {
			commaSeperatedEscapeChars.append(", \\").append(escapeChars.charAt(i));
		}
		return commaSeperatedEscapeChars.toString();
	}

}