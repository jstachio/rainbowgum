package io.jstach.rainbowgum.pattern.format;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.CompositeFactory;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.FormatterConfig;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.KeywordFactory;
import io.jstach.rainbowgum.pattern.internal.Node;
import io.jstach.rainbowgum.pattern.internal.Parser;
import io.jstach.rainbowgum.pattern.internal.Node.CompositeNode;
import io.jstach.rainbowgum.pattern.internal.Node.End;
import io.jstach.rainbowgum.pattern.internal.Node.FormattingNode;
import io.jstach.rainbowgum.pattern.internal.Node.KeywordNode;
import io.jstach.rainbowgum.pattern.internal.Node.LiteralNode;

/**
 * Compiles a pattern into a formatter.
 */
public sealed interface PatternCompiler {

	/**
	 * Compiles a pattern into a formatter.
	 * @param pattern logback style pattern.
	 * @return formatter.
	 */
	public LogFormatter compile(String pattern);

}

final class Compiler implements PatternCompiler {

	private final FormatterConfig config;

	private final PatternRegistry registry;

	Compiler(PatternRegistry registry, FormatterConfig config) {
		this.config = config;
		this.registry = registry;
	}

	@Override
	public LogFormatter compile(String pattern) {
		Parser p = new Parser(pattern);
		return compile(p.parse());
	}

	LogFormatter compile(Node start) {
		var b = LogFormatter.builder();
		if (start == Node.end()) {
			return b.flatten();
		}

		for (Node n = start; n != Node.end();) {
			n = switch (n) {
				case End e -> {
					yield e;
				}
				case LiteralNode ln -> {
					b.text(ln.value());
					yield ln.next();
				}
				case FormattingNode fn -> {
					FormatterFactory f = registry.getOrNull(fn.keyword());
					if (f == null) {
						throw new IllegalStateException("Missing formatter for key: " + fn.keyword());
					}

					var _child = switch (fn) {
						case CompositeNode cn -> cn.childNode();
						case KeywordNode kn -> null;
					};

					var formatter = switch (f) {
						case KeywordFactory kf -> kf.create(config, fn);
						case CompositeFactory cf -> {
							LogFormatter child = _child == null ? null : compile(_child);
							yield cf.create(config, fn, child);
						}
					};
					b.add(formatter);
					yield fn.next();
				}
			};
		}
		return b.flatten();
	}

}
