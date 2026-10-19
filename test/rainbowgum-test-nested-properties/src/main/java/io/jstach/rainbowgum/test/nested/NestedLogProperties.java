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
import io.jstach.rainbowgum.LogProperties.LoggerKey;

/**
 * Log properties read from a nested format such as TOML or HOCON. The document is turned
 * into a tree of maps, lists and strings, and every key is looked up by walking it.
 * <p>
 * Keys that end with a logger name ({@link LoggerKey}) are the exception. The logger name
 * is one member of the table at the key's prefix, so it is looked up as is, quoted in the
 * document when it contains dots:
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
 * also a table, so the root logger can either be a value at the prefix (when no other
 * logger is set) or the member named {@value #ROOT}.
 */
public final class NestedLogProperties implements LogProperties.Listable {

	/**
	 * The member at a logger key prefix that holds the root logger's value, the same name
	 * Spring Boot uses.
	 */
	public static final String ROOT = "root";

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
	public @Nullable String valueOrNull(String key) {
		var node = node(key);
		if (node instanceof String s) {
			return s;
		}
		if (node instanceof List<?> list) {
			return list.stream().map(String::valueOf).collect(Collectors.joining(","));
		}
		return null;
	}

	@Override
	public @Nullable List<String> listOrNull(String key) {
		if (node(key) instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		return LogProperties.Listable.super.listOrNull(key);
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

	private @Nullable Object node(String key) {
		var loggerKey = LoggerKey.parseOrNull(key);
		if (loggerKey != null) {
			return loggerNode(loggerKey);
		}
		return at(root, key);
	}

	private @Nullable Object loggerNode(LoggerKey key) {
		var prefix = at(root, key.prefix());
		if (!(prefix instanceof Map<?, ?> members)) {
			return key.loggerName().isEmpty() ? prefix : null;
		}
		String name = key.loggerName().isEmpty() ? ROOT : key.loggerName();
		var member = members.get(name);
		if (member != null) {
			return member;
		}
		// Unquoted, so the format nested it.
		return at(members, name);
	}

	private static @Nullable Object at(Map<?, ?> table, String path) {
		Object node = table;
		for (String segment : path.split("\\.", -1)) {
			if (!(node instanceof Map<?, ?> m)) {
				return null;
			}
			node = m.get(segment);
		}
		return node;
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
			m.put(e.getKey(), value(e.getValue()));
		}
		return m;
	}

	private static Map<String, Object> tree(Map<String, ? extends @Nullable Object> map) {
		Map<String, Object> m = new LinkedHashMap<>();
		for (var e : map.entrySet()) {
			var v = e.getValue();
			if (v != null) {
				m.put(e.getKey(), value(v));
			}
		}
		return m;
	}

	@SuppressWarnings("unchecked")
	private static Object value(Object value) {
		if (value instanceof TomlTable t) {
			return tree(t);
		}
		if (value instanceof TomlArray a) {
			return a.toList().stream().map(NestedLogProperties::value).toList();
		}
		if (value instanceof Map<?, ?> m) {
			return tree((Map<String, Object>) m);
		}
		if (value instanceof List<?> l) {
			return l.stream().map(NestedLogProperties::value).toList();
		}
		return String.valueOf(value);
	}

}
