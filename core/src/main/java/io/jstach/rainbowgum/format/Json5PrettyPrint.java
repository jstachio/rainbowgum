package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * How the core <code>json5</code> encoder lays out each one line object. Only whitespace
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
	@EnumAlias({ "true", "default" })
	LEVEL_PADDING,
	/**
	 * Level padding and a space after every comma:
	 * <code>level:"INFO" , logger:"com.example.App", thread:...</code>.
	 */
	SPACING;

	static Json5PrettyPrint parse(String value) {
		return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
			case "true", "default" -> LEVEL_PADDING;
			case "false" -> OFF;
			default ->
				io.jstach.rainbowgum.LogProperty.enumValue(Json5PrettyPrint.class, value, "true", "false", "default");
		};
	}

}
