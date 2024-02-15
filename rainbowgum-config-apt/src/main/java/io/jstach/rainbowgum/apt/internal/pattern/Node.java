package io.jstach.rainbowgum.apt.internal.pattern;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

public sealed interface Node {

	public static End end() {
		return End.END;
	}

	public enum End implements Node {

		END;

		public void prettyPrint(StringBuilder sb) {
			sb.append("END");
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

	}

	public record LiteralNode(Node next, String value) implements HasNext {
		public void prettyPrint(StringBuilder sb) {
			sb.append("LITERAL[").append('\'').append(value).append('\'').append("]");
			appendPrint(sb, next);
		}
	}

	public sealed interface FormattingNode extends HasNext {

		public FormatInfo formatInfo();

	}

	public record SimpleKeywordNode(Node next, @Nullable FormatInfo formatInfo, String value,
			List<String> optionList) implements FormattingNode {

		public List<String> getOptions() {
			return optionList;
		}

		public void prettyPrint(StringBuilder sb) {
			sb.append("KEYWORD[").append("'").append(value).append("'");
			if (!optionList.isEmpty()) {
				sb.append(", options=").append(optionList);
			}
			sb.append("]");
			appendPrint(sb, next);
		}

	}

	public record CompositeNode(Node next, FormatInfo formatInfo, String keyword, List<String> optionList,
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
