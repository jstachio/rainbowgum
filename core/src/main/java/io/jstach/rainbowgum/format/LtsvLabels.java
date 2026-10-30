package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * Which fields the core <code>ltsv</code> encoder writes with a label.
 */
@CaseChanging
public enum LtsvLabels {

	/**
	 * Every field is <code>label:value</code>, as LTSV defines. The default.
	 */
	@EnumAlias("true")
	ALL,
	/**
	 * The fields every event has (time, level, logger, the thread fields, and the
	 * message) are tab separated values in that order without labels, and key values,
	 * <code>error</code>, and <code>stacktrace</code> keep theirs. The output is no
	 * longer LTSV, but its leading columns can be read by position.
	 */
	@EnumAlias({ "false", "keyValues" })
	KEY_VALUES;

	static LtsvLabels parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "true" -> ALL;
			case "false", "keyvalues" -> KEY_VALUES;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(LtsvLabels.class, value, "true", "false", "keyValues");
		};
	}

}
