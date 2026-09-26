package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogReporter.Section;

/**
 * Every combination of {@link Section} on/off, matching
 * {@link AppenderAsModeFlagPermutationTest}'s own power-set-of-an-enum-flags style: a
 * baseline safety net that each section is included or excluded independently of the
 * others, and that the empty set genuinely falls back to the documented default
 * ({@link Section#VERSION}, {@link Section#COMPONENTS}) rather than silently printing
 * nothing.
 */
class LogReporterSectionPermutationTest {

	static Stream<Arguments> permutations() {
		List<Arguments> args = new ArrayList<>();
		for (var sections : powerSet(Section.values())) {
			args.add(Arguments.of(sections));
		}
		return args.stream();
	}

	private static List<Set<Section>> powerSet(Section[] values) {
		List<Set<Section>> result = new ArrayList<>();
		int n = values.length;
		for (int mask = 0; mask < (1 << n); mask++) {
			var set = EnumSet.noneOf(Section.class);
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0) {
					set.add(values[i]);
				}
			}
			result.add(set);
		}
		return result;
	}

	@ParameterizedTest
	@MethodSource("permutations")
	void testPermutation(Set<Section> sections) {
		var reporter = LogReporter.builder().sections(sections).maxAlerts(3).build();
		try (var gum = RainbowGum.builder().build()) {
			gum.config().metrics().errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, 1);
			gum.config().alerts().error(LogReporterSectionPermutationTest.class, "boom", new IllegalStateException());

			String actual = reporter.report(gum);

			// Section.builder()'s own documented contract: an empty set falls back to
			// the default, it does not mean "print nothing".
			Set<Section> effective = sections.isEmpty() ? EnumSet.of(Section.VERSION, Section.COMPONENTS) : sections;

			assertEquals(effective.contains(Section.VERSION), actual.startsWith("Rainbow Gum "),
					() -> "VERSION mismatch for " + sections + ": " + actual);
			assertEquals(effective.contains(Section.COMPONENTS), actual.contains("Global Properties:"),
					() -> "COMPONENTS mismatch for " + sections + ": " + actual);
			assertEquals(effective.contains(Section.METRICS), actual.contains("Metrics:"),
					() -> "METRICS mismatch for " + sections + ": " + actual);
			assertEquals(effective.contains(Section.ALERTS), actual.contains("Alerts ("),
					() -> "ALERTS mismatch for " + sections + ": " + actual);
		}
	}

}
