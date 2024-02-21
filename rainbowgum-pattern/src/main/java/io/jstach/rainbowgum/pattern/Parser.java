package io.jstach.rainbowgum.pattern;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.pattern.Node.CompositeNode;
import io.jstach.rainbowgum.pattern.Node.FormattingNode;
import io.jstach.rainbowgum.pattern.Node.LiteralNode;
import io.jstach.rainbowgum.pattern.Node.KeywordNode;

// ~=lambda
// E = TE|T

// Left factorization
// E = T(E|~)
// Eopt = E|~
// replace E|~ with Eopt in E
// E = TEopt

// T = LITERAL | '%' C | '%' FORMAT_MODIFIER C
// C = SIMPLE_KEYWORD OPTION | COMPOSITE_KEYWORD COMPOSITE
// OPTION = {...} | ~
// COMPOSITE = E ')' OPTION

public class Parser {

	// public final static Map<String, String> DEFAULT_COMPOSITE_CONVERTER_MAP = new
	// HashMap<String, String>();
	final static String REPLACE_CONVERTER_WORD = "replace";

	// static {
	// DEFAULT_COMPOSITE_CONVERTER_MAP.put(Token.BARE_COMPOSITE_KEYWORD_TOKEN.getValue().toString(),
	// IdentityCompositeConverter.class.getName());
	// DEFAULT_COMPOSITE_CONVERTER_MAP.put(REPLACE_CONVERTER_WORD,
	// ReplacingCompositeConverter.class.getName());
	// }

	final List<Token> tokenList;

	int pointer = 0;

	Parser(TokenStream ts) throws ScanException {
		this.tokenList = ts.tokenize();
	}

	public Parser(String pattern) throws ScanException {
		this(pattern, new RegularEscaper());
	}

	public Parser(String pattern, ParserEscaper parserEscaper) throws ScanException {
		try {
			TokenStream ts = new TokenStream(pattern, parserEscaper);
			this.tokenList = ts.tokenize();
		}
		catch (IllegalArgumentException npe) {
			throw new ScanException("Failed to initialize Parser", npe);
		}
	}

	// /**
	// * When the parsing step is done, the Node list can be transformed into a
	// * converter chain.
	// *
	// * @param top
	// * @param converterMap
	// * @return
	// */
	// public ConverterRegistry<E> compile(final Node top, Map<String, String>
	// converterMap) {
	// Compiler<E> compiler = new Compiler<E>(top, converterMap);
	// compiler.setContext(context);
	// // compiler.setStatusManager(statusManager);
	// return compiler.compile();
	// }

	public Node parse() throws ScanException {
		return E();
	}

	interface NodeBuilder<T extends Node> {

		T next(Node node);

	}

	// E = TEopt
	Node E() throws ScanException {
		NodeBuilder<?> t = T();
		if (t == null) {
			return Node.end();
		}
		Node eOpt = Objects.requireNonNull(Eopt());
		if (eOpt != Node.end()) {
			// t.setNext(eOpt);
			return t.next(eOpt);
		}
		return t.next(Node.end());
	}

	// Eopt = E|~
	Node Eopt() throws ScanException {
		_debug("in Eopt()");
		Token next = getCurentToken();
		_debug("Current token is ", next);
		if (next == null) {
			return Node.end();
		}
		else {
			return E();
		}
	}

	// T = LITERAL | '%' C | '%' FORMAT_MODIFIER C
	@Nullable
	NodeBuilder<?> T() throws ScanException {
		Token t = getCurentToken();
		expectNotNull(t, "a LITERAL or '%'");

		switch (t.getType()) {
			case Token.LITERAL:
				advanceTokenPointer();
				return n -> new LiteralNode(n, t.getValue());
			case Token.PERCENT:
				advanceTokenPointer();
				_debug("% token found");
				FormatInfo fi;
				Token u = getCurentToken();
				NodeBuilder<FormattingNode> c;
				expectNotNull(u, "a FORMAT_MODIFIER, SIMPLE_KEYWORD or COMPOUND_KEYWORD");
				if (u.getType() == Token.FORMAT_MODIFIER) {
					fi = FormatInfo.valueOf((String) u.getValue());
					advanceTokenPointer();
					c = C(fi);
				}
				else {
					c = C(null);
				}
				return c;

			default:
				return null;
		}

	}

	NodeBuilder<FormattingNode> C(@Nullable FormatInfo formatInfo) throws ScanException {
		Token t = getCurentToken();
		_debug("in C()");
		_debug("Current token is ", t);
		expectNotNull(t, "a LEFT_PARENTHESIS or KEYWORD");
		int type = t.getType();
		switch (type) {
			case Token.SIMPLE_KEYWORD:
				return SINGLE(formatInfo);
			case Token.COMPOSITE_KEYWORD:
				advanceTokenPointer();
				return COMPOSITE(formatInfo, t.getValue());
			default:
				throw new IllegalStateException("Unexpected token " + t);
		}
	}

	NodeBuilder<FormattingNode> SINGLE(@Nullable FormatInfo formatInfo) throws ScanException {
		_debug("in SINGLE()");
		Token t = getNextToken();
		_debug("==", t);

		Token ot = getCurentToken();

		List<String> optionList;

		if (ot != null && ot.getType() == Token.OPTION) {
			optionList = ot.getOptionsList();
			advanceTokenPointer();
		}
		else {
			optionList = List.of();
		}
		return n -> new KeywordNode(n, formatInfo, t.getValue(), optionList);
	}

	NodeBuilder<FormattingNode> COMPOSITE(@Nullable FormatInfo formatInfo, String keyword) throws ScanException {

		Node childNode = E();

		Token t = getNextToken();

		if (t == null || t.getType() != Token.RIGHT_PARENTHESIS) {
			String msg = "Expecting RIGHT_PARENTHESIS token but got " + t;
			throw new ScanException(msg);
		}
		Token ot = getCurentToken();

		List<String> optionList;
		if (ot != null && ot.getType() == Token.OPTION) {
			optionList = ot.getOptionsList();
			advanceTokenPointer();
		}
		else {
			optionList = List.of();
		}

		return n -> new CompositeNode(n, formatInfo, keyword, optionList, childNode);
	}

	Token getNextToken() {
		if (pointer < tokenList.size()) {
			return tokenList.get(pointer++);
		}
		return null;
	}

	Token getCurentToken() {
		if (pointer < tokenList.size()) {
			return tokenList.get(pointer);
		}
		return null;
	}

	void advanceTokenPointer() {
		pointer++;
	}

	void expectNotNull(Token t, String expected) {
		if (t == null) {
			throw new IllegalStateException("All tokens consumed but was expecting " + expected);
		}
	}

	private static void _debug(String msg) {
		// System.out.println(msg);
	}

	private static void _debug(String msg, Object arg) {
		// System.out.println(msg + arg);
	}

}
