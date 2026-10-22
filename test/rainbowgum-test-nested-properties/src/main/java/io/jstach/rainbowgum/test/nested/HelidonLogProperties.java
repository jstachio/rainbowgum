package io.jstach.rainbowgum.test.nested;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.helidon.config.Config;
import io.jstach.rainbowgum.LogProperties;

/**
 * Log properties read from Helidon Config, which holds its settings as a tree of nodes. A
 * Helidon {@link Config} does not change once created, and a reload makes a new one, so
 * every lookup starts from the latest version of the configuration. A source that polls
 * or watches for changes is therefore read live, and the application only needs to call
 * {@link io.jstach.rainbowgum.LogConfig.ChangePublisher#publish()} from
 * {@link Config#onChange(java.util.function.Consumer)}.
 * <p>
 * Helidon splits keys on dots, quoted YAML keys included, and a node can have both a
 * value and members, so <code>"com.example": debug</code> becomes the members
 * <code>com</code> and <code>example</code>. A logger name with an empty part, such as
 * one ending in <code>...</code>, must be written with Helidon's escape for a dot,
 * <code>~1</code>.
 */
public final class HelidonLogProperties extends LogProperties.AbstractNested<Config> implements LogProperties.Listable {

	private final Config config;

	private HelidonLogProperties(Config config) {
		this.config = config;
	}

	/**
	 * Reads the latest version of a Helidon configuration.
	 * @param config any version of the configuration.
	 * @return properties.
	 */
	public static HelidonLogProperties of(Config config) {
		return new HelidonLogProperties(config);
	}

	@Override
	protected Config doRootNodeOrNull() {
		return config.context().last();
	}

	@Override
	protected @Nullable Config doMemberNodeOrNull(Config node, String name) {
		var member = node.get(Config.Key.escapeName(name));
		return member.exists() ? member : null;
	}

	@Override
	protected @Nullable String doValueNodeOrNull(Config node) {
		var list = doListNodeOrNull(node);
		if (list != null) {
			return String.join(",", list);
		}
		return node.hasValue() ? node.asString().get() : null;
	}

	@Override
	protected @Nullable List<String> doListNodeOrNull(Config node) {
		return node.isList() ? node.asList(String.class).get() : null;
	}

	@Override
	protected @Nullable Map<String, String> doMapNodeOrNull(Config node) {
		if (!node.isObject()) {
			return null;
		}
		Map<String, String> m = new LinkedHashMap<>();
		for (var member : node.asNodeList().get()) {
			if (member.isObject() || member.isList() || !member.hasValue()) {
				return null;
			}
			m.put(member.name(), member.asString().get());
		}
		return m;
	}

	@Override
	public Collection<String> keys() {
		var keys = new ArrayList<String>();
		addKeys(doRootNodeOrNull(), keys);
		return keys;
	}

	@Override
	public String description(String key) {
		return "Helidon[" + key + "]";
	}

	@Override
	public String toString() {
		return "HelidonLogProperties";
	}

	private static void addKeys(Config node, List<String> keys) {
		if (node.isList() || node.hasValue()) {
			keys.add(node.key().toString());
		}
		if (node.isObject()) {
			for (var member : node.asNodeList().get()) {
				addKeys(member, keys);
			}
		}
	}

}
