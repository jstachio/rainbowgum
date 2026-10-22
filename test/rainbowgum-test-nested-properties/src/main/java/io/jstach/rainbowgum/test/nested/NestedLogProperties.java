package io.jstach.rainbowgum.test.nested;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

import com.typesafe.config.ConfigFactory;

import io.jstach.rainbowgum.LogProperties;

/**
 * Log properties read from a nested format such as TOML or HOCON. The document is turned
 * into a tree of maps, lists and strings.
 * <p>
 * Logger names that contain dots are quoted in the document so they stay one member, as
 * {@link LogProperties.AbstractNested} looks them up:
 *
 * <pre>{@code
 * [logging.level]
 * root = "warn"
 * "com.example" = "debug"
 * "com.example.sub" = "error"
 * }</pre>
 *
 * Unquoted dotted logger names, which the format turns into nested tables, still work as
 * long as the format allows them. A nested format cannot have a value at a key that is
 * also a table, so the root logger is either a value at the prefix (when no other logger
 * is set) or the <code>root</code> member.
 */
public final class NestedLogProperties extends LogProperties.AbstractNested<Object> implements LogProperties.Listable {

	private final String format;

	private final Map<String, Object> root;

	private NestedLogProperties(String format, Map<String, Object> root) {
		this.format = format;
		this.root = root;
	}

	/**
	 * Parses TOML.
	 * @param toml document.
	 * @return properties.
	 * @throws IllegalArgumentException if the document is not valid TOML.
	 */
	public static NestedLogProperties ofToml(String toml) {
		var result = Toml.parse(toml);
		if (result.hasErrors()) {
			throw new IllegalArgumentException("Invalid TOML: "
					+ result.errors().stream().map(Object::toString).collect(Collectors.joining("; ")));
		}
		return new NestedLogProperties("TOML", tree(result));
	}

	/**
	 * Parses HOCON (Lightbend Config).
	 * @param hocon document.
	 * @return properties.
	 */
	public static NestedLogProperties ofHocon(String hocon) {
		return new NestedLogProperties("HOCON", tree(ConfigFactory.parseString(hocon).resolve().root().unwrapped()));
	}

	@Override
	protected Object doRootNodeOrNull() {
		return root;
	}

	@Override
	protected @Nullable Object doMemberNodeOrNull(Object node, String name) {
		return node instanceof Map<?, ?> m ? m.get(name) : null;
	}

	@Override
	protected @Nullable String doValueNodeOrNull(Object node) {
		if (node instanceof String s) {
			return s;
		}
		if (node instanceof List<?> list) {
			return list.stream().map(String::valueOf).collect(Collectors.joining(","));
		}
		return null;
	}

	@Override
	protected @Nullable List<String> doListNodeOrNull(Object node) {
		if (node instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		return null;
	}

	@Override
	protected @Nullable Map<String, String> doMapNodeOrNull(Object node) {
		if (!(node instanceof Map<?, ?> table)) {
			return null;
		}
		Map<String, String> m = new LinkedHashMap<>();
		for (var e : table.entrySet()) {
			if (!(e.getValue() instanceof String value)) {
				return null;
			}
			m.put(String.valueOf(e.getKey()), value);
		}
		return m;
	}

	@Override
	public Collection<String> keys() {
		var keys = new ArrayList<String>();
		addKeys("", root, keys);
		return keys;
	}

	@Override
	public String description(String key) {
		return format + "[" + key + "]";
	}

	@Override
	public String toString() {
		return "NestedLogProperties[" + format + "]";
	}

	private static void addKeys(String prefix, Map<?, ?> table, List<String> keys) {
		for (var e : table.entrySet()) {
			String key = prefix + e.getKey();
			if (e.getValue() instanceof Map<?, ?> m) {
				addKeys(key + LogProperties.SEP, m, keys);
			}
			else {
				keys.add(key);
			}
		}
	}

	/*
	 * Maps, lists and strings, whatever the format's own types are.
	 */
	private static Map<String, Object> tree(TomlTable table) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (var e : table.entrySet()) {
			m.put(e.getKey(), convert(e.getValue()));
		}
		return m;
	}

	private static Map<String, Object> tree(Map<String, ? extends @Nullable Object> map) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (var e : map.entrySet()) {
			var v = e.getValue();
			if (v != null) {
				m.put(e.getKey(), convert(v));
			}
		}
		return m;
	}

	@SuppressWarnings("unchecked")
	private static Object convert(Object value) {
		if (value instanceof TomlTable t) {
			return tree(t);
		}
		if (value instanceof TomlArray a) {
			return a.toList().stream().map(NestedLogProperties::convert).toList();
		}
		if (value instanceof Map<?, ?> m) {
			return tree((Map<String, Object>) m);
		}
		if (value instanceof List<?> l) {
			return l.stream().map(NestedLogProperties::convert).toList();
		}
		return String.valueOf(value);
	}

}
