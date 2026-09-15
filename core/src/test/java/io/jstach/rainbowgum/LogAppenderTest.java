package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * {@link LogAppender#builder(String)} is one of the two hand-written builders (the other
 * being {@link LogRouter.Router#builder(String, LogConfig)}, see {@code RouterTest}) that
 * can otherwise skip every keyed property lookup entirely when every field is set
 * explicitly, so unlike an annotation-processor-generated builder it needs its own eager,
 * direct test proving a bad name is rejected at construction time, not just via
 * {@code ConfigFailureTest}'s property-driven cases.
 */
class LogAppenderTest {

	@Test
	void testBuilderRejectsBadNameBeforeAnyFieldIsSet() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> LogAppender.builder("bad name"));
		assertEquals(
				"Validation failed for io.jstach.rainbowgum.LogAppender: \"logging.appender.{name}.\" cannot be interpolated: parameter 'name' value 'bad name' must be alphanumeric (hyphen/underscore allowed)",
				e.getMessage());
	}

}
