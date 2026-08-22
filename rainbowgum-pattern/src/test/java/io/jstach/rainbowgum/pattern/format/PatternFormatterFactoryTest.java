package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/*
 * Direct tests of a few pure-function parsing/lookup helpers in
 * PatternFormatterFactory.java that have many branches which are awkward to reach one at
 * a time solely through compiled pattern strings (see CompilerTest for the
 * keyword-to-formatter integration tests instead).
 */
class PatternFormatterFactoryTest {

	@ParameterizedTest
	@ValueSource(strings = { "yyyy-MM-dd HH:mm:ss,SSS", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "HH:mm:ss.S",
			"HH:mm:ss.SSS", "'literal SSSS text' HH:mm:ss" })
	void millisOrCoarserPrecisionIsTrueForPatternsWithAtMostThreeConsecutiveNonLiteralS(String pattern) {
		assertTrue(StandardKeywordFactory.isMillisOrCoarserPrecision(pattern));
	}

	@ParameterizedTest
	@ValueSource(strings = { "HH:mm:ss.SSSS", "HH:mm:ss.SSSSSS", "HH:mm:ss n", "HH:mm:ss N" })
	void millisOrCoarserPrecisionIsFalseForSubMillisecondOrNanoPatterns(String pattern) {
		assertFalse(StandardKeywordFactory.isMillisOrCoarserPrecision(pattern));
	}

	@Test
	void millisOrCoarserPrecisionIgnoresLiteralQuotedTextIncludingConsecutiveS() {
		// four S's, but inside a quoted literal - should not count as sub-millisecond.
		assertTrue(StandardKeywordFactory.isMillisOrCoarserPrecision("HH:mm:ss'SSSS'"));
	}

	@Test
	void millisOrCoarserPrecisionResetsConsecutiveSCountAcrossLiteralBoundary() {
		// "SS" then a literal then "SS" again - never more than 2 in a row outside the
		// literal, so this should still count as millis-or-coarser.
		assertTrue(StandardKeywordFactory.isMillisOrCoarserPrecision("SS'x'SS"));
	}

	@Test
	void keyAndFallbackSplitsOnColonDashWhenPresent() {
		var kf = StandardKeywordFactory.keyAndFallback("requestId:-none");
		assertEquals("requestId", kf.key());
		assertEquals("none", kf.fallback());
	}

	@Test
	void keyAndFallbackHasNullFallbackWhenColonDashAbsent() {
		var kf = StandardKeywordFactory.keyAndFallback("requestId");
		assertEquals("requestId", kf.key());
		assertNull(kf.fallback());
	}

	@ParameterizedTest
	@ValueSource(strings = { "blue", "cyan", "faint", "green", "magenta", "red", "yellow", "bright_black", "bright_red",
			"bright_green", "bright_yellow", "bright_blue", "bright_magenta", "bright_cyan", "bright_white", "RED",
			"Bright_Red" })
	void clrParseColorAcceptsAllKnownNamesCaseInsensitively(String color) {
		// just confirming none of these throw and each maps to a non-blank ANSI code.
		String code = HighlightCompositeFactory.clrParseColor(color);
		assertTrue(code != null && !code.isBlank(), color);
	}

	@Test
	void clrParseColorRejectsUnknownName() {
		var e = assertThrows(IllegalArgumentException.class,
				() -> HighlightCompositeFactory.clrParseColor("chartreuse"));
		assertEquals("Bad color:chartreuse", e.getMessage());
	}

	@Test
	void clrParseColorBlueIsActuallyBlackForegroundLikeSpringBoot() {
		// Spring Boot's own %clr{blue} really is black (a known quirk of its
		// converter), preserved here on purpose - not a typo.
		assertEquals(ANSIConstants.BLACK_FG, HighlightCompositeFactory.clrParseColor("blue"));
	}

}
