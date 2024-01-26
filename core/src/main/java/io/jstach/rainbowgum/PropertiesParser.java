package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
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
	 * @throws IOException on read failue
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
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public java.util.Enumeration keys() {
				return Collections.enumeration(map.keySet());
			}

			@Override
			@SuppressWarnings({ "unchecked", "rawtypes" })
			public java.util.Set entrySet() {
				return map.entrySet();
			}

			@Override
			public Object get(Object key) {
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

	enum PropertiesFormat {

		PROPERTIES, XML

	}

	static Map<String, String> readProperties(InputStream s) throws IOException {
		return readProperties(s, PropertiesFormat.PROPERTIES);
	}

	private static Map<String, String> readProperties(InputStream s, PropertiesFormat format) throws IOException {
		final Map<String, String> ordered = new LinkedHashMap<>();

		try {
			switch (format) {
				case PROPERTIES: {
					LineNumberReader lr = new LineNumberReader(new InputStreamReader(s, StandardCharsets.UTF_8));
					Properties bp = prepareProperties((k, v) -> {
						if (ordered.containsKey(k)) {
							throw new DuplicateKeyException(k, lr.getLineNumber());
						}
						ordered.put(k, v);
					});
					bp.load(lr);
					break;
				}
				case XML: {
					AtomicInteger i = new AtomicInteger();
					Properties bp = prepareProperties((k, v) -> {
						i.incrementAndGet();
						if (ordered.containsKey(k)) {
							throw new DuplicateKeyException(k, i.get());
						}
						ordered.put(k, v);
					});
					bp.loadFromXML(s);
					break;
				}
			}
			return ordered;
		}
		catch (DuplicateKeyException e) {
			throw new IOException("Duplicate key detected. key: '" + e.key + "' line: " + e.lineNumber, e);
		}
	}

	private static Properties prepareProperties(BiConsumer<String, String> consumer) throws IOException {

		// Hack to use properties class to load but our map for preserved order
		@SuppressWarnings("serial")
		@NonNullByDefault({})
		Properties bp = new Properties() {
			@Override
			public Object put(@Nullable Object key, @Nullable Object value) {
				if (key != null && value != null) {
					consumer.accept((String) key, (String) value);
				}
				return null;
			}
		};
		return bp;
	}

	static class DuplicateKeyException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		final String key;

		final int lineNumber;

		public DuplicateKeyException(String key, int lineNumber) {
			super("Duplicate key detected. key: '" + key + "' line: " + lineNumber);
			this.key = key;
			this.lineNumber = lineNumber;
		}

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

	static void encode(final StringBuilder buf, final CharSequence content, final @Nullable Charset charset,
			final BitSet safechars, final boolean blankAsPlus) {
		final CharBuffer cb = CharBuffer.wrap(content);
		final ByteBuffer bb = (charset != null ? charset : StandardCharsets.UTF_8).encode(cb);
		while (bb.hasRemaining()) {
			final int b = bb.get() & 0xff;
			if (safechars.get(b)) {
				buf.append((char) b);
			}
			else if (blankAsPlus && b == ' ') {
				buf.append("+");
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

	static void encode(final StringBuilder buf, final CharSequence content, final Charset charset,
			final boolean blankAsPlus) {
		encode(buf, content, charset, UNRESERVED, blankAsPlus);
	}

	public static void encode(final StringBuilder buf, final CharSequence content, final Charset charset) {
		encode(buf, content, charset, UNRESERVED, false);
	}

	public static String encode(final CharSequence content, final Charset charset) {

		final StringBuilder buf = new StringBuilder();
		encode(buf, content, charset, UNRESERVED, false);
		return buf.toString();
	}

	static String decode(final CharSequence content, final @Nullable Charset charset, final boolean plusAsBlank) {
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
			else if (plusAsBlank && c == '+') {
				bb.put((byte) ' ');
			}
			else {
				bb.put((byte) c);
			}
		}
		bb.flip();
		return (charset != null ? charset : StandardCharsets.UTF_8).decode(bb).toString();
	}

	public static String decode(final CharSequence content, final Charset charset) {
		return decode(content, charset, false);
	}

}
