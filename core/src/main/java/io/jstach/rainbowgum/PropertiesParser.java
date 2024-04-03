package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Writes and parses properties.
 */
public final class PropertiesParser {

	private PropertiesParser() {
	}

	/**
	 * Writes properties.
	 * @param map properties map.
	 * @return properties as a string.
	 */
	public static String writeProperties(Map<String, String> map) {
		StringBuilder sb = new StringBuilder();
		try {
			writeProperties(map, sb);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return sb.toString();

	}

	/**
	 * Read properties.
	 * @param input from.
	 * @return properties as a map.
	 */
	public static Map<String, String> readProperties(String input) {
		Map<String, String> m = new LinkedHashMap<>();
		StringReader sr = new StringReader(input);
		try {
			readProperties(sr, m::put);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return m;
	}

	/**
	 * Read properties.
	 * @param reader from
	 * @param consumer to
	 * @throws IOException on read failure
	 */
	static void readProperties(Reader reader, BiConsumer<String, String> consumer) throws IOException {
		Properties bp = prepareProperties(consumer);
		bp.load(reader);

	}

	@SuppressWarnings({ "serial" })
	static void writeProperties(Map<String, String> map, Appendable sb) throws IOException {
		StringWriter sw = new StringWriter();
		new Properties() {
			@Override
			@SuppressWarnings({ "unchecked", "rawtypes", "UnsynchronizedOverridesSynchronized", "null" })
			public java.util.Enumeration keys() {
				return Collections.enumeration(map.keySet());
			}

			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public java.util.Set entrySet() {
				return map.entrySet();
			}

			@SuppressWarnings({ "nullness", "UnsynchronizedOverridesSynchronized" }) // checker
																						// bug
			@Override
			public @Nullable Object get(Object key) {
				return map.get(key);
			}
		}.store(sw, null);
		LineNumberReader lr = new LineNumberReader(new StringReader(sw.toString()));

		String line;
		while ((line = lr.readLine()) != null) {
			if (!line.startsWith("#")) {
				sb.append(line).append(System.lineSeparator());
			}
		}
	}

	private static Properties prepareProperties(BiConsumer<String, String> consumer) throws IOException {

		// Hack to use properties class to load but our map for preserved order
		@SuppressWarnings({ "serial", "nullness" })
		Properties bp = new Properties() {
			@Override
			@SuppressWarnings({ "nullness", "keyfor", "UnsynchronizedOverridesSynchronized" }) // checker
																								// bug
			public @Nullable Object put(Object key, Object value) {
				Objects.requireNonNull(key);
				Objects.requireNonNull(value);
				consumer.accept((String) key, (String) value);
				return null;
			}
		};
		return bp;
	}

}

final class PercentCodec {

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

	public static void encode(final StringBuilder buf, final CharSequence content, final Charset charset) {
		encode(buf, content, charset, UNRESERVED);
	}

	public static String encode(final CharSequence content, final Charset charset) {
		final StringBuilder buf = new StringBuilder();
		encode(buf, content, charset);
		return buf.toString();
	}

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
