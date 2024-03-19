package io.jstach.rainbowgum.pattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ParserTest {

	@ParameterizedTest
	@EnumSource(value = ParserResult.class)
	void test(ParserResult pr) throws ScanException {
		Parser parser = new Parser(pr.input);
		var node = parser.parse();
		String actual = node.prettyPrint();
		String expected = pr.expected;
		assertEquals(expected, actual);
	}

	@Test
	void testFail() {
		assertThrows(ScanException.class, () -> {
			String input = "";
			Parser parser = new Parser(input);
			parser.parse();
		});
		assertThrows(IllegalStateException.class, () -> {
			String input = "\\";
			Parser parser = new Parser(input);
			parser.parse();
		});
		Throwable e = assertThrows(ScanException.class, () -> {
			String input = "\\p";
			Parser parser = new Parser(input);
			parser.parse();
		});
		while (e.getCause() != null) {
			e = e.getCause();
		}
		assertInstanceOf(IllegalArgumentException.class, e);
		String actual = e.getMessage();
		assertEquals(
				"Illegal char 'p at column 2. Only \\\\, \\_, \\%, \\(, \\), \\t, \\n, \\r combinations are allowed as escape characters.",
				actual);
	}

}

// @formatter:off
enum ParserResult {
	space(" ", "LITERAL[' ']"),
	escape("\\_\\%(stuff\\) \\\\ \\t \\r\\n", "LITERAL['%(stuff) \\ \t \r\n']"),
	escape_option("%blah{\" \\\" \"}", "KEYWORD['blah', options=[ \\\" ]]"),
	keyword_first("%xyz", "KEYWORD['xyz']"),
	keyword("hello%xyz", "LITERAL['hello'], KEYWORD['xyz']"),
	keyword_value("hello%xyz{x}", "LITERAL['hello'], KEYWORD['xyz', options=[x]]"),
	composite("hello%(%child)", "LITERAL['hello'], COMPOSITE[keyword='BARE', childNode=KEYWORD['child']]"),
	compositeSpace("hello%(%child) ", "LITERAL['hello'], COMPOSITE[keyword='BARE', childNode=KEYWORD['child']], LITERAL[' ']"),
	compositeArg("hello%(%child %h)", "LITERAL['hello'], COMPOSITE[keyword='BARE', childNode=KEYWORD['child'], LITERAL[' '], KEYWORD['h']]"),
	compositeNext("hello%(%child %h) %m", "LITERAL['hello'], COMPOSITE[keyword='BARE', childNode=KEYWORD['child'], LITERAL[' '], KEYWORD['h']], LITERAL[' '], KEYWORD['m']"),
	group("%-30(%d{HH:mm:ss.SSS} %t) %-5level", "COMPOSITE[keyword='BARE', childNode=KEYWORD['d', options=[HH:mm:ss.SSS]], LITERAL[' '], KEYWORD['t']], LITERAL[' '], KEYWORD['level']");
	
	final String input;
	final String expected;
	
	private ParserResult(
			String input,
			String expected) {
		this.input = input;
		this.expected = expected;
	}
	
}
// @formatter:on
