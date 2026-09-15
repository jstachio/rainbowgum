package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperty;

/*
 * PatternEncoderBuilder's generated constructor calls LogProperties.interpolateKey
 * eagerly (see the codegen template every @LogConfigurable builder shares), so a bad name
 * is rejected immediately, before any property or field is ever set, and wrapped into a
 * ValidationException the same way the generated build() method already wraps a bad
 * factory-method call.
 */
class PatternEncoderBuilderNameTest {

	@Test
	void testBuilderRejectsBadNameBeforeAnyFieldIsSet() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> new PatternEncoderBuilder("bad name"));
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.pattern.format.PatternEncoderBuilder: \"logging.encoder.{name}.\" cannot be interpolated: parameter 'name' value 'bad name' must be alphanumeric (hyphen/underscore allowed)",
				e.getMessage());
	}

}
