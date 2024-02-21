package io.jstach.rainbowgum.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public sealed interface Node {

	public enum NodeKind {

		LITERAL, KEYWORD, COMPOSITE

	}

	public static End end() {
		return End.END;
	}

	public enum End implements Node {

		END;

		public void prettyPrint(StringBuilder sb) {
			sb.append("END");
		}

		public Node nextOrNull() {
			return null;
		}

	}

	public static List<Node> nodes(Node node) {
		List<Node> list = new ArrayList<>();
		Node current = node;
		while (current != end()) {
			list.add(current);
			current = switch (current) {
				case HasNext h -> h.next();
				case End e -> e;
			};
		}
		return list;
	}

	public void prettyPrint(StringBuilder sb);

	static void appendPrint(StringBuilder sb, Node next) {
		if (next != Node.end()) {
			sb.append(", ");
			next.prettyPrint(sb);
		}
	}

	default String prettyPrint() {
		StringBuilder sb = new StringBuilder();
		prettyPrint(sb);
		return sb.toString();
	}

	public sealed interface HasNext extends Node {

		public Node next();

		default NodeKind kind() {
			return switch (this) {
				case LiteralNode n -> NodeKind.LITERAL;
				case KeywordNode n -> NodeKind.KEYWORD;
				case CompositeNode n -> NodeKind.COMPOSITE;
			};
		}

	}

	public record LiteralNode(Node next, String value) implements HasNext {
		public void prettyPrint(StringBuilder sb) {
			sb.append("LITERAL[").append('\'').append(value).append('\'').append("]");
			appendPrint(sb, next);
		}
	}

	public sealed interface FormattingNode extends HasNext {

		@Nullable
		FormatInfo formatInfo();

		String keyword();

		List<String> optionList();

		default <T> T opt(int index, T fallback, Function<String, T> f) {
			var v = opt(index, f);
			if (v == null) {
				return fallback;
			}
			return v;
		}

		default <T> @Nullable T opt(int index, Function<String, T> f) {
			String s = opt(index);
			if (s == null) {
				return null;
			}
			return f.apply(s);
		}

		default @Nullable String opt(int index) {
			var optionList = optionList();
			int size = optionList.size();
			if (index < size) {
				String s = optionList.get(index);
				if (s.isBlank()) {
					return null;
				}
				return s;
			}
			return null;
		}

		default String opt(int index, String fallback) {
			var v = opt(index);
			if (v == null) {
				return fallback;
			}
			return v;
		}

	}

	public record KeywordNode(Node next, @Nullable FormatInfo formatInfo, String keyword,
			List<String> optionList) implements FormattingNode {

		public List<String> getOptions() {
			return optionList;
		}

		public void prettyPrint(StringBuilder sb) {
			sb.append("KEYWORD[").append("'").append(keyword).append("'");
			if (!optionList.isEmpty()) {
				sb.append(", options=").append(optionList);
			}
			sb.append("]");
			appendPrint(sb, next);
		}

	}

	public record CompositeNode(Node next, @Nullable FormatInfo formatInfo, String keyword, List<String> optionList,
			Node childNode) implements FormattingNode {

		public void prettyPrint(StringBuilder sb) {
			sb.append("COMPOSITE[").append("keyword='").append(keyword).append("'");
			if (!optionList.isEmpty()) {
				sb.append(", options=").append(optionList);
			}
			sb.append(", childNode=");
			childNode.prettyPrint(sb);
			sb.append("]");
			appendPrint(sb, next);
		}
	}

}
