package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DataSizeTest {

	private static final long KB = 1024L;

	private static final long MB = KB * 1024;

	private static final long GB = MB * 1024;

	private static final long TB = GB * 1024;

	enum Valid {

		// Logback FileSize forms, which must keep their Logback values.
		ZERO("0", 0), BYTES("1048576", 1048576), KB_LOWER("10kb", 10 * KB), KB_UPPER("10KB", 10 * KB),
		MB_SPACED("10 MB", 10 * MB), MB_MIXED_CASE("10Mb", 10 * MB), MB_PLURAL("10mbs", 10 * MB),
		GB_PLURAL_UPPER("2GBS", 2 * GB), BYTES_PLURAL("10s", 10),
		// Additions beyond Logback.
		B_UNIT("10B", 10), K_SINGLE("512k", 512 * KB), M_SINGLE("10M", 10 * MB), G_SINGLE("2g", 2 * GB),
		T_SINGLE("1t", TB), TB_UNIT("1TB", TB), KIB("4KiB", 4 * KB), MIB("10mib", 10 * MB),
		GIB_SPACED("1.5 GiB", GB + GB / 2), TIB("2TiB", 2 * TB), DECIMAL_MB("1.5mb", MB + MB / 2),
		DECIMAL_ROUNDS_DOWN("1.3mb", 1363148), FRACTIONAL_BYTE("0.5b", 0), TRIMMED(" 10mb ", 10 * MB),
		// Boundaries.
		MAX_LONG("9223372036854775807", Long.MAX_VALUE), MAX_GB("8589934591gb", 8589934591L * GB),
		MAX_TB("8388607tb", 8388607L * TB);

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

	private static final String FORMAT_HINT = "Expected a number with an optional unit of b, k/kb/kib, m/mb/mib, g/gb/gib, or t/tb/tib (case insensitive, powers of 1024). Examples: '10MB', '512k', '1.5 GiB', '1048576'.";

	enum Invalid {

		EMPTY("", "Invalid data size: ''. " + FORMAT_HINT), BLANK("  ", "Invalid data size: '  '. " + FORMAT_HINT),
		NO_NUMBER("mb", "Invalid data size: 'mb'. " + FORMAT_HINT),
		NEGATIVE("-1", "Invalid data size: '-1'. " + FORMAT_HINT),
		COMMA_DECIMAL("1,5mb", "Invalid data size: '1,5mb'. " + FORMAT_HINT),
		TRAILING_DOT("1.mb", "Invalid data size: '1.mb'. " + FORMAT_HINT),
		LEADING_DOT(".5mb", "Invalid data size: '.5mb'. " + FORMAT_HINT),
		SPACE_IN_UNIT("10 m b", "Invalid data size: '10 m b'. " + FORMAT_HINT),
		UNKNOWN_UNIT("10x", "Invalid data size: '10x'. Unknown unit 'x'. " + FORMAT_HINT),
		SPELLED_OUT_UNIT("10 megabytes", "Invalid data size: '10 megabytes'. Unknown unit 'megabytes'. " + FORMAT_HINT),
		PLURAL_ONLY_FOR_LOGBACK_UNITS("10mibs", "Invalid data size: '10mibs'. Unknown unit 'mibs'. " + FORMAT_HINT),
		LONG_OVERFLOW("9223372036854775808",
				"Data size is too large: '9223372036854775808'. Maximum is 9223372036854775807 bytes."),
		GB_OVERFLOW("8589934592gb", "Data size is too large: '8589934592gb'. Maximum is 9223372036854775807 bytes."),
		TB_OVERFLOW("8388608tb", "Data size is too large: '8388608tb'. Maximum is 9223372036854775807 bytes.");

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
		GB(3L * 1024 * 1024 * 1024, "3GB"), MB_NOT_GB(1536L * 1024 * 1024, "1536MB"),
		TB(5L * 1024 * 1024 * 1024 * 1024, "5TB");

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
