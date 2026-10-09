package io.jstach.rainbowgum.apt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DefaultParameterTest {

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
		assertTrue(builder.contains("Default is {@link demo.Mode#SLOW slow}."), builder);
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

	static Stream<Arguments> defaultDocs() {
		String uri = "static final java.net.URI DEFAULT_MODE = java.net.URI.create(\"x:y\");";
		String integer = "static final int DEFAULT_MODE = 7;";
		String uriParameter = "@LogConfigurable.DefaultParameter(\"DEFAULT_MODE\") java.net.URI mode";
		String integerParameter = "@LogConfigurable.DefaultParameter(\"DEFAULT_MODE\") Integer mode";
		return Stream.of( //
				arguments("@LogConfigurable.DefaultDoc(\"10MB\") " + uri, uriParameter, "10MB"),
				arguments("/** The {@code x:y} URI. */ " + uri, uriParameter, "The {@code x:y} URI."),
				arguments("public " + uri, uriParameter, "{@link demo.Demo#DEFAULT_MODE }"),
				arguments("/** Seven. */ " + integer, integerParameter, "{@value demo.Demo#DEFAULT_MODE }"),
				arguments("@LogConfigurable.DefaultDoc(\"seven\") " + integer, integerParameter, "seven"));
	}

	@ParameterizedTest
	@MethodSource("defaultDocs")
	void defaultFieldDoc(String field, String parameter, String expectedDoc) throws Exception {
		var result = compile(factory(field, parameter));
		assertEquals(List.of(), result.errors());
		String builder = Files.readString(out.resolve("demo/DemoBuilder.java"));
		assertTrue(builder.contains("Default is " + expectedDoc + "."), builder);
	}

	static Stream<Arguments> undocumentedDefaults() {
		String uri = "static final java.net.URI DEFAULT_MODE = java.net.URI.create(\"x:y\");";
		return Stream.of( //
				arguments(uri), //
				arguments("/**\n * The x:y\n * URI.\n */ " + uri));
	}

	@ParameterizedTest
	@MethodSource("undocumentedDefaults")
	void undocumentedDefaultFieldFails(String field) throws Exception {
		var result = compile(factory(field, "@LogConfigurable.DefaultParameter(\"DEFAULT_MODE\") java.net.URI mode"));
		assertEquals(List.of("Default field demo.Demo.DEFAULT_MODE for parameter 'mode' is not public or a constant "
				+ "so its value cannot be linked in the builder javadoc. Add @LogConfigurable.DefaultDoc or a one line "
				+ "javadoc to the field, or use @DefaultParameter(constant = ...) for an enum parameter."),
				result.errors());
	}

	@Test
	void missingDefaultFieldFails() throws Exception {
		var result = compile(factory("@LogConfigurable.DefaultParameter(\"DEFAULT_MISSING\") java.net.URI mode"));
		assertEquals(List.of("@DefaultParameter on parameter 'mode' references static field 'DEFAULT_MISSING' "
				+ "which does not exist on demo.Demo."), result.errors());
	}

	private static String factory(String parameter) {
		return factory("", parameter);
	}

	private static String factory(String field, String parameter) {
		return """
				package demo;
				import io.jstach.rainbowgum.annotation.LogConfigurable;
				public class Demo {
					%s
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
				""".formatted(field, parameter);
	}

	private ProcessorCompiler.Result compile(String demo) {
		return ProcessorCompiler.compile(out, Map.of("demo/Mode", ENUM, "demo/Demo", demo));
	}

}
