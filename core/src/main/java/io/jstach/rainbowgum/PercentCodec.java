package io.jstach.rainbowgum;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;

/**
 * Percent encoding (<a href="https://www.rfc-editor.org/rfc/rfc3986#section-2.1">RFC
 * 3986</a>), the encoding Rainbow Gum uses for URI query properties and for key values
 * written with
 * {@link io.jstach.rainbowgum.format.KeyValuesFormatterBuilder.Format#PERCENT}, so that
 * key values read from a log decode back to exactly what was logged.
 */
public final class PercentCodec {

	private PercentCodec() {
	}

	static final BitSet GEN_DELIMS = new BitSet(256);
	static final BitSet SUB_DELIMS = new BitSet(256);
	static final BitSet UNRESERVED = new BitSet(256);
	static final BitSet URIC = new BitSet(256);

	static {
		GEN_DELIMS.set(':');
		GEN_DELIMS.set('/');
		GEN_DELIMS.set('?');
		GEN_DELIMS.set('#');
		GEN_DELIMS.set('[');
		GEN_DELIMS.set(']');
		GEN_DELIMS.set('@');

		SUB_DELIMS.set('!');
		SUB_DELIMS.set('$');
		SUB_DELIMS.set('&');
		SUB_DELIMS.set('\'');
		SUB_DELIMS.set('(');
		SUB_DELIMS.set(')');
		SUB_DELIMS.set('*');
		SUB_DELIMS.set('+');
		SUB_DELIMS.set(',');
		SUB_DELIMS.set(';');
		SUB_DELIMS.set('=');

		for (int i = 'a'; i <= 'z'; i++) {
			UNRESERVED.set(i);
		}
		for (int i = 'A'; i <= 'Z'; i++) {
			UNRESERVED.set(i);
		}
		// numeric characters
		for (int i = '0'; i <= '9'; i++) {
			UNRESERVED.set(i);
		}
		UNRESERVED.set('-');
		UNRESERVED.set('.');
		UNRESERVED.set('_');
		UNRESERVED.set('~');
		URIC.or(SUB_DELIMS);
		URIC.or(UNRESERVED);
	}

	private static final int RADIX = 16;

	static void encode(final StringBuilder buf, final CharSequence content, Charset charset, final BitSet safechars) {
		final CharBuffer cb = CharBuffer.wrap(content);
		final ByteBuffer bb = charset.encode(cb);
		while (bb.hasRemaining()) {
			final int b = bb.get() & 0xff;
			if (safechars.get(b)) {
				buf.append((char) b);
			}
			else {
				buf.append("%");
				final char hex1 = Character.toUpperCase(Character.forDigit((b >> 4) & 0xF, RADIX));
				final char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, RADIX));
				buf.append(hex1);
				buf.append(hex2);
			}
		}
	}

	/**
	 * Appends the content percent encoded: every byte of its encoding in the charset
	 * other than the unreserved characters <code>A-Z a-z 0-9 - . _ ~</code> is written as
	 * <code>%XX</code> with upper case hex digits.
	 * @param buf output.
	 * @param content text to encode.
	 * @param charset charset of the bytes to encode, usually UTF-8.
	 */
	public static void encode(final StringBuilder buf, final CharSequence content, final Charset charset) {
		encode(buf, content, charset, UNRESERVED);
	}

	/**
	 * Percent encodes the content, see
	 * {@link #encode(StringBuilder, CharSequence, Charset)}.
	 * @param content text to encode.
	 * @param charset charset of the bytes to encode, usually UTF-8.
	 * @return encoded text.
	 */
	public static String encode(final CharSequence content, final Charset charset) {
		final StringBuilder buf = new StringBuilder();
		encode(buf, content, charset);
		return buf.toString();
	}

	/**
	 * Decodes <code>%XX</code> sequences into bytes and the bytes into text with the
	 * charset. A <code>%</code> not followed by two hex digits is kept as is, and
	 * <code>+</code> is not a space.
	 * @param content percent encoded text.
	 * @param charset charset of the encoded bytes, usually UTF-8.
	 * @return decoded text.
	 */
	public static String decode(final CharSequence content, Charset charset) {
		final ByteBuffer bb = ByteBuffer.allocate(content.length());
		final CharBuffer cb = CharBuffer.wrap(content);
		while (cb.hasRemaining()) {
			final char c = cb.get();
			if (c == '%' && cb.remaining() >= 2) {
				final char uc = cb.get();
				final char lc = cb.get();
				final int u = Character.digit(uc, RADIX);
				final int l = Character.digit(lc, RADIX);
				if (u != -1 && l != -1) {
					bb.put((byte) ((u << 4) + l));
				}
				else {
					bb.put((byte) '%');
					bb.put((byte) uc);
					bb.put((byte) lc);
				}
			}
			else {
				bb.put((byte) c);
			}
		}
		bb.flip();
		return charset.decode(bb).toString();
	}

}
