package io.jstach.rainbowgum.keyvalues.contributor;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;

/**
 * Key values added to every event, registered as the
 * {@link KeyValuesContributor.Source.Standard#DEFAULTS} source so every other source
 * overrides them on a key collision. Values come from the {@value #DEFAULTS_PROPERTY}
 * property and from code; a property value wins over a value set in code for the same
 * key. For example: <pre><code>
 * logging.keyvalues.contributor.defaults=git.commit=abc123&amp;instance.id=i-42
 * </code></pre> or from code, which may change values at any time: <pre><code>
 * DefaultKeyValues.of(config.serviceRegistry()).put("app_status", "READY");
 * </code></pre> Every change copies the values once into an immutable snapshot that
 * events then share, so reading them per event costs one volatile read. Disable them with
 * <code>logging.keyvalues.disabled=defaults</code>.
 */
public final class DefaultKeyValues implements KeyValuesContributor {

	/**
	 * Property prefix of this module.
	 */
	public static final String PROPERTY_PREFIX = LogProperties.ROOT_PREFIX + "keyvalues.contributor.";

	/**
	 * Key values added to every event, as a map property, in the order the property
	 * source gives them, which for the URI query form (<code>a=1&amp;b=2</code>) is the
	 * order declared.
	 */
	public static final String DEFAULTS_PROPERTY = PROPERTY_PREFIX + "defaults";

	private final Object lock = new Object();

	private KeyValues configured = KeyValues.of();

	private KeyValues programmatic = KeyValues.of();

	private volatile KeyValues current = KeyValues.of();

	private DefaultKeyValues() {
	}

	/**
	 * Finds the default key values in a service registry, creating them if needed.
	 * @param registry usually {@link io.jstach.rainbowgum.LogConfig#serviceRegistry()}.
	 * @return the default key values of that registry.
	 */
	public static DefaultKeyValues of(ServiceRegistry registry) {
		return registry.putIfAbsent(DefaultKeyValues.class, DefaultKeyValues::new);
	}

	@Override
	public KeyValues keyValues() {
		return current;
	}

	/**
	 * Replaces the key values set in code.
	 * @param keyValues copied once, never kept.
	 */
	public void set(KeyValues keyValues) {
		synchronized (lock) {
			programmatic = copy(keyValues);
			update();
		}
	}

	/**
	 * Sets one key value from code, keeping the others.
	 * @param key key.
	 * @param value value, may be <code>null</code>.
	 */
	public void put(String key, @Nullable String value) {
		synchronized (lock) {
			var kvs = copyMutable(programmatic);
			kvs.putKeyValue(key, value);
			programmatic = kvs.freeze();
			update();
		}
	}

	/**
	 * Removes one key set in code, keeping the others.
	 * @param key key.
	 */
	public void remove(String key) {
		synchronized (lock) {
			var kvs = copyMutable(programmatic);
			kvs.remove(key);
			programmatic = kvs.freeze();
			update();
		}
	}

	/*
	 * Called by the configurator with the property values, which win over code.
	 */
	void configure(KeyValues keyValues) {
		synchronized (lock) {
			configured = copy(keyValues);
			update();
		}
	}

	private void update() {
		current = KeyValues.merge(programmatic, configured);
	}

	private static KeyValues copy(KeyValues keyValues) {
		return copyMutable(keyValues).freeze();
	}

	private static MutableKeyValues copyMutable(KeyValues keyValues) {
		var kvs = MutableKeyValues.of(keyValues.size() + 1);
		keyValues.forEach(kvs);
		return kvs;
	}

}
