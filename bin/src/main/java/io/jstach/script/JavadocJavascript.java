package io.jstach.script;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

public class JavadocJavascript {

	static PrintStream out = System.out;
	static final String VERSION_TOKEN = "_VERSION_";
	static String version = VERSION_TOKEN;

	final static String DOC_ROOT = "../";

	public static void main(String[] args) {
		try {
			if (args.length > 0) {
				version = args[0];
			}
			findFiles();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	static void findFiles() throws Exception {
		// final String API_DOCS = "target/site/apidocs";
		final String API_DOCS = "target/reports/apidocs";

		String path = DOC_ROOT + API_DOCS;
		// Path resourcesPath = Path.of("../doc/target/site/apidocs/resources");
		Path resourcesPath = Path.of(DOC_ROOT + API_DOCS + "/resources");

		var p = Path.of(path);
		int maxDepth = 100;
		BiPredicate<Path, BasicFileAttributes> matcher = (_p, _a) -> {
			return _p.getFileName().toString().endsWith(".html");
		};
		try (Stream<Path> stream = Files.find(p, maxDepth, matcher)) {
			for (var _p : stream.toList()) {
				out.println("Fixing " + _p);
				var relativeResourcesPath = _p.getParent().relativize(resourcesPath);
				addJavascript(_p, relativeResourcesPath);
			}
		}
		addToc(Path.of(DOC_ROOT + API_DOCS + "/index.html"));
		removeSearchFocus(Path.of(DOC_ROOT + API_DOCS + "/search.js"));

	}

	static void addToc(Path htmlPath) throws IOException {
		List<String> lines = Files.readAllLines(htmlPath);
		List<String> processed = new ArrayList<>();
		boolean found = false;
		for (String line : lines) {
			if (line.startsWith("<div class=\"header\"")) {
				found = true;
				processed.add(line);
				processed.add("<nav class=\"js-toc\"></nav>");
			}
			else {
				processed.add(line);
			}
		}
		if (found) {
			Files.write(htmlPath, processed, StandardOpenOption.WRITE);
		}
		else {
			out.println("header not found for: " + htmlPath);
		}
	}

	static void removeSearchFocus(Path searchJs) throws IOException {
		List<String> lines = Files.readAllLines(searchJs);
		List<String> processed = new ArrayList<>();
		boolean found = false;
		for (String line : lines) {
			if (line.trim().equals("search.focus();")) {
				found = true;
				processed.add("//rainbowgum commented out: search.focus()");
			}
			else {
				processed.add(line);
			}
		}
		if (found) {
			Files.write(searchJs, processed, StandardOpenOption.WRITE);
		}
		else {
			throw new IOException("search.focus not found");
			// out.println("header not found for: " + searchJs);
		}
	}

	static void addJavascript(Path htmlPath, Path resourcesPath) throws IOException {
		List<String> lines = Files.readAllLines(htmlPath);
		List<String> processed = new ArrayList<>();
		boolean found = false;
		for (String line : lines) {
			if (line.startsWith("</body>")) {
				found = true;
				processed.add(scriptTag("https://cdnjs.cloudflare.com/ajax/libs/tocbot/4.18.2/tocbot.min.js"));
				processed.add(scriptTag("https://cdn.jsdelivr.net/npm/anchor-js/anchor.min.js"));
				processed.add(scriptTag(resourcesPath + "/" + "jstachio.js"));
				processed.add(line);
			}
			else if (line.contains(VERSION_TOKEN)) {
				processed.add(line.replace(VERSION_TOKEN, version));
			}
			else {
				processed.add(line);
			}
		}
		if (found) {
			Files.write(htmlPath, processed, StandardOpenOption.WRITE);
		}
		else {
			out.println("body tag not found for: " + htmlPath);
		}
	}

	static String scriptTag(String src) {
		return "<script src=\"" + src + "\"></script>";
	}

}
