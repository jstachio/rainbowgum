package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

class LogMessageFormatterTest {

	LogMessageFormatter formatter = StandardMessageFormatter.SLF4J;

	@ParameterizedTest
	@EnumSource(Arg.class)
	void testOneArg(Arg arg1) {
		String message = "Hello {}!";
		String expected = "Hello " + arg1.expected + "!";
		assertOneArg(expected, arg1, message);
	}

	@ParameterizedTest
	@EnumSource(Arg.class)
	void testOneArgDoubleEscape(Arg arg1) {
		String message = "Hello \\\\{}!";
		String expected = "Hello \\" + arg1.expected + "!";
		assertOneArg(expected, arg1, message);
	}

	@ParameterizedTest
	@EnumSource(Arg.class)
	void testOneArgMissingPlaceHolder(Arg arg1) {
		String message = "Hello !";
		String expected = "Hello !";
		assertOneArg(expected, arg1, message);
	}

	private void assertOneArg(String expected, Arg arg1, String message) {
		StringBuilder builder = new StringBuilder();
		formatter.format(builder, message, arg1.arg);
		assertEquals(expected, builder.toString());
		builder.setLength(0);
		formatter.formatArray(builder, message, new @Nullable Object[] { arg1.arg });
		assertEquals(expected, builder.toString());
	}

	@ParameterizedTest
	@MethodSource("arg1Arg2Parameters")
	void testTwoArg(Arg arg1, Arg arg2) {
		String message = "Hello {} : {}!";
		String expected = "Hello " + arg1.expected + " : " + arg2.expected + "!";
		assertTwoArg(expected, arg1, arg2, message);
	}

	@ParameterizedTest
	@MethodSource("arg1Arg2Parameters")
	void testTwoArgEscape(Arg arg1, Arg arg2) {
		String message = "Hello \\{} {} : {}!";
		String expected = "Hello {} " + arg1.expected + " : " + arg2.expected + "!";
		assertTwoArg(expected, arg1, arg2, message);
	}

	@ParameterizedTest
	@MethodSource("arg1Arg2Parameters")
	void testTwoArgOnePlaceHolder(Arg arg1, Arg arg2) {
		String message = "Hello {} : !";
		String expected = "Hello " + arg1.expected + " : !";
		assertTwoArg(expected, arg1, arg2, message);
	}

	@SuppressWarnings("null")
	private void assertTwoArg(String expected, Arg arg1, Arg arg2, String message) {
		StringBuilder builder = new StringBuilder();
		formatter.format(builder, message, arg1.arg, arg2.arg);
		assertEquals(expected, builder.toString());
		builder.setLength(0);
		formatter.formatArray(builder, message, new @Nullable Object[] { arg1.arg, arg2.arg });
		assertEquals(expected, builder.toString());

		builder.setLength(0);
		formatter.format(builder, null, arg1.arg, arg2.arg);
		assertEquals("", builder.toString());
		builder.setLength(0);
		formatter.formatArray(builder, null, new @Nullable Object[] { arg1.arg, arg2.arg });
		assertEquals("", builder.toString());
	}

	@SuppressWarnings("null")
	private static Stream<Arguments> arg1Arg2Parameters() {
		List<Arguments> list = new ArrayList<>();
		for (var arg1 : Arg.values()) {
			for (var arg2 : Arg.values()) {
				list.add(Arguments.of(arg1, arg2));
			}
		}
		return list.stream();
	}

	static final class BadToString {

		@Override
		public String toString() {
			throw new RuntimeException("expected");
		}

	}

	enum Arg {

		BAD("[FAILED toString()]", new BadToString()), //
		STRING("hello", "hello"), //
		STRING_ARRAY_ARRAY("[[a, b, c], [d]]", new String[][] { new String[] { "a", "b", "c" }, new String[] { "d" } }), //
		INTEGER("1", 1), //
		INT_ARRAY_EMPTY("[]", new int[] {}), //
		INT_ARRAY_ONE("[1]", new int[] { 1 }), //
		INT_ARRAY_ONE_TWO("[1, 2]", new int[] { 1, 2 }), //
		INT_ARRAY_ARRAY("[[1, 2]]", new int[][] { new int[] { 1, 2 } }), //
		SHORT_ARRAY_EMPTY("[]", new short[] {}), //
		SHORT_ARRAY_ONE("[1]", new short[] { 1 }), //
		SHORT_ARRAY_ONE_TWO("[1, 2]", new short[] { 1, 2 }), //
		SHORT_ARRAY_ARRAY("[[1, 2]]", new short[][] { new short[] { 1, 2 } }), //
		BYTE_ARRAY_EMPTY("[]", new byte[] {}), //
		BYTE_ARRAY_ONE("[1]", new byte[] { 1 }), //
		BYTE_ARRAY_ONE_TWO("[1, 2]", new byte[] { 1, 2 }), //
		BYTE_ARRAY_ARRAY("[[1, 2]]", new byte[][] { new byte[] { 1, 2 } }), //
		LONG_ARRAY_EMPTY("[]", new long[] {}), //
		LONG_ARRAY_ONE("[1]", new long[] { 1L }), //
		LONG_ARRAY_ONE_TWO("[1, 2]", new long[] { 1L, 2L }), //
		LONG_ARRAY_ARRAY("[[1, 2]]", new long[][] { new long[] { 1L, 2L } }), //
		BOOL_ARRAY_EMPTY("[]", new boolean[] {}), //
		BOOL_ARRAY_ONE("[true]", new boolean[] { true }), //
		BOOL_ARRAY_ONE_TWO("[true, false]", new boolean[] { true, false }), //
		BOOL_ARRAY_ARRAY("[[true, false]]", new boolean[][] { new boolean[] { true, false } }), //
		CHAR_ARRAY_EMPTY("[]", new char[] {}), //
		CHAR_ARRAY_ONE("[y]", new char[] { 'y' }), //
		CHAR_ARRAY_ONE_TWO("[y, n]", new char[] { 'y', 'n' }), //
		CHAR_ARRAY_ARRAY("[[y, n]]", new char[][] { new char[] { 'y', 'n' } }), //
		DOUBLE_ARRAY_EMPTY("[]", new double[] {}), //
		DOUBLE_ARRAY_ONE("[1.0]", new double[] { 1.0d }), //
		DOUBLE_ARRAY_ONE_TWO("[1.0, 2.0]", new double[] { 1.0d, 2.0d }), //
		DOUBLE_ARRAY_ARRAY("[[1.0, 2.0]]", new double[][] { new double[] { 1.0d, 2.0d } }), //
		FLOAT_ARRAY_EMPTY("[]", new float[] {}), //
		FLOAT_ARRAY_ONE("[1.0]", new float[] { 1.0f }), //
		FLOAT_ARRAY_ONE_TWO("[1.0, 2.0]", new float[] { 1.0f, 2.0f }), //
		FLOAT_ARRAY_ARRAY("[[1.0, 2.0]]", new float[][] { new float[] { 1.0f, 2.0f } }), //
		;

		private final String expected;

		private final Object arg;

		private Arg(String expected, Object arg) {
			this.expected = expected;
			this.arg = arg;
		}

	}

}
