package io.jstach.rainbowgum.pattern.internal;

import java.util.List;

import org.jspecify.annotations.Nullable;

class Token {

	static final int PERCENT = 37;

	// static final int LEFT_PARENTHESIS = 40;
	static final int RIGHT_PARENTHESIS = 41;
	static final int MINUS = 45;
	static final int DOT = 46;
	static final int CURLY_LEFT = 123;
	static final int CURLY_RIGHT = 125;

	static final int LITERAL = 1000;
	static final int FORMAT_MODIFIER = 1002;
	static final int SIMPLE_KEYWORD = 1004;
	static final int COMPOSITE_KEYWORD = 1005;
	static final int OPTION = 1006;

	static final int EOF = Integer.MAX_VALUE;

	static Token EOF_TOKEN = new Token(EOF, "EOF");
	static Token RIGHT_PARENTHESIS_TOKEN = new Token(RIGHT_PARENTHESIS);

	// BARE as in naked. Used for formatting purposes
	static Token BARE_COMPOSITE_KEYWORD_TOKEN = new Token(COMPOSITE_KEYWORD, "BARE");
	static Token PERCENT_TOKEN = new Token(PERCENT);

	private final int type;

	private final @Nullable String value;

	private final @Nullable List<String> optionsList;

	Token(int type) {
		this(type, null, null);
	}

	Token(int type, String value) {
		this(type, value, null);
	}

	Token(int type, List<String> optionsList) {
		this(type, null, optionsList);
	}

	private Token(int type, @Nullable String value, @Nullable List<String> optionsList) {
		this.type = type;
		this.value = value;
		this.optionsList = optionsList;
	}

	public String value() {
		String v = value;
		if (v == null) {
			throw new IllegalStateException("Parser expected a value but was null. Likely a bug");
		}
		return v;
	}

	public List<String> optionsList() {
		var list = optionsList;
		if (list == null) {
			throw new IllegalStateException("Parser expected a option list but was null. Likely a bug");

		}
		return list;
	}

	public int type() {
		return type;
	}

	public @Nullable String valueOrNull() {
		return value;
	}

	public @Nullable List<String> optionsListOrNull() {
		return optionsList;
	}

}
