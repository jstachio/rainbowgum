package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

	private List<String> compile(String parameters) {
		return ProcessorCompiler.compile(out, Map.of("demo/Demo", factory(parameters))).errors();
	}

}
