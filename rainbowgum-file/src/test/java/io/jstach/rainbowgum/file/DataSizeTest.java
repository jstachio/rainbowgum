package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DataSizeTest {

	/*
	 * Matches Logback's FileSize: ([0-9]+)\s*(|kb|mb|gb)s? case insensitive, whole match.
	 */
	enum Valid {

		ZERO("0", 0), BYTES("1048576", 1048576), KB("10kb", 10L * 1024), KB_UPPER("10KB", 10L * 1024),
		MB_SPACED("10 MB", 10L * 1024 * 1024), MB_MIXED_CASE("10Mb", 10L * 1024 * 1024),
		MB_PLURAL("10mbs", 10L * 1024 * 1024), GB("2gb", 2L * 1024 * 1024 * 1024),
		GB_PLURAL_UPPER("2GBS", 2L * 1024 * 1024 * 1024), BYTES_PLURAL("10s", 10),
		MAX_LONG("9223372036854775807", Long.MAX_VALUE), MAX_GB("8589934591gb", 8589934591L * 1024 * 1024 * 1024);

		final String input;

		final long bytes;

		Valid(String input, long bytes) {
			this.input = input;
			this.bytes = bytes;
		}

	}

	@ParameterizedTest
	@EnumSource(Valid.class)
	void parseValid(Valid test) {
		assertEquals(test.bytes, DataSize.parse(test.input).toBytes());
	}

	private static final String FORMAT_HINT = "Expected a whole number with an optional unit of kb, mb, or gb (case insensitive, powers of 1024). Examples: '10mb', '512 KB', '1048576'.";

	enum Invalid {

		EMPTY("", "Invalid data size: ''. " + FORMAT_HINT), NO_NUMBER("mb", "Invalid data size: 'mb'. " + FORMAT_HINT),
		NEGATIVE("-1", "Invalid data size: '-1'. " + FORMAT_HINT),
		SINGLE_LETTER_UNIT("10M", "Invalid data size: '10M'. " + FORMAT_HINT),
		DECIMAL("1.5mb", "Invalid data size: '1.5mb'. " + FORMAT_HINT),
		TERABYTES("1tb", "Invalid data size: '1tb'. " + FORMAT_HINT),
		LEADING_SPACE(" 10mb", "Invalid data size: ' 10mb'. " + FORMAT_HINT),
		TRAILING_SPACE("10mb ", "Invalid data size: '10mb '. " + FORMAT_HINT),
		LONG_OVERFLOW("9223372036854775808",
				"Data size is too large: '9223372036854775808'. Maximum is 9223372036854775807 bytes."),
		MULTIPLY_OVERFLOW("8589934592gb",
				"Data size is too large: '8589934592gb'. Maximum is 9223372036854775807 bytes.");

		final String input;

		final String message;

		Invalid(String input, String message) {
			this.input = input;
			this.message = message;
		}

	}

	@ParameterizedTest
	@EnumSource(Invalid.class)
	void parseInvalid(Invalid test) {
		var e = assertThrows(IllegalArgumentException.class, () -> DataSize.parse(test.input));
		assertEquals(test.message, e.getMessage());
	}

	enum Format {

		ZERO(0, "0"), ODD_BYTES(1536, "1536"), KB(2048, "2KB"), MB(10L * 1024 * 1024, "10MB"),
		GB(3L * 1024 * 1024 * 1024, "3GB"), MB_NOT_GB(1536L * 1024 * 1024, "1536MB");

		final long bytes;

		final String text;

		Format(long bytes, String text) {
			this.bytes = bytes;
			this.text = text;
		}

	}

	@ParameterizedTest
	@EnumSource(Format.class)
	void toStringRoundTrips(Format test) {
		var size = DataSize.ofBytes(test.bytes);
		assertEquals(test.text, size.toString());
		assertEquals(size, DataSize.parse(size.toString()));
	}

	@org.junit.jupiter.api.Test
	void negativeBytesRejected() {
		var e = assertThrows(IllegalArgumentException.class, () -> DataSize.ofBytes(-1));
		assertEquals("Data size must not be negative. bytes: -1", e.getMessage());
		var e2 = assertThrows(IllegalArgumentException.class, () -> DataSize.ofMegabytes(-1));
		assertEquals("Data size must not be negative: '-1mb'", e2.getMessage());
	}

	@org.junit.jupiter.api.Test
	void unitFactories() {
		assertEquals(DataSize.parse("3kb"), DataSize.ofKilobytes(3));
		assertEquals(DataSize.parse("3mb"), DataSize.ofMegabytes(3));
		assertEquals(DataSize.parse("3gb"), DataSize.ofGigabytes(3));
		var e = assertThrows(IllegalArgumentException.class, () -> DataSize.ofGigabytes(Long.MAX_VALUE));
		assertEquals("Data size is too large: '9223372036854775807gb'. Maximum is 9223372036854775807 bytes.",
				e.getMessage());
	}

}
