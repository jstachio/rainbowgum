package io.jstach.rainbowgum.apt;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * Compiles in-memory sources with {@link ConfigProcessor} the way a module using
 * {@code @LogConfigurable} would be compiled, generated builders included.
 */
final class ProcessorCompiler {

	private ProcessorCompiler() {
	}

	record Result(List<String> errors, Path out) {

		String generated(String path) throws IOException {
			return Files.readString(out.resolve(path));
		}

	}

	/**
	 * Compiles the sources.
	 * @param out directory for classes and generated sources.
	 * @param sources source code keyed by path without extension like
	 * <code>demo/Demo</code>.
	 * @return error messages in the order reported, and the output directory.
	 */
	static Result compile(Path out, Map<String, String> sources) {
		var compiler = ToolProvider.getSystemJavaCompiler();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		var fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null);
		List<String> options = List.of("-proc:full", "-d", out.toString(), "-s", out.toString(), "-classpath",
				System.getProperty("java.class.path"));
		List<JavaFileObject> files = new ArrayList<>();
		sources.forEach((name, code) -> files.add(source(name, code)));
		var task = compiler.getTask(null, fileManager, diagnostics, options, null, files);
		task.setProcessors(List.of(new ConfigProcessor()));
		task.call();
		var errors = diagnostics.getDiagnostics()
			.stream()
			.filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
			.map(d -> d.getMessage(Locale.ROOT))
			.toList();
		return new Result(errors, out);
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
