package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;

class ChangeTypeTest {

	@ParameterizedTest
	@EnumSource(value = _Test.class)
	void test(_Test test) {
		List<String> s = LogProperties.parseList(test.input);
		var actual = ChangeType.parse(s);
		assertEquals(test.expected, actual);
	}

	/*
	 * "caller" used to be a valid ChangeType token before CALLER moved to its own
	 * logging.caller.<name> property (ChangePublisher.CallerType) - now an unrecognized
	 * token, which AbstractChangePublisher.allowedChanges() already catches and alerts on
	 * rather than crashing, a reasonable pre-1.0 migration path for anyone still setting
	 * logging.change.<name>=caller.
	 */
	@Test
	void testParseRejectsTheOldCallerToken() {
		assertThrows(IllegalArgumentException.class, () -> ChangeType.parse(List.of("caller")));
	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum _Test {

		TRUE("true", EnumSet.allOf(ChangeType.class)), LEVEL("level", EnumSet.of(ChangeType.LEVEL));

		final String input;

		private final Set<ChangeType> expected;

		private _Test(String input, Set<ChangeType> expected) {
			this.input = input;
			this.expected = expected;
		}

	}

}
