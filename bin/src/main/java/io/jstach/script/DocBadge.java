package io.jstach.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes an SVG badge showing the version of the documentation it sits beside, so the
 * README can show which version each documentation link leads to. The doc build writes
 * it into the aggregate javadoc, so the snapshot site and every published release carry
 * their own.
 * <p>
 * Usage: {@code java bin/src/main/java/io/jstach/script/DocBadge.java <svg file> <version>}
 */
public final class DocBadge {

	static final String LABEL = "docs + javadoc";

	static final String COLOR = "#007ec6";

	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Usage: DocBadge SVG_FILE VERSION");
			System.exit(2);
		}
		try {
			Path file = Path.of(args[0]);
			Files.writeString(file, badge(LABEL, args[1], COLOR), StandardCharsets.UTF_8);
			System.out.println("Wrote " + file);
		}
		catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/*
	 * A flat badge in the same two part style as CoverageReport's.
	 */
	static String badge(String label, String value, String color) {
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

	/*
	 * Approximate Verdana 11px widths. Versions mix narrow dots and wide capitals, as in
	 * 1.0.0-SNAPSHOT, so one width per character would clip or pad them.
	 */
	static int textWidth(String text) {
		double width = 0;
		for (char c : text.toCharArray()) {
			if (c == '.' || c == ' ') {
				width += 3.9;
			}
			else if (c == '-') {
				width += 4.6;
			}
			else if (c == '+') {
				width += 9.2;
			}
			else if (Character.isUpperCase(c)) {
				width += 7.8;
			}
			else {
				width += 7.0;
			}
		}
		return (int) Math.ceil(width) + 10;
	}

}
