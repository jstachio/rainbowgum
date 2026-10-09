package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PropertyNameCaseTest {

	@TempDir
	Path out;

	@Test
	void distinctNamesCompile() throws Exception {
		assertEquals(List.of(), compile("String keyValues, String values"));
	}

	@Test
	void namesDifferingOnlyByCaseFail() throws Exception {
		assertEquals(List.of("Property 'SAY' differs from property 'Say' only by case. "
				+ "Property names must differ ignoring case."), compile("String Say, String SAY"));
	}

	@Test
	void everyCollisionIsReported() throws Exception {
		assertEquals(
				List.of("Property 'KeyValues' differs from property 'keyValues' only by case. "
						+ "Property names must differ ignoring case.",
						"Property 'keyvalues' differs from property 'keyValues' only by case. "
								+ "Property names must differ ignoring case."),
				compile("String keyValues, String KeyValues, String keyvalues"));
	}

	private static String factory(String parameters) {
		return """
				package demo;
				import io.jstach.rainbowgum.annotation.LogConfigurable;
				class Demo {
					/**
					 * Demo.
					 * @return demo.
					 */
					@LogConfigurable(name = "DemoBuilder", prefix = "logging.demo.")
					static String of(%s) {
						return "";
					}
				}
				""".formatted(parameters);
	}

	private List<String> compile(String parameters) throws Exception {
		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
		List<String> options = List.of("-proc:only", "-d", out.toString(), "-s", out.toString(), "-classpath",
				classpath());
		var task = compiler.getTask(null, fileManager, diagnostics, options, null,
				List.of(source("demo/Demo", factory(parameters))));
		task.setProcessors(List.of(new ConfigProcessor()));
		task.call();
		// The generated builder references core (LogProperties etc.), which cannot be on
		// this module's classpath because core is built with this processor. Only errors
		// reported against the test's own sources are the processor's.
		return diagnostics.getDiagnostics()
			.stream()
			.filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
			.filter(d -> d.getSource() != null && d.getSource().toUri().getScheme().equals("string"))
			.map(d -> d.getMessage(Locale.ROOT))
			.toList();
	}

	private static String classpath() {
		// Surefire may put dependencies on either path depending on module-info handling.
		var paths = new ArrayList<String>();
		for (String key : List.of("java.class.path", "jdk.module.path")) {
			String value = System.getProperty(key);
			if (value != null && !value.isBlank()) {
				paths.add(value);
			}
		}
		return String.join(File.pathSeparator, paths);
	}

	private static JavaFileObject source(String name, String code) {
		return new SimpleJavaFileObject(URI.create("string:///" + name + ".java"), JavaFileObject.Kind.SOURCE) {
			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return code;
			}
		};
	}

}
