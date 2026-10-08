package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/*
 * The UTF-8 fast path must write exactly what the general Charset based path writes.
 */
class PercentCodecTest {

	private static String general(String content) {
		var sb = new StringBuilder();
		PercentCodec.encode(sb, content, StandardCharsets.UTF_8, PercentCodec.UNRESERVED);
		return sb.toString();
	}

	private static String fast(String content) {
		var sb = new StringBuilder();
		PercentCodec.encodeUtf8(sb, content);
		return sb.toString();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "requestId", "Ada Lovelace", "a=b&c", "-._~", "!*'();:@&=+$,/?#[]", "x\ny\tz\u0000",
			"\u007f\u0080߿ࠀ￿", "café 田 😀", "\uD800", "\uDC00", "a\uD800b", "\uDC00\uD800", "\uD83D", "end\uD83D" })
	void fastPathMatchesTheGeneralPath(String content) {
		assertEquals(general(content), fast(content));
	}

	@Test
	void fastPathMatchesTheGeneralPathForRandomText() {
		var random = new Random(42);
		char[] interesting = { 'a', 'Z', '0', '-', '~', ' ', '%', '&', '=', '\n', 'é', '߿', 'ࠀ', '田', '￿', '\uD83D',
				'\uDE00', '\uDBFF', '\uDFFF' };
		for (int n = 0; n < 20_000; n++) {
			var sb = new StringBuilder();
			int length = random.nextInt(12);
			for (int i = 0; i < length; i++) {
				sb.append(random.nextBoolean() ? interesting[random.nextInt(interesting.length)]
						: (char) random.nextInt(0x10000));
			}
			String content = sb.toString();
			assertEquals(general(content), fast(content), () -> "content: "
					+ content.chars().mapToObj(c -> String.format("\\u%04x", c)).reduce("", String::concat));
		}
	}

	@Test
	void publicEncodeUsesTheFastPathForUtf8() {
		assertEquals("Ada%20Lovelace%E7%94%B0", PercentCodec.encode("Ada Lovelace田", StandardCharsets.UTF_8));
		assertEquals("Ada Lovelace田", PercentCodec.decode("Ada%20Lovelace%E7%94%B0", StandardCharsets.UTF_8));
	}

}
