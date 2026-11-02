package io.jstach.script;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds the documentation for agents to the aggregate javadoc: the user guide as Markdown
 * and the files in <code>doc/agents</code>, such as <code>llms.txt</code>.
 * <p>
 * The user guide is converted from the generated <code>index.html</code> rather than
 * <code>doc/overview.html</code> because javadoc has already resolved links, values and
 * snippets there. Only the HTML the guide uses is understood. Anything else, such as an
 * unknown tag or a paragraph left open, fails the build so the guide is fixed instead of
 * the Markdown silently losing content.
 * <p>
 * Usage: <code>AgentDocs APIDOCS_DIR AGENTS_DIR VERSION</code>. Writes
 * <code>overview.md</code> and copies the agent files next to <code>index.html</code>, so
 * the links javadoc wrote relative to the site root work unchanged, replacing
 * <code>_VERSION_</code> with the version. Fails if a relative link in any of them points
 * to a missing file or a missing section of the guide. Links the Markdown from the head
 * of <code>index.html</code>.
 */
public final class AgentDocs {

	static final String MARKDOWN_FILE = "overview.md";

	static final String VERSION_TOKEN = "_VERSION_";

	static final String GUIDE_START = "<div class=\"js-toc-content\">";

	static final String MODULES_START = "<div id=\"all-modules-table\">";

	static final String ALTERNATE_LINK = "<link rel=\"alternate\" type=\"text/markdown\" href=\"" + MARKDOWN_FILE
			+ "\" title=\"User guide as Markdown\">";

	public static void main(String[] args) {
		if (args.length != 3) {
			System.err.println("Usage: AgentDocs APIDOCS_DIR AGENTS_DIR VERSION");
			System.exit(2);
		}
		try {
			run(Path.of(args[0]), Path.of(args[1]), args[2]);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void run(Path apidocs, Path agents, String version) throws IOException {
		Path index = apidocs.resolve("index.html");
		String html = Files.readString(index);
		var files = new LinkedHashMap<String, String>();
		files.put(MARKDOWN_FILE, convert(html, version));
		try (var paths = Files.list(agents)) {
			for (var p : paths.sorted().toList()) {
				files.put(p.getFileName().toString(), Files.readString(p).replace(VERSION_TOKEN, version));
			}
		}
		var links = new Links(apidocs, files.keySet(), anchors(files.get(MARKDOWN_FILE)));
		var broken = new ArrayList<String>();
		files.forEach((name, content) -> broken.addAll(links.broken(name, content)));
		if (!broken.isEmpty()) {
			throw new IllegalStateException("Broken links:\n" + String.join("\n", broken));
		}
		for (var e : files.entrySet()) {
			Files.writeString(apidocs.resolve(e.getKey()), e.getValue());
			System.out.println("Wrote " + apidocs.resolve(e.getKey()));
		}
		if (!html.contains(ALTERNATE_LINK)) {
			int head = html.indexOf("</head>");
			if (head < 0) {
				throw new IllegalStateException("No </head> in " + index);
			}
			Files.writeString(index, html.substring(0, head) + ALTERNATE_LINK + "\n" + html.substring(head));
		}
	}

	static final Pattern ANCHOR = Pattern.compile("<a id=\"([^\"]+)\"></a>");

	static Set<String> anchors(String markdown) {
		var ids = new HashSet<String>();
		Matcher m = ANCHOR.matcher(markdown);
		while (m.find()) {
			ids.add(m.group(1));
		}
		return ids;
	}

	/*
	 * Checks relative links: the file must exist in the javadoc or be one of the written
	 * files, and a section of the guide or of the same file must exist. Links to other
	 * sites and anchors in javadoc pages are not checked.
	 */
	record Links(Path apidocs, Set<String> written, Set<String> guideIds) {

		static final Pattern LINK = Pattern.compile("\\]\\(([^)\\s]+)\\)");

		static final Pattern CODE = Pattern.compile("(?s)```.*?```|`[^`\n]*`");

		static final Pattern HEADING = Pattern.compile("(?m)^#+ (.*)$");

		List<String> broken(String file, String content) {
			var result = new ArrayList<String>();
			String text = CODE.matcher(content).replaceAll("");
			Set<String> ownIds = file.equals(MARKDOWN_FILE) ? guideIds : headingIds(text);
			Matcher m = LINK.matcher(text);
			while (m.find()) {
				String target = m.group(1);
				if (target.contains("://") || target.startsWith("mailto:")) {
					continue;
				}
				int hash = target.indexOf('#');
				String path = hash < 0 ? target : target.substring(0, hash);
				String fragment = hash < 0 ? null : target.substring(hash + 1);
				if (path.isEmpty()) {
					if (fragment != null && !ownIds.contains(fragment)) {
						result.add(file + ": no section '" + fragment + "'");
					}
				}
				else if (!written.contains(path) && !Files.exists(apidocs.resolve(path))) {
					result.add(file + ": no file '" + path + "'");
				}
				else if (path.equals(MARKDOWN_FILE) && fragment != null && !guideIds.contains(fragment)) {
					result.add(file + ": no section '" + fragment + "' in " + MARKDOWN_FILE);
				}
			}
			return result;
		}

		/*
		 * Section ids GitHub derives from headings, plus explicit anchors.
		 */
		static Set<String> headingIds(String text) {
			var ids = anchors(text);
			Matcher m = HEADING.matcher(text);
			while (m.find()) {
				ids.add(m.group(1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 -]", "").replace(' ', '-'));
			}
			return ids;
		}

	}

	static String convert(String html, String version) {
		var guide = new Parser(html, GUIDE_START).parse();
		var modules = new Parser(html, MODULES_START).parse();
		var sb = new StringBuilder();
		sb.append("# Rainbow Gum ").append(version).append(" User Guide\n\n");
		sb.append("The user guide from the Rainbow Gum javadoc, which is the index.html next to this file, ")
			.append("as Markdown for agents and other tools. Links are relative to this file.\n\n");
		sb.append(new Renderer().blocks(guide.children()));
		sb.append("\n\n## Modules <a id=\"all-modules-table\"></a>\n\n");
		sb.append(Renderer.modules(modules));
		sb.append("\n");
		return sb.toString().replace(VERSION_TOKEN, version);
	}

	sealed interface Node permits Text, Element {

	}

	record Text(String text) implements Node {
	}

	record Element(String name, Map<String, String> attributes, List<Node> children, int line) implements Node {

		String attributeOrNull(String key) {
			return attributes.get(key);
		}

		boolean hasClass(String c) {
			String classes = attributes.get("class");
			return classes != null && List.of(classes.split(" ")).contains(c);
		}

		List<Element> elements() {
			var result = new ArrayList<Element>();
			for (var n : children) {
				if (n instanceof Element e) {
					result.add(e);
				}
				else if (n instanceof Text t && !t.text().isBlank()) {
					throw fail(this, "unexpected text '" + t.text().strip() + "' in <" + name + ">");
				}
			}
			return result;
		}

	}

	static IllegalStateException fail(Element e, String message) {
		return new IllegalStateException("index.html line " + e.line() + ": " + message);
	}

	/*
	 * Builds the element tree starting at a marker, strictly: every element but the void
	 * ones must be closed by its own end tag.
	 */
	static final class Parser {

		static final Set<String> VOID = Set.of("br", "img", "hr", "input", "meta", "link");

		static final Pattern ATTRIBUTE = Pattern.compile("\\s+([a-zA-Z-]+)=\"([^\"]*)\"");

		final String html;

		final List<Integer> lineStarts = new ArrayList<>();

		int pos;

		Parser(String html, String marker) {
			this.html = html;
			this.pos = html.indexOf(marker);
			if (pos < 0) {
				throw new IllegalStateException("index.html has no " + marker);
			}
			lineStarts.add(0);
			for (int i = 0; i < html.length(); i++) {
				if (html.charAt(i) == '\n') {
					lineStarts.add(i + 1);
				}
			}
		}

		int line(int offset) {
			int i = Collections.binarySearch(lineStarts, offset);
			return i >= 0 ? i + 1 : -i - 1;
		}

		Element parse() {
			var stack = new ArrayList<Element>();
			Element root = null;
			while (pos < html.length()) {
				int lt = html.indexOf('<', pos);
				if (lt < 0) {
					lt = html.length();
				}
				if (lt > pos && !stack.isEmpty()) {
					stack.get(stack.size() - 1).children().add(new Text(decode(html.substring(pos, lt), line(pos))));
				}
				if (lt == html.length()) {
					break;
				}
				if (html.startsWith("<!--", lt)) {
					int end = html.indexOf("-->", lt);
					if (end < 0) {
						throw new IllegalStateException("index.html line " + line(lt) + ": unclosed comment");
					}
					pos = end + 3;
					continue;
				}
				int gt = html.indexOf('>', lt);
				if (gt < 0) {
					throw new IllegalStateException("index.html line " + line(lt) + ": unclosed tag");
				}
				String tag = html.substring(lt + 1, gt);
				pos = gt + 1;
				if (tag.startsWith("/")) {
					String name = tag.substring(1).strip();
					if (stack.isEmpty()) {
						throw new IllegalStateException("index.html line " + line(lt) + ": stray </" + name + ">");
					}
					var open = stack.remove(stack.size() - 1);
					if (!open.name().equals(name)) {
						throw fail(open, "<" + open.name() + "> is closed by </" + name + "> on line " + line(lt)
								+ ", close it explicitly");
					}
					if (stack.isEmpty()) {
						return open;
					}
					continue;
				}
				boolean selfClosing = tag.endsWith("/");
				if (selfClosing) {
					tag = tag.substring(0, tag.length() - 1);
				}
				int nameEnd = 0;
				while (nameEnd < tag.length() && !Character.isWhitespace(tag.charAt(nameEnd))) {
					nameEnd++;
				}
				String name = tag.substring(0, nameEnd);
				var attributes = new HashMap<String, String>();
				Matcher m = ATTRIBUTE.matcher(tag);
				int consumed = nameEnd;
				while (m.find() && m.start() == consumed) {
					attributes.put(m.group(1), decode(m.group(2), line(lt)));
					consumed = m.end();
				}
				if (!tag.substring(consumed).isBlank()) {
					throw new IllegalStateException("index.html line " + line(lt) + ": cannot read attributes of <"
							+ tag + ">, use name=\"value\"");
				}
				var element = new Element(name, attributes, new ArrayList<>(), line(lt));
				if (stack.isEmpty()) {
					root = element;
				}
				else {
					stack.get(stack.size() - 1).children().add(element);
				}
				if (!selfClosing && !VOID.contains(name)) {
					stack.add(element);
				}
				else if (root == element) {
					return element;
				}
			}
			throw new IllegalStateException(
					"index.html: unclosed <" + (stack.isEmpty() ? "?" : stack.get(stack.size() - 1).name()) + ">");
		}

		static final Pattern ENTITY = Pattern.compile("&(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");

		static final Map<String, String> NAMED = Map.of("lt", "<", "gt", ">", "amp", "&", "quot", "\"", "apos",
				"'", "nbsp", "\u00a0");

		static String decode(String s, int line) {
			if (s.indexOf('&') < 0) {
				return s;
			}
			Matcher m = ENTITY.matcher(s);
			var sb = new StringBuilder();
			int last = 0;
			while (m.find()) {
				sb.append(s, last, m.start());
				String e = m.group(1);
				if (e.startsWith("#x")) {
					sb.appendCodePoint(Integer.parseInt(e.substring(2), 16));
				}
				else if (e.startsWith("#")) {
					sb.appendCodePoint(Integer.parseInt(e.substring(1)));
				}
				else {
					String named = NAMED.get(e);
					if (named == null) {
						throw new IllegalStateException("index.html line " + line + ": unknown entity &" + e + ";");
					}
					sb.append(named);
				}
				last = m.end();
			}
			sb.append(s, last, s.length());
			return sb.toString();
		}

	}

	static final class Renderer {

		static final Set<String> BLOCK = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "pre", "div",
				"dl", "table", "hr", "br");

		/*
		 * Renders a mix of block elements and inline content. Runs of inline content
		 * become paragraphs, as browsers render text between blocks.
		 */
		String blocks(List<Node> nodes) {
			var parts = new ArrayList<String>();
			var inline = new ArrayList<Node>();
			for (var n : nodes) {
				if (n instanceof Element e && BLOCK.contains(e.name())) {
					paragraph(inline, parts);
					String block = block(e);
					if (!block.isBlank()) {
						parts.add(block);
					}
				}
				else {
					inline.add(n);
				}
			}
			paragraph(inline, parts);
			return String.join("\n\n", parts);
		}

		void paragraph(List<Node> inline, List<String> parts) {
			String text = inline(inline);
			if (!text.isEmpty()) {
				parts.add(text);
			}
			inline.clear();
		}

		String block(Element e) {
			return switch (e.name()) {
				case "p" -> paragraph(e);
				case "h1", "h2", "h3", "h4", "h5", "h6" -> heading(e);
				case "ul", "ol" -> list(e);
				case "pre" -> pre(e);
				case "div" -> div(e);
				case "dl" -> definitions(e);
				case "table" -> table(e);
				case "hr" -> "---";
				case "br" -> "";
				default -> throw fail(e, "unsupported block <" + e.name() + ">");
			};
		}

		String paragraph(Element p) {
			for (var n : p.children()) {
				if (n instanceof Element c && BLOCK.contains(c.name()) && !c.name().equals("br")) {
					throw fail(c, "<" + c.name() + "> inside <p>, close the <p> before it");
				}
			}
			return anchor(p) + inline(p.children());
		}

		String heading(Element h) {
			int level = h.name().charAt(1) - '0';
			String id = h.attributeOrNull("id");
			return "#".repeat(level) + " " + inline(h.children())
					+ (id == null ? "" : " <a id=\"" + id + "\"></a>");
		}

		static String anchor(Element e) {
			String id = e.attributeOrNull("id");
			return id == null ? "" : "<a id=\"" + id + "\"></a>";
		}

		String list(Element list) {
			var items = new ArrayList<String>();
			int number = 1;
			for (var li : list.elements()) {
				if (!li.name().equals("li")) {
					throw fail(li, "<" + li.name() + "> in <" + list.name() + ">");
				}
				String marker = list.name().equals("ol") ? (number++) + ". " : "- ";
				items.add(item(marker, blocks(li.children())));
			}
			return String.join("\n", items);
		}

		static String item(String marker, String body) {
			String indent = " ".repeat(marker.length());
			var sb = new StringBuilder(marker);
			var lines = body.split("\n", -1);
			for (int i = 0; i < lines.length; i++) {
				if (i > 0) {
					sb.append('\n');
					if (!lines[i].isEmpty()) {
						sb.append(indent);
					}
				}
				sb.append(lines[i]);
			}
			return sb.toString();
		}

		String pre(Element pre) {
			var children = pre.elements();
			if (children.size() != 1 || !children.get(0).name().equals("code")) {
				throw fail(pre, "<pre> must hold exactly one <code>");
			}
			var code = children.get(0);
			String language = "";
			String classes = code.attributeOrNull("class");
			if (classes != null) {
				if (!classes.startsWith("language-") || classes.contains(" ")) {
					throw fail(code, "unsupported code class '" + classes + "'");
				}
				language = classes.substring("language-".length());
			}
			String content = codeText(code);
			if (content.startsWith("\n")) {
				content = content.substring(1);
			}
			content = content.stripTrailing();
			String fence = "```";
			while (content.contains(fence)) {
				fence += "`";
			}
			return fence + language + "\n" + content + "\n" + fence;
		}

		/*
		 * Text of code, keeping the text of links and spans javadoc puts in snippets.
		 */
		static String codeText(Element e) {
			var sb = new StringBuilder();
			for (var n : e.children()) {
				switch (n) {
					case Text t -> sb.append(t.text());
					case Element c when c.name().equals("a") || c.name().equals("span") -> sb.append(codeText(c));
					case Element c -> throw fail(c, "unsupported <" + c.name() + "> in code");
				}
			}
			return sb.toString();
		}

		String div(Element div) {
			if (div.hasClass("snippet-container")) {
				for (var c : div.elements()) {
					if (c.name().equals("pre")) {
						return pre(c);
					}
					if (!c.name().equals("button")) {
						throw fail(c, "unsupported <" + c.name() + "> in snippet");
					}
				}
				throw fail(div, "snippet without <pre>");
			}
			if (div.hasClass("callout")) {
				return callout(div);
			}
			if (div.attributes().isEmpty()) {
				return blocks(div.children());
			}
			throw fail(div, "unsupported <div> " + div.attributes());
		}

		String callout(Element div) {
			var body = new ArrayList<Node>();
			String title = null;
			for (var n : div.children()) {
				if (n instanceof Element e && e.name().equals("p") && e.hasClass("callout-title")) {
					title = inline(e.children());
				}
				else {
					body.add(n);
				}
			}
			if (title == null) {
				throw fail(div, "callout without a callout-title");
			}
			String content = anchor(div) + "**" + title + "**\n\n" + blocks(body);
			var sb = new StringBuilder();
			for (var line : content.split("\n", -1)) {
				sb.append(line.isEmpty() ? ">" : "> " + line).append('\n');
			}
			return sb.toString().stripTrailing();
		}

		String definitions(Element dl) {
			var items = new ArrayList<String>();
			String term = null;
			for (var e : dl.elements()) {
				switch (e.name()) {
					case "dt" -> {
						if (term != null) {
							throw fail(e, "<dt> without <dd>");
						}
						term = inline(e.children());
					}
					case "dd" -> {
						if (term == null) {
							throw fail(e, "<dd> without <dt>");
						}
						items.add(item("- ", "**" + term + "**: " + blocks(e.children())));
						term = null;
					}
					default -> throw fail(e, "<" + e.name() + "> in <dl>");
				}
			}
			return String.join("\n", items);
		}

		String table(Element table) {
			var rows = new ArrayList<List<String>>();
			String caption = "";
			for (var e : table.elements()) {
				switch (e.name()) {
					case "caption" -> caption = inline(e.children());
					case "tr" -> {
						var cells = new ArrayList<String>();
						for (var cell : e.elements()) {
							if (!cell.name().equals("td") && !cell.name().equals("th")) {
								throw fail(cell, "<" + cell.name() + "> in <tr>");
							}
							cells.add(inline(cell.children()).replace("|", "\\|"));
						}
						rows.add(cells);
					}
					default -> throw fail(e, "<" + e.name() + "> in <table>, use <caption> and <tr> only");
				}
			}
			if (rows.isEmpty()) {
				throw fail(table, "empty table");
			}
			var sb = new StringBuilder();
			if (!caption.isEmpty()) {
				sb.append(caption.startsWith("**") ? caption : "**" + caption + "**").append("\n\n");
			}
			int columns = rows.get(0).size();
			for (int r = 0; r < rows.size(); r++) {
				var cells = rows.get(r);
				if (cells.size() != columns) {
					throw fail(table, "row " + (r + 1) + " has " + cells.size() + " cells, expected " + columns);
				}
				sb.append("| ").append(String.join(" | ", cells)).append(" |\n");
				if (r == 0) {
					sb.append("|").append("---|".repeat(columns)).append("\n");
				}
			}
			return sb.toString().stripTrailing();
		}

		String inline(List<Node> nodes) {
			var sb = new StringBuilder();
			for (var n : nodes) {
				switch (n) {
					case Text t -> sb.append(escape(t.text().replaceAll("\\s+", " ")));
					case Element e -> sb.append(inline(e));
				}
			}
			return sb.toString().replaceAll(" {2,}", " ").strip();
		}

		String inline(Element e) {
			return switch (e.name()) {
				case "code" -> code(codeText(e).replaceAll("\\s+", " "));
				case "a" -> link(e);
				case "strong", "b" -> wrap("**", e);
				case "em", "i" -> wrap("*", e);
				case "sup" -> "<sup>" + inline(e.children()) + "</sup>";
				case "span" -> {
					if (!e.hasClass("search-tag-result")) {
						throw fail(e, "unsupported <span> " + e.attributes());
					}
					yield inline(e.children());
				}
				case "br" -> "<br>";
				default -> throw fail(e, "unsupported inline <" + e.name() + ">");
			};
		}

		static String code(String text) {
			String ticks = "`";
			while (text.contains(ticks)) {
				ticks += "`";
			}
			boolean pad = text.startsWith("`") || text.endsWith("`");
			return ticks + (pad ? " " + text + " " : text) + ticks;
		}

		String link(Element a) {
			String href = a.attributeOrNull("href");
			String text = inline(a.children());
			if (href == null) {
				throw fail(a, "<a> without href");
			}
			if (text.isEmpty()) {
				throw fail(a, "<a href=\"" + href + "\"> without text");
			}
			return "[" + text + "](" + href.replace(" ", "%20").replace(")", "%29") + ")";
		}

		/*
		 * Keeps the spaces around emphasized text outside the markers, where Markdown
		 * needs them.
		 */
		String wrap(String marker, Element e) {
			var sb = new StringBuilder();
			for (var n : e.children()) {
				switch (n) {
					case Text t -> sb.append(escape(t.text().replaceAll("\\s+", " ")));
					case Element c -> sb.append(inline(c));
				}
			}
			String raw = sb.toString();
			String text = raw.strip();
			if (text.isEmpty()) {
				return raw;
			}
			String before = raw.startsWith(" ") ? " " : "";
			String after = raw.endsWith(" ") ? " " : "";
			return before + marker + text + marker + after;
		}

		static String escape(String text) {
			var sb = new StringBuilder(text.length());
			for (int i = 0; i < text.length(); i++) {
				char c = text.charAt(i);
				switch (c) {
					case '\\', '`', '*', '_', '[', ']', '<' -> sb.append('\\').append(c);
					default -> sb.append(c);
				}
			}
			return sb.toString();
		}

		static String modules(Element table) {
			var renderer = new Renderer();
			var items = new ArrayList<String>();
			String module = null;
			for (var e : descendants(table)) {
				if (e.hasClass("table-header")) {
					continue;
				}
				if (e.name().equals("div") && e.hasClass("col-first")) {
					module = renderer.inline(e.children());
				}
				else if (e.name().equals("div") && e.hasClass("col-last")) {
					if (module == null) {
						throw fail(e, "module description without a module");
					}
					var block = e.elements().stream().filter(c -> c.hasClass("block")).findFirst();
					String description = block.map(b -> renderer.inline(b.children())).orElse("");
					items.add("- " + module + (description.isEmpty() ? "" : ": " + description));
					module = null;
				}
			}
			if (items.isEmpty()) {
				throw fail(table, "no modules found");
			}
			return String.join("\n", items);
		}

		static List<Element> descendants(Element e) {
			var result = new ArrayList<Element>();
			for (var n : e.children()) {
				if (n instanceof Element c) {
					result.add(c);
					if (!c.hasClass("col-last")) {
						result.addAll(descendants(c));
					}
				}
			}
			return result;
		}

	}

}
