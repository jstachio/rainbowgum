package io.jstach.rainbowgum.test.enumaliases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * Every @EnumAlias must agree with its enum's parse method. The annotation has source
 * retention, so rainbowgum-apt records the aliases in each module's
 * META-INF/rainbowgum/enum-aliases.txt (enum binary name, constant, alias, tab
 * separated) and this reads them back.
 */
class EnumAliasesTest {

	static final String RESOURCE = "META-INF/rainbowgum/enum-aliases.txt";

	/*
	 * Values parse lists as valid that are not aliases of one constant.
	 */
	static final Map<String, Set<String>> EXTRA_VALID_VALUES = Map.of(
			// true selects every change type, handled by the list parser
			"io.jstach.rainbowgum.LogConfig$ChangePublisher$ChangeType", Set.of("true"));

	static final Pattern QUOTED = Pattern.compile("'([^']*)'");

	/*
	 * enum binary name -> alias -> constant name
	 */
	static Map<String, Map<String, String>> aliases() {
		Map<String, Map<String, String>> result = new TreeMap<>();
		try {
			var resources = EnumAliasesTest.class.getClassLoader().getResources(RESOURCE);
			for (var url : Collections.list(resources)) {
				try (var reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
					for (String line : reader.lines().toList()) {
						if (line.isBlank()) {
							continue;
						}
						String[] parts = line.split("\t", -1);
						assertEquals(3, parts.length, () -> "Bad line in " + url + ": " + line);
						result.computeIfAbsent(parts[0], k -> new LinkedHashMap<>()).put(parts[2], parts[1]);
					}
				}
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return result;
	}

	static Stream<String> enums() {
		return aliases().keySet().stream();
	}

	@Test
	void everyModuleRecordsItsAliases() {
		assertEquals(Set.of( //
				"io.jstach.rainbowgum.LogConfig$ChangePublisher$ChangeType", //
				"io.jstach.rainbowgum.LogConfig$DebugModeType", //
				"io.jstach.rainbowgum.LogEvent$Caller$CallerType", //
				"io.jstach.rainbowgum.format.TTLL$ColorMode", //
				"io.jstach.rainbowgum.format.TTLL$ColorTheme", //
				"io.jstach.rainbowgum.format.TTLL$KeyValuesFormat", //
				"io.jstach.rainbowgum.format.TTLL$LevelFormat", //
				"io.jstach.rainbowgum.format.TTLL$LoggerFormat", //
				"io.jstach.rainbowgum.format.TTLL$ThreadFormat", //
				"io.jstach.rainbowgum.format.TTLL$TimestampFormat", //
				"io.jstach.rainbowgum.pattern.format.PatternConfig$CacheType", //
				"io.jstach.rainbowgum.simple.props.SimpleProperties$StrictType", //
				"io.jstach.rainbowgum.otlp.EnvironmentVariables"), aliases().keySet());
	}

	@ParameterizedTest
	@MethodSource("enums")
	void aliasesSelectTheirConstant(String enumName) throws Exception {
		var type = enumType(enumName);
		var parse = parse(type);
		for (var e : aliases().get(enumName).entrySet()) {
			Object expected = constant(type, e.getValue());
			assertSame(expected, parse.invoke(null, e.getKey()), () -> enumName + " alias " + e.getKey());
			assertSame(expected, parse.invoke(null, e.getKey().toUpperCase(Locale.ROOT)),
					() -> enumName + " alias " + e.getKey() + " upper case");
		}
	}

	@ParameterizedTest
	@MethodSource("enums")
	void namesSelectTheirConstant(String enumName) throws Exception {
		var type = enumType(enumName);
		var parse = parse(type);
		for (Object c : type.getEnumConstants()) {
			String name = ((Enum<?>) c).name().toLowerCase(Locale.ROOT);
			assertSame(c, parse.invoke(null, name), () -> enumName + " name " + name);
		}
	}

	@ParameterizedTest
	@MethodSource("enums")
	void validValuesAreTheNamesAndAliases(String enumName) throws Exception {
		var type = enumType(enumName);
		var parse = parse(type);
		var e = assertThrows(InvocationTargetException.class, () -> parse.invoke(null, "not-a-valid-value"));
		var cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
		String message = String.valueOf(cause.getMessage());
		int at = message.indexOf("Valid values: ");
		assertEquals(true, at >= 0, () -> enumName + " error lists no valid values: " + message);
		var listed = new TreeSet<String>();
		QUOTED.matcher(message.substring(at)).results().forEach(m -> listed.add(m.group(1)));
		var expected = new TreeSet<String>();
		for (Object c : type.getEnumConstants()) {
			expected.add(((Enum<?>) c).name().toLowerCase(Locale.ROOT));
		}
		for (String alias : aliases().get(enumName).keySet()) {
			expected.add(alias.toLowerCase(Locale.ROOT));
		}
		expected.addAll(EXTRA_VALID_VALUES.getOrDefault(enumName, Set.of()));
		assertEquals(expected, listed, () -> enumName + " valid values");
	}

	private static Class<?> enumType(String enumName) throws ClassNotFoundException {
		return Class.forName(enumName, false, EnumAliasesTest.class.getClassLoader());
	}

	private static Method parse(Class<?> type) throws NoSuchMethodException {
		Method m = type.getDeclaredMethod("parse", String.class);
		assertEquals(type, m.getReturnType(), () -> type + " parse(String) must return the enum");
		m.setAccessible(true);
		return m;
	}

	private static Object constant(Class<?> type, String name) {
		for (Object c : type.getEnumConstants()) {
			if (((Enum<?>) c).name().equals(name)) {
				return c;
			}
		}
		throw new AssertionError(type + " has no constant " + name);
	}

}
