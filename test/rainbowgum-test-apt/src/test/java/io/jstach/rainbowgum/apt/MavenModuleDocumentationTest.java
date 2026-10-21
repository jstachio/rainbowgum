package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.lang.module.ModuleFinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.annotation.MavenModule;

class MavenModuleDocumentationTest {

	@TempDir
	Path directory;

	@Test
	void coordinatesAppearInJavadocWithoutRetentionOrARuntimeDependency() throws Exception {
		var annotations = annotationPath();
		var source = Files.createDirectory(directory.resolve("source"));
		var descriptor = source.resolve("module-info.java");
		Files.writeString(descriptor, """
				/** Example module. */
				@io.jstach.rainbowgum.annotation.MavenModule(
				    groupId = "org.example", artifactId = "example-artifact")
				module example.module {
				    requires static io.jstach.rainbowgum.annotation;
				    exports example;
				}
				""");
		var api = source.resolve("example/Example.java");
		Files.createDirectory(api.getParent());
		Files.writeString(api, """
				package example;
				/** Example API. */
				public interface Example {}
				""");
		var classes = directory.resolve("classes");
		var diagnostics = new ByteArrayOutputStream();
		int compiled = ToolProvider.getSystemJavaCompiler()
			.run(null, diagnostics, diagnostics, "--release", "21", "-proc:none", "--module-path",
					annotations.toString(), "-d", classes.toString(), descriptor.toString(), api.toString());
		assertEquals(0, compiled, diagnostics.toString(StandardCharsets.UTF_8));
		var bytecode = new String(Files.readAllBytes(classes.resolve("module-info.class")),
				StandardCharsets.ISO_8859_1);
		assertFalse(bytecode.contains("MavenModule"), "source metadata must not enter module-info.class");

		// Only the compiled example is available to the resolver, without the annotations
		// jar.
		var configuration = ModuleLayer.boot()
			.configuration()
			.resolve(ModuleFinder.of(classes), ModuleFinder.of(), Set.of("example.module"));
		var layer = ModuleLayer.boot().defineModulesWithOneLoader(configuration, ClassLoader.getPlatformClassLoader());
		var module = layer.findModule("example.module").orElseThrow();
		assertEquals(0, module.getDeclaredAnnotations().length);
		assertEquals(Set.of("example.module"),
				configuration.modules().stream().map(m -> m.name()).collect(java.util.stream.Collectors.toSet()));

		var docs = directory.resolve("docs");
		int documented = ToolProvider.getSystemDocumentationTool()
			.run(null, diagnostics, diagnostics, "--release", "21", "--module-path", annotations.toString(), "-quiet",
					"-d", docs.toString(), descriptor.toString(), api.toString());
		assertEquals(0, documented, diagnostics.toString(StandardCharsets.UTF_8));
		var html = Files.readString(docs.resolve("example.module/module-summary.html"));
		assertTrue(html.contains("MavenModule"), html);
		assertTrue(html.contains("org.example"), html);
		assertTrue(html.contains("example-artifact"), html);
	}

	@Test
	void annotationCannotBeUsedOnAClass() throws Exception {
		var source = directory.resolve("WrongTarget.java");
		Files.writeString(source, """
				@io.jstach.rainbowgum.annotation.MavenModule(
				    groupId = "org.example", artifactId = "wrong-target")
				class WrongTarget {}
				""");
		var diagnostics = new ByteArrayOutputStream();
		int compiled = ToolProvider.getSystemJavaCompiler()
			.run(null, diagnostics, diagnostics, "--release", "21", "-proc:none", "-XDrawDiagnostics", "-classpath",
					annotationPath().toString(), "-d", directory.toString(), source.toString());
		assertNotEquals(0, compiled);
		assertTrue(diagnostics.toString(StandardCharsets.UTF_8).contains("annotation.type.not.applicable"),
				diagnostics.toString(StandardCharsets.UTF_8));
	}

	private static Path annotationPath() throws Exception {
		return Path.of(MavenModule.class.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

}
