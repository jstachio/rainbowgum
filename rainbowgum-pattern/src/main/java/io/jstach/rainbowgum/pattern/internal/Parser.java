package io.jstach.rainbowgum.pattern.internal;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.pattern.Padding;
import io.jstach.rainbowgum.pattern.internal.Node.CompositeNode;
import io.jstach.rainbowgum.pattern.internal.Node.FormattingNode;
import io.jstach.rainbowgum.pattern.internal.Node.KeywordNode;
import io.jstach.rainbowgum.pattern.internal.Node.LiteralNode;

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

/*
 * Ported from Logback's grammar-driven parser (see the grammar comment above) - the
 * statement switches here have local declarations and multi-statement case bodies, so
 * converting them to arrow-style expression switches isn't a mechanical rename and
 * risks subtly changing this hand-ported parser's control flow.
 */
@SuppressWarnings("StatementSwitchToExpressionSwitch")
public class Parser {

	// public final static Map<String, String> DEFAULT_COMPOSITE_CONVERTER_MAP = new
	// HashMap<String, String>();
	// private final static String REPLACE_CONVERTER_WORD = "replace";

	// static {
	// DEFAULT_COMPOSITE_CONVERTER_MAP.put(Token.BARE_COMPOSITE_KEYWORD_TOKEN.getValue().toString(),
	// IdentityCompositeConverter.class.getName());
	// DEFAULT_COMPOSITE_CONVERTER_MAP.put(REPLACE_CONVERTER_WORD,
	// ReplacingCompositeConverter.class.getName());
	// }

	private final List<Token> tokenList;

	private int pointer = 0;

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
	// public PatternRegistry<E> compile(final Node top, Map<String, String>
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
		Token next = getCurrentToken();
		_debug("Current token is ", next);
		if (next == null) {
			return Node.end();
		}
		else {
			return E();
		}
	}

	// T = LITERAL | '%' C | '%' FORMAT_MODIFIER C
	@Nullable NodeBuilder<?> T() throws ScanException {
		var t = expectNotNull(getCurrentToken(), "a LITERAL or '%'");

		switch (t.type()) {
			case Token.LITERAL:
				advanceTokenPointer();
				return n -> new LiteralNode(n, t.value());
			case Token.PERCENT:
				advanceTokenPointer();
				_debug("% token found");
				Padding fi;
				NodeBuilder<FormattingNode> c;
				var u = expectNotNull(getCurrentToken(), "a FORMAT_MODIFIER, SIMPLE_KEYWORD or COMPOUND_KEYWORD");
				if (u.type() == Token.FORMAT_MODIFIER) {
					fi = Padding.valueOf(u.value());
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

	NodeBuilder<FormattingNode> C(@Nullable Padding padding) throws ScanException {
		Token t = getCurrentToken();
		_debug("in C()");
		_debug("Current token is ", t);
		t = expectNotNull(t, "a LEFT_PARENTHESIS or KEYWORD");
		int type = t.type();
		switch (type) {
			case Token.SIMPLE_KEYWORD:
				return SINGLE(padding);
			case Token.COMPOSITE_KEYWORD:
				advanceTokenPointer();
				return COMPOSITE(padding, t.value());
			default:
				throw new IllegalStateException("Unexpected token " + t);
		}
	}

	NodeBuilder<FormattingNode> SINGLE(@Nullable Padding padding) throws ScanException {
		_debug("in SINGLE()");
		Token t = expectNotNull(getNextToken(), "a SINGLE");
		_debug("==", t);

		Token ot = getCurrentToken();

		List<String> optionList;

		if (ot != null && ot.type() == Token.OPTION) {
			optionList = ot.optionsList();
			advanceTokenPointer();
		}
		else {
			optionList = List.of();
		}
		return n -> new KeywordNode(n, padding, t.value(), optionList);
	}

	NodeBuilder<FormattingNode> COMPOSITE(@Nullable Padding padding, String keyword) throws ScanException {

		Node childNode = E();

		Token t = getNextToken();

		if (t == null || t.type() != Token.RIGHT_PARENTHESIS) {
			String msg = "Expecting RIGHT_PARENTHESIS token but got " + t;
			throw new ScanException(msg);
		}
		Token ot = getCurrentToken();

		List<String> optionList;
		if (ot != null && ot.type() == Token.OPTION) {
			optionList = ot.optionsList();
			advanceTokenPointer();
		}
		else {
			optionList = List.of();
		}

		return n -> new CompositeNode(n, padding, keyword, optionList, childNode);
	}

	@Nullable Token getNextToken() {
		if (pointer < tokenList.size()) {
			return tokenList.get(pointer++);
		}
		return null;
	}

	@Nullable Token getCurrentToken() {
		if (pointer < tokenList.size()) {
			return tokenList.get(pointer);
		}
		return null;
	}

	void advanceTokenPointer() {
		pointer++;
	}

	Token expectNotNull(@Nullable Token t, String expected) {
		if (t == null) {
			throw new IllegalStateException("All tokens consumed but was expecting " + expected);
		}
		return t;
	}

	/*
	 * Intentionally disabled debug tracing hooks (called throughout this parser) - the
	 * parameters exist so re-enabling the println below doesn't require touching every
	 * call site.
	 */
	@SuppressWarnings("UnusedVariable")
	private static void _debug(String msg) {
		// System.out.println(msg);
	}

	@SuppressWarnings("UnusedVariable")
	private static void _debug(String msg, @Nullable Object arg) {
		// System.out.println(msg + arg);
	}

}
