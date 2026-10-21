package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/*
 * A module name rarely matches its artifact, so every published module-info.java shows
 * its Maven dependency. This catches a snippet that is missing or no longer matches the
 * POM. _VERSION_ is replaced with the release version when the docs are built.
 */
class ModuleMavenSnippetTest {

	private static final Path ROOT = Path.of("../..");

	@Test
	void everyPublishedModuleShowsItsMavenDependency() throws Exception {
		List<Path> descriptors;
		try (Stream<Path> files = Files.walk(ROOT)) {
			descriptors = files.filter(p -> p.endsWith(Path.of("src/main/java/module-info.java")))
				.filter(p -> ROOT.relativize(p).toString().indexOf("/.") < 0 && !p.toString().contains("/target/"))
				.sorted()
				.toList();
		}
		List<String> problems = new ArrayList<>();
		int published = 0;
		for (Path descriptor : descriptors) {
			Path module = descriptor.getParent().getParent().getParent().getParent();
			if (!isPublished(module)) {
				continue;
			}
			published++;
			Element pom = pom(module);
			String groupId = groupId(pom);
			String artifactId = requireText(pom, "artifactId");
			String expected = """
					{@snippet lang=xml :
					<dependency>
					    <groupId>%s</groupId>
					    <artifactId>%s</artifactId>
					    <version>_VERSION_</version>
					</dependency>
					}""".formatted(groupId, artifactId);
			if (!moduleComment(descriptor).contains(expected)) {
				problems.add(ROOT.relativize(descriptor) + " needs in its module comment:\n" + expected);
			}
		}
		assertTrue(published > 0, "no published modules found under " + ROOT.toAbsolutePath().normalize());
		assertEquals(List.of(), problems);
	}

	/*
	 * Unpublished modules (tests, examples, benchmarks) skip deployment in their own POM
	 * or an aggregator above them.
	 */
	private static boolean isPublished(Path module) throws Exception {
		Path root = ROOT.toRealPath();
		for (Path dir = module.toRealPath(); dir.startsWith(root); dir = dir.getParent()) {
			Path pom = dir.resolve("pom.xml");
			if (Files.exists(pom)) {
				var properties = child(pom(dir), "properties");
				var skip = properties == null ? null : child(properties, "skipNexusStagingDeployMojo");
				if (skip != null && skip.getTextContent().strip().equals("true")) {
					return false;
				}
			}
			if (dir.equals(root)) {
				break;
			}
		}
		return true;
	}

	/*
	 * The module doc comment with each line's leading " * " removed.
	 */
	private static String moduleComment(Path descriptor) throws IOException {
		String source = Files.readString(descriptor);
		int start = source.indexOf("/**");
		int end = start < 0 ? -1 : source.indexOf("*/", start);
		if (end < 0) {
			return "";
		}
		StringBuilder comment = new StringBuilder();
		for (String line : source.substring(start + 3, end).split("\n")) {
			String stripped = line.stripLeading();
			if (stripped.startsWith("*")) {
				stripped = stripped.substring(1);
				if (stripped.startsWith(" ")) {
					stripped = stripped.substring(1);
				}
			}
			comment.append(stripped.stripTrailing()).append('\n');
		}
		return comment.toString();
	}

	private static Element pom(Path module) throws Exception {
		return DocumentBuilderFactory.newInstance()
			.newDocumentBuilder()
			.parse(module.resolve("pom.xml").toFile())
			.getDocumentElement();
	}

	private static String groupId(Element pom) {
		var groupId = child(pom, "groupId");
		if (groupId != null) {
			return groupId.getTextContent().strip();
		}
		var parent = child(pom, "parent");
		if (parent == null) {
			throw new IllegalStateException("POM has no groupId or parent");
		}
		return requireText(parent, "groupId");
	}

	private static String requireText(Element element, String name) {
		var child = child(element, name);
		if (child == null) {
			throw new IllegalStateException(element.getTagName() + " has no " + name);
		}
		return child.getTextContent().strip();
	}

	private static @Nullable Element child(Element element, String name) {
		for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
			if (n instanceof Element e && e.getTagName().equals(name)) {
				return e;
			}
		}
		return null;
	}

}
