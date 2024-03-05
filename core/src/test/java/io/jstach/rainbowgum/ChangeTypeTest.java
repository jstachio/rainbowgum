package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

	enum _Test {

		TRUE("true", EnumSet.allOf(ChangeType.class)), CALLER_INFO("caller_info", EnumSet.of(ChangeType.CALLER_INFO));

		final String input;

		final Set<ChangeType> expected;

		private _Test(String input, Set<ChangeType> expected) {
			this.input = input;
			this.expected = expected;
		}

	}

}
