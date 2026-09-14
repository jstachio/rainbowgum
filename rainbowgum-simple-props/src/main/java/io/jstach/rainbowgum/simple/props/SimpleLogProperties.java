package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties;

class SimpleLogProperties implements LogProperties {

	private final String resource;

	private final Map<String, PropertyEntry> entries;

	record PropertyEntry(String key, String value, long line) {
	}

	private SimpleLogProperties(String resource, Map<String, PropertyEntry> entries) {
		super();
		this.resource = resource;
		this.entries = entries;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		var e = entries.get(key);
		if (e == null)
			return null;
		return e.value();
	}

	static SimpleLogProperties read(Reader reader, String resource) throws IOException {
		Map<String, PropertyEntry> m = new HashMap<>(); // TreeMap maybe?
		readProperties(reader, e -> {
			m.put(e.key, e);
		});
		return new SimpleLogProperties(resource, m);
	}

	private static String DESCRIPTION = "SIMPLE_PROPS";

	@Override
	public String description(String key) {
		var v = entries.get(key);
		if (v != null) {
			return LogProperties.descriptionForResource(DESCRIPTION, this.resource, key, v.line());
		}
		return LogProperties.descriptionForResource(DESCRIPTION, this.resource, key);
	}

	static Map<String, PropertyEntry> readProperties(String input) {
		Map<String, PropertyEntry> m = new HashMap<>(); // TreeMap maybe?

		StringReader sr = new StringReader(input);
		try {
			readProperties(sr, e -> {
				m.put(e.key, e);
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return m;
	}

	static void readProperties(Reader reader, Consumer<PropertyEntry> consumer) throws IOException {
		LineNumberReader lnr = new LineNumberReader(reader);
		Properties bp = prepareProperties((k, v) -> {
			int line = lnr.getLineNumber();
			consumer.accept(new PropertyEntry(k, v, line));
		});
		bp.load(lnr);

	}

	private static Properties prepareProperties(BiConsumer<String, String> consumer) throws IOException {

		// Hack to use properties class to load but our map for preserved order
		@SuppressWarnings({ "serial", "nullness" })
		Properties bp = new Properties() {
			@Override
			@SuppressWarnings({ "nullness", "keyfor", "UnsynchronizedOverridesSynchronized" }) // checker
																								// bug
			public @Nullable Object put(Object key, Object value) {
				Objects.requireNonNull(key);
				Objects.requireNonNull(value);
				consumer.accept((String) key, (String) value);
				return null;
			}
		};
		return bp;
	}

}
