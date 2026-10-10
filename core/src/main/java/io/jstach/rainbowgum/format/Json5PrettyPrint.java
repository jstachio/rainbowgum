package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * How the core <code>json5</code> encoder lays out each event's object. Only whitespace
 * outside values is added, so the output parses the same. Ignored for plain JSON.
 */
@CaseChanging
public enum Json5PrettyPrint {

	/**
	 * No added whitespace: <code>level:"INFO",logger:...</code>.
	 */
	@EnumAlias("false")
	OFF,
	/**
	 * Pads after the level's closing quote so the fields after it line up:
	 * <code>level:"INFO" ,logger:...</code>. The default.
	 */
	@EnumAlias("default")
	LEVEL_PADDING,
	/**
	 * Level padding and a space after every comma:
	 * <code>level:"INFO" , logger:"com.example.App", thread:...</code>.
	 */
	SPACING,
	/**
	 * Each of the event's fields on its own line, indented one space like the
	 * <code>rainbowgum-json</code> encoders' pretty print, with nested key values on one
	 * line. The level is not padded.
	 */
	@EnumAlias("true")
	FULL;

	static Json5PrettyPrint parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "default" -> LEVEL_PADDING;
			case "true" -> FULL;
			case "false" -> OFF;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(Json5PrettyPrint.class, value, "true", "false", "default");
		};
	}

}
