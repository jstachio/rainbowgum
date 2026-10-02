package io.jstach.rainbowgum.file;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A non negative amount of data in bytes, such as a maximum file size.
 * <p>
 * {@link #parse(String)} accepts the same format as Logback's {@code FileSize}: a whole
 * number followed by an optional unit of {@code kb}, {@code mb}, or {@code gb} (case
 * insensitive, optional whitespace between number and unit, optional trailing {@code s}).
 * Units are powers of 1024, so {@code 10MB} is {@code 10485760} bytes. A number without a
 * unit is bytes.
 */
public final class DataSize implements Comparable<DataSize> {

	private static final Pattern PATTERN = Pattern.compile("([0-9]+)\\s*(|kb|mb|gb)s?", Pattern.CASE_INSENSITIVE);

	private static final long KB = 1024L;

	private static final long MB = KB * 1024L;

	private static final long GB = MB * 1024L;

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
	 * Parses a data size such as {@code 10MB}, {@code 512 kb}, or {@code 1048576}.
	 * @param value text to parse.
	 * @return data size.
	 * @throws IllegalArgumentException if the text is not a valid data size.
	 */
	public static DataSize parse(String value) {
		var m = PATTERN.matcher(value);
		if (!m.matches()) {
			throw new IllegalArgumentException("Invalid data size: '" + value
					+ "'. Expected a whole number with an optional unit of kb, mb, or gb (case insensitive, powers of 1024). Examples: '10mb', '512 KB', '1048576'.");
		}
		long number;
		try {
			number = Long.parseLong(Objects.requireNonNull(m.group(1)));
		}
		catch (NumberFormatException e) {
			throw tooLarge(value);
		}
		long multiplier = switch (Objects.requireNonNullElse(m.group(2), "").toLowerCase(Locale.ROOT)) {
			case "kb" -> KB;
			case "mb" -> MB;
			case "gb" -> GB;
			default -> 1L;
		};
		return ofBytes(multiply(number, multiplier, value));
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
