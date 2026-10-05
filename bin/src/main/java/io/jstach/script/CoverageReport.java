package io.jstach.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Summarizes a JaCoCo XML report without any external coverage service.
 * <p>
 * Usage: {@code java bin/src/main/java/io/jstach/script/CoverageReport.java <jacoco.xml>
 * [--summary <markdown file>] [--badge <svg file>]}
 * <ul>
 * <li>{@code --summary} appends a Markdown table of every counter, for example to
 * {@code $GITHUB_STEP_SUMMARY}.</li>
 * <li>{@code --badge} writes an SVG badge showing line coverage.</li>
 * </ul>
 * With neither option the table is printed to standard out.
 */
public class CoverageReport {

	/*
	 * Counters in the order they are reported; the badge uses LINE.
	 */
	static final List<String> COUNTERS = List.of("LINE", "BRANCH", "INSTRUCTION", "METHOD", "CLASS");

	record Counter(long missed, long covered) {
		double percent() {
			long total = missed + covered;
			return total == 0 ? 100.0 : covered * 100.0 / total;
		}
	}

	public static void main(String[] args) {
		try {
			run(args);
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void run(String[] args) throws Exception {
		if (args.length == 0) {
			throw new IllegalArgumentException("Usage: CoverageReport <jacoco.xml> [--summary file] [--badge file]");
		}
		Path report = Path.of(args[0]);
		Path summary = null;
		Path badge = null;
		for (int i = 1; i < args.length; i++) {
			switch (args[i]) {
				case "--summary" -> summary = Path.of(args[++i]);
				case "--badge" -> badge = Path.of(args[++i]);
				default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
			}
		}
		var counters = counters(report);
		String table = table(counters);
		if (summary != null) {
			Files.writeString(summary, table, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
					java.nio.file.StandardOpenOption.APPEND);
		}
		if (badge != null) {
			Files.writeString(badge, badge("coverage", counters.get("LINE").percent()), StandardCharsets.UTF_8);
		}
		if (summary == null && badge == null) {
			System.out.print(table);
		}
	}

	/*
	 * The report-level counters are the direct <counter> children of <report>.
	 */
	static Map<String, Counter> counters(Path report) throws Exception {
		var factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		// jacoco.xml declares report.dtd, which is not shipped next to it.
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setValidating(false);
		Element root;
		try (InputStream in = Files.newInputStream(report)) {
			root = factory.newDocumentBuilder().parse(in).getDocumentElement();
		}
		Map<String, Counter> counters = new LinkedHashMap<>();
		for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e && e.getTagName().equals("counter")) {
				counters.put(e.getAttribute("type"),
						new Counter(Long.parseLong(e.getAttribute("missed")), Long.parseLong(e.getAttribute("covered"))));
			}
		}
		for (String name : COUNTERS) {
			if (!counters.containsKey(name)) {
				throw new IOException("No " + name + " counter in " + report);
			}
		}
		return counters;
	}

	static String table(Map<String, Counter> counters) {
		var sb = new StringBuilder();
		sb.append("### Code coverage (JaCoCo)\n\n");
		sb.append("| Counter | Coverage | Covered | Missed |\n");
		sb.append("| --- | ---: | ---: | ---: |\n");
		for (String name : COUNTERS) {
			var c = counters.get(name);
			sb.append("| ")
				.append(name.charAt(0))
				.append(name.substring(1).toLowerCase(Locale.ROOT))
				.append(" | ")
				.append(percent(c.percent()))
				.append(" | ")
				.append(c.covered())
				.append(" | ")
				.append(c.missed())
				.append(" |\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	static String percent(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value);
	}

	/*
	 * A flat badge in the common two part style. Text width is approximated, which is
	 * fine for the short fixed strings used here.
	 */
	static String badge(String label, double percent) {
		String value = percent(percent);
		String color = percent >= 90 ? "#4c1" : percent >= 75 ? "#dfb317" : "#e05d44";
		int labelWidth = textWidth(label);
		int valueWidth = textWidth(value);
		int width = labelWidth + valueWidth;
		return """
				<svg xmlns="http://www.w3.org/2000/svg" width="%1$d" height="20" role="img" aria-label="%3$s: %4$s">
				<title>%3$s: %4$s</title>
				<linearGradient id="s" x2="0" y2="100%%"><stop offset="0" stop-color="#bbb" stop-opacity=".1"/><stop offset="1" stop-opacity=".1"/></linearGradient>
				<clipPath id="r"><rect width="%1$d" height="20" rx="3" fill="#fff"/></clipPath>
				<g clip-path="url(#r)"><rect width="%2$d" height="20" fill="#555"/><rect x="%2$d" width="%5$d" height="20" fill="%6$s"/><rect width="%1$d" height="20" fill="url(#s)"/></g>
				<g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" font-size="11">
				<text x="%7$d" y="14">%3$s</text><text x="%8$d" y="14">%4$s</text>
				</g>
				</svg>
				""".formatted(width, labelWidth, label, value, valueWidth, color, labelWidth / 2,
				labelWidth + valueWidth / 2);
	}

	static int textWidth(String text) {
		return text.length() * 7 + 10;
	}

}
