package io.jstach.rainbowgum.pattern.internal;

interface ParserEscaper {

	void escape(String additionalEscapeChars, StringBuilder buf, char next, int pointer);

}

class RestrictedEscaper implements ParserEscaper {

	public void escape(String escapeChars, StringBuilder buf, char next, int pointer) {
		if (escapeChars.indexOf(next) >= 0) {
			buf.append(next);
		}
		else {
			// restitute the escape char (because it was consumed
			// before this method was called).
			buf.append("\\");
			// restitute the next character
			buf.append(next);
		}
	}

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
		else
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

	String formatEscapeCharsForListing(String escapeChars) {
		StringBuilder commaSeperatedEscapeChars = new StringBuilder();
		for (int i = 0; i < escapeChars.length(); i++) {
			commaSeperatedEscapeChars.append(", \\").append(escapeChars.charAt(i));
		}
		return commaSeperatedEscapeChars.toString();
	}

	// s might be path such as c:\\toto\\file.log
	// as of version 1.3.0-beta1 this method is no longer used
	public static String basicEscape(String s) {
		char c;
		int len = s.length();
		StringBuilder sbuf = new StringBuilder(len);

		int i = 0;
		while (i < len) {
			c = s.charAt(i++);
			if (c == '\\' && i < len) {
				c = s.charAt(i++);
				if (c == 'n') {
					c = '\n';
				}
				else if (c == 'r') {
					c = '\r';
				}
				else if (c == 't') {
					c = '\t';
				}
				else if (c == 'f') {
					c = '\f';
				}
				else if (c == '\b') {
					c = '\b';
				}
				else if (c == '\"') {
					c = '\"';
				}
				else if (c == '\'') {
					c = '\'';
				}
				else if (c == '\\') {
					c = '\\';
				}
				/////
			}
			sbuf.append(c);
		} // while
		return sbuf.toString();
	}

}