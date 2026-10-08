package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * Where an event's key values go in a structured event format such as the core
 * <code>json</code> encoder.
 */
@CaseChanging
public enum KeyValuesPlacement {

	/**
	 * Alongside the event's own fields, the way logfmt and most log aggregators expect
	 * them. A key value named like one of the event's own fields is placed in the nested
	 * object instead, so nothing is dropped, renamed, or written twice. The default.
	 */
	@EnumAlias({ "true", "default" })
	MERGED,
	/**
	 * In a nested object, written even when there are no key values so every event has
	 * the same shape.
	 */
	NESTED,
	/**
	 * Key values are left out.
	 */
	@EnumAlias("false")
	NONE;

	static KeyValuesPlacement parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "true", "default" -> MERGED;
			case "false" -> NONE;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(KeyValuesPlacement.class, value, "true", "false", "default");
		};
	}

}
