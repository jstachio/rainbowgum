package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PrefixFormatTest {

	@ParameterizedTest
	@ValueSource(strings = { "logging.", "logging.encoder.{name}.", "logging.pattern.config.{name}.",
			"logging.jfr.alerts.", "logging.my_plugin2.{name}.{id}." })
	void validPrefixes(String prefix) {
		assertNull(ConfigProcessor.prefixFormatError(prefix));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			textBlock = """
					'logging'                  | must end with '.'.
					''                         | must end with '.'.
					'logging.{name}'           | must end with '.'.
					'.'                        | must start with 'logging.'.
					'logging..encoder.'        | has an empty segment.
					'.logging.'                | must start with 'logging.'.
					'Logging.'                 | must start with 'logging.'.
					'log.'                     | must start with 'logging.'.
					'loggingx.'                | must start with 'logging.'.
					'myplugin.{name}.'         | must start with 'logging.'.
					'logging.Encoder.'         | has segment 'Encoder' which must be lowercase.
					'logging.keyValues.'       | has segment 'keyValues' which must be lowercase.
					'logging.my-plugin.'       | has segment 'my-plugin' which is not a valid Java package name segment (an identifier that is not a keyword).
					'logging.2d.'              | has segment '2d' which is not a valid Java package name segment (an identifier that is not a keyword).
					'logging.class.'           | has segment 'class' which is not a valid Java package name segment (an identifier that is not a keyword).
					'logging.{}.'              | has segment '{}' which is not a valid Java package name segment (an identifier that is not a keyword).
					'logging.x{name}.'         | has segment 'x{name}' which is not a valid Java package name segment (an identifier that is not a keyword).
					""")
	void invalidPrefixes(String prefix, String expected) {
		assertEquals("@LogConfigurable prefix '" + prefix + "' " + expected, ConfigProcessor.prefixFormatError(prefix));
	}

}
