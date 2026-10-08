package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultParameterConstantTest {

	@TempDir
	Path out;

	static final String ENUM = """
			package demo;
			public enum Mode { FAST, SLOW }
			""";

	@Test
	void constantBecomesTheDefaultAndALinkToTheEnumConstant() throws Exception {
		var result = compile(factory(
				"@LogConfigurable.DefaultParameter(constant = \"SLOW\") @LogConfigurable.ConvertParameter(\"convert\") Mode mode"));
		assertEquals(List.of(), result.errors());
		String builder = Files.readString(out.resolve("demo/DemoBuilder.java"));
		assertTrue(builder.contains("private demo.Mode mode = demo.Mode.SLOW;"), builder);
		assertTrue(builder.contains("Default is {@link demo.Mode#SLOW }."), builder);
	}

	@Test
	void missingConstantFails() throws Exception {
		var result = compile(factory("@LogConfigurable.DefaultParameter(constant = \"MEDIUM\") Mode mode"));
		assertEquals(List
			.of("@DefaultParameter constant 'MEDIUM' is not a constant of demo.Mode. " + "Valid constants: FAST, SLOW"),
				result.errors());
	}

	@Test
	void nonEnumParameterFails() throws Exception {
		var result = compile(factory("@LogConfigurable.DefaultParameter(constant = \"SLOW\") String mode"));
		assertEquals(List.of("@DefaultParameter constant requires an enum parameter but parameter 'mode' "
				+ "is of type java.lang.String."), result.errors());
	}

	@Test
	void valueAndConstantTogetherFails() throws Exception {
		var result = compile(
				factory("@LogConfigurable.DefaultParameter(value = \"DEFAULT_MODE\", constant = \"SLOW\") Mode mode"));
		assertEquals(List.of("@DefaultParameter on parameter 'mode' cannot set both value (static field) "
				+ "and constant (enum constant)."), result.errors());
	}

	private static String factory(String parameter) {
		return """
				package demo;
				import io.jstach.rainbowgum.annotation.LogConfigurable;
				class Demo {
					/**
					 * Demo.
					 * @param mode the mode.
					 * @return the mode.
					 */
					@LogConfigurable(name = "DemoBuilder", prefix = "logging.demo.")
					static String of(%s) {
						return String.valueOf(mode);
					}
					static Mode convert(String value) {
						return Mode.valueOf(value);
					}
				}
				""".formatted(parameter);
	}

	record Result(List<String> errors) {
	}

	private Result compile(String demo) throws Exception {
		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
		List<String> options = new ArrayList<>();
		options.addAll(List.of("-proc:only", "-d", out.toString(), "-s", out.toString()));
		options.addAll(List.of("-classpath", classpath()));
		var task = compiler.getTask(null, fileManager, diagnostics, options, null,
				List.of(source("demo/Mode", ENUM), source("demo/Demo", demo)));
		task.setProcessors(List.of(new ConfigProcessor()));
		task.call();
		// The generated builder references core (LogProperties etc.), which cannot be on
		// this module's classpath because core is built with this processor. Only errors
		// reported against the test's own sources are the processor's.
		var errors = diagnostics.getDiagnostics()
			.stream()
			.filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
			.filter(d -> d.getSource() != null && d.getSource().toUri().getScheme().equals("string"))
			.map(d -> d.getMessage(Locale.ROOT))
			.collect(Collectors.toList());
		return new Result(errors);
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
