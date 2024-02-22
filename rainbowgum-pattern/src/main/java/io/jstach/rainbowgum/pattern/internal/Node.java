package io.jstach.rainbowgum.pattern.internal;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.pattern.PadInfo;
import io.jstach.rainbowgum.pattern.PatternKeyword;

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

	public sealed interface FormattingNode extends HasNext, PatternKeyword {

	}

	public record KeywordNode(Node next, @Nullable PadInfo padInfo, String keyword,
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

	public record CompositeNode(Node next, @Nullable PadInfo padInfo, String keyword, List<String> optionList,
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
