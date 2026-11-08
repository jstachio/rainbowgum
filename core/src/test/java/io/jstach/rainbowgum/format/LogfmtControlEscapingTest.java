package io.jstach.rainbowgum.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * logfmt escapes the same controls as LTSV: C0, DEL, C1, the Unicode line and paragraph
 * separators, and bidirectional embedding, override and isolate characters, written as
 * backslash-u escapes in quoted values and percent-encoded as UTF-8 in keys.
 */
class LogfmtControlEscapingTest {

	// Built from code points: line separators and directionality characters in source can
	// conceal code.
	private static final char LS = (char) 0x2028;

	private static final char PS = (char) 0x2029;

	private static final char RLO = (char) 0x202e;

	private static final char LRI = (char) 0x2066;

	@Test
	void valueWithC1SeparatorOrBidiControlIsQuotedAndEscaped() {
		var output = new StringBuilder("msg=");
		LogfmtKeyValuesFormatter.appendValue(output, "a\u0085b" + LS + "c" + RLO + "d" + LRI + "e");
		assertEquals("msg=\"a\\u0085b\\u2028c\\u202ed\\u2066e\"", output.toString());
	}

	@Test
	void keyWithC1SeparatorOrBidiControlIsPercentEncodedAsUtf8() {
		var output = new StringBuilder();
		LogfmtKeyValuesFormatter.appendKey(output, "k\u0085" + LS + RLO);
		assertEquals("k%C2%85%E2%80%A8%E2%80%AE", output.toString());
	}

	@Test
	void allUtf16CodeUnitsMatchTheReference() {
		for (int c = Character.MIN_VALUE; c <= Character.MAX_VALUE; c++) {
			String value = "a" + (char) c + "b";
			var output = new StringBuilder();
			LogfmtKeyValuesFormatter.appendValue(output, value);
			assertEquals(referenceValue(value), output.toString(),
					() -> "value U+" + Integer.toHexString(value.charAt(1)));
			if (!Character.isSurrogate((char) c)) {
				var key = new StringBuilder();
				LogfmtKeyValuesFormatter.appendKey(key, value);
				assertEquals(referenceKey(value), key.toString(),
						() -> "key U+" + Integer.toHexString(value.charAt(1)));
			}
		}
	}

	// Uses the JDK's classification and UTF-8 encoder rather than the production checks.
	private static boolean escaped(char c) {
		return Character.isISOControl(c) || c == LS || c == PS || switch (Character.getDirectionality(c)) {
			case Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING, Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
					Character.DIRECTIONALITY_POP_DIRECTIONAL_FORMAT, Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE,
					Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE, Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE,
					Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE, Character.DIRECTIONALITY_FIRST_STRONG_ISOLATE,
					Character.DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE ->
				true;
			default -> false;
		};
	}

	private static String referenceValue(String value) {
		boolean quote = value.isEmpty();
		var body = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> body.append("\\\"");
				case '\\' -> body.append("\\\\");
				case '\n' -> body.append("\\n");
				case '\r' -> body.append("\\r");
				case '\t' -> body.append("\\t");
				default -> {
					if (escaped(c)) {
						String hex = Integer.toHexString(c);
						body.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
					}
					else {
						body.append(c);
					}
				}
			}
			if (c <= ' ' || c == '=' || c == '"' || c == '\\' || escaped(c)) {
				quote = true;
			}
		}
		return quote ? "\"" + body + "\"" : value;
	}

	private static String referenceKey(String key) {
		var output = new StringBuilder();
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (c <= ' ' || c == '=' || c == '"' || c == '\\' || c == '%' || escaped(c)) {
				for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
					output.append('%').append(String.format("%02X", b & 0xFF));
				}
			}
			else {
				output.append(c);
			}
		}
		return output.toString();
	}

}
