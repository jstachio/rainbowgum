package io.jstach.rainbowgum.file;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A non negative amount of data in bytes, such as a maximum file size.
 * <p>
 * {@link #parse(String)} accepts a number followed by an optional unit, with optional
 * whitespace around and between them. Every unit is a power of 1024 and units are case
 * insensitive:
 * <ul>
 * <li>none or {@code b}: bytes</li>
 * <li>{@code k}, {@code kb}, {@code kib}: 1024 bytes</li>
 * <li>{@code m}, {@code mb}, {@code mib}: 1024<sup>2</sup> bytes</li>
 * <li>{@code g}, {@code gb}, {@code gib}: 1024<sup>3</sup> bytes</li>
 * <li>{@code t}, {@code tb}, {@code tib}: 1024<sup>4</sup> bytes</li>
 * </ul>
 * The number may have a decimal part using {@code .} (never {@code ,}), for example
 * {@code 1.5GB}; a result that is not a whole number of bytes is rounded down. Every
 * value Logback's {@code FileSize} accepts, including its optional trailing {@code s} as
 * in {@code 10MBs}, parses to the same number of bytes. Unlike some other formats,
 * {@code MB} never means 1000<sup>2</sup>.
 */
public final class DataSize implements Comparable<DataSize> {

	private static final Pattern PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-Z]*)");

	private static final String FORMAT_HINT = "Expected a number with an optional unit of b, k/kb/kib, m/mb/mib, g/gb/gib, or t/tb/tib (case insensitive, powers of 1024). Examples: '10MB', '512k', '1.5 GiB', '1048576'.";

	private static final long KB = 1024L;

	private static final long MB = KB * 1024L;

	private static final long GB = MB * 1024L;

	private static final long TB = GB * 1024L;

	/**
	 * Zero bytes.
	 */
	public static final DataSize ZERO = new DataSize(0);

	private final long bytes;

	private DataSize(long bytes) {
		this.bytes = bytes;
	}

	/**
	 * Creates a data size from a number of bytes.
	 * @param bytes number of bytes, must not be negative.
	 * @return data size.
	 * @throws IllegalArgumentException if bytes is negative.
	 */
	public static DataSize ofBytes(long bytes) {
		if (bytes < 0) {
			throw new IllegalArgumentException("Data size must not be negative. bytes: " + bytes);
		}
		return bytes == 0 ? ZERO : new DataSize(bytes);
	}

	/**
	 * Creates a data size from a number of kilobytes (1024 bytes each).
	 * @param kilobytes number of kilobytes, must not be negative.
	 * @return data size.
	 * @throws IllegalArgumentException if negative or too large.
	 */
	public static DataSize ofKilobytes(long kilobytes) {
		return ofBytes(multiply(kilobytes, KB, kilobytes + "kb"));
	}

	/**
	 * Creates a data size from a number of megabytes (1024 * 1024 bytes each).
	 * @param megabytes number of megabytes, must not be negative.
	 * @return data size.
	 * @throws IllegalArgumentException if negative or too large.
	 */
	public static DataSize ofMegabytes(long megabytes) {
		return ofBytes(multiply(megabytes, MB, megabytes + "mb"));
	}

	/**
	 * Creates a data size from a number of gigabytes (1024 * 1024 * 1024 bytes each).
	 * @param gigabytes number of gigabytes, must not be negative.
	 * @return data size.
	 * @throws IllegalArgumentException if negative or too large.
	 */
	public static DataSize ofGigabytes(long gigabytes) {
		return ofBytes(multiply(gigabytes, GB, gigabytes + "gb"));
	}

	/**
	 * Parses a data size such as {@code 10MB}, {@code 512 kb}, {@code 1.5g}, or
	 * {@code 1048576}. See the class documentation for the format.
	 * @param value text to parse.
	 * @return data size.
	 * @throws IllegalArgumentException if the text is not a valid data size.
	 */
	public static DataSize parse(String value) {
		var m = PATTERN.matcher(value.strip());
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid data size: '" + value + "'. " + FORMAT_HINT);
		}
		String number = Objects.requireNonNull(m.group(1));
		String unit = Objects.requireNonNullElse(m.group(2), "");
		long multiplier = switch (unit.toLowerCase(Locale.ROOT)) {
			case "", "b", "s" -> 1L;
			case "k", "kb", "kib", "kbs" -> KB;
			case "m", "mb", "mib", "mbs" -> MB;
			case "g", "gb", "gib", "gbs" -> GB;
			case "t", "tb", "tib", "tbs" -> TB;
			default -> throw new IllegalArgumentException(
					"Invalid data size: '" + value + "'. Unknown unit '" + unit + "'. " + FORMAT_HINT);
		};
		BigInteger bytes = new BigDecimal(number).multiply(BigDecimal.valueOf(multiplier)).toBigInteger();
		if (bytes.bitLength() >= Long.SIZE) {
			throw tooLarge(value);
		}
		return ofBytes(bytes.longValue());
	}

	private static long multiply(long number, long multiplier, String description) {
		if (number < 0) {
			throw new IllegalArgumentException("Data size must not be negative: '" + description + "'");
		}
		try {
			return Math.multiplyExact(number, multiplier);
		}
		catch (ArithmeticException e) {
			throw tooLarge(description);
		}
	}

	private static IllegalArgumentException tooLarge(String value) {
		return new IllegalArgumentException(
				"Data size is too large: '" + value + "'. Maximum is " + Long.MAX_VALUE + " bytes.");
	}

	/**
	 * The size in bytes.
	 * @return bytes, never negative.
	 */
	public long toBytes() {
		return bytes;
	}

	@Override
	public int compareTo(DataSize o) {
		return Long.compare(bytes, o.bytes);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		return obj instanceof DataSize other && other.bytes == bytes;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(bytes);
	}

	/**
	 * A string that {@link #parse(String)} accepts, using the largest unit that divides
	 * the size exactly, for example {@code 10MB} or {@code 1500}.
	 * @return parseable text.
	 */
	@Override
	public String toString() {
		if (bytes != 0) {
			if (bytes % TB == 0) {
				return (bytes / TB) + "TB";
			}
			if (bytes % GB == 0) {
				return (bytes / GB) + "GB";
			}
			if (bytes % MB == 0) {
				return (bytes / MB) + "MB";
			}
			if (bytes % KB == 0) {
				return (bytes / KB) + "KB";
			}
		}
		return Long.toString(bytes);
	}

}
