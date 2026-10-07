package io.jstach.rainbowgum.simple.props;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogReporter;

class SimpleLogProperties implements LogProperties, LogReporter.Reportable {

	private final String resource;

	private final Map<String, PropertyEntry> entries;

	private final List<PropertyEntry> invalidEntries;

	record PropertyEntry(String key, String value, long line) {
	}

	private SimpleLogProperties(String resource, Map<String, PropertyEntry> entries,
			List<PropertyEntry> invalidEntries) {
		super();
		this.resource = resource;
		this.entries = entries;
		this.invalidEntries = invalidEntries;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		var e = entries.get(key);
		if (e == null)
			return null;
		return e.value();
	}

	static SimpleLogProperties read(Reader reader, String resource) throws IOException {
		Map<String, PropertyEntry> m = new LinkedHashMap<>();
		var invalidEntries = new ArrayList<PropertyEntry>();
		readProperties(reader, e -> {
			m.put(e.key, e);
			if (!e.key().startsWith(LogProperties.ROOT_PREFIX)) {
				invalidEntries.add(e);
			}
		});
		return new SimpleLogProperties(resource, m, List.copyOf(invalidEntries));
	}

	/*
	 * The same entries with the logging prefix added to keys that lack it, each keeping
	 * its key as written for descriptions. A key given both ways keeps the later line, as
	 * putting into the map in file order does for any repeated key.
	 */
	SimpleLogProperties withShortKeys() {
		Map<String, PropertyEntry> m = new LinkedHashMap<>();
		for (var e : entries.values()) {
			String key = e.key().startsWith(LogProperties.ROOT_PREFIX) ? e.key() : LogProperties.ROOT_PREFIX + e.key();
			var previous = m.get(key);
			if (previous == null || previous.line() <= e.line()) {
				m.put(key, e);
			}
		}
		return new SimpleLogProperties(resource, m, List.of());
	}

	/*
	 * An error for every key without the logging prefix, saying so plainly when the key
	 * is one only java.util.logging uses.
	 */
	List<String> prefixErrors() {
		return invalidEntries.stream().map(e -> isJulKey(e.key()) ? julError(e) : prefixError(e)).toList();
	}

	/*
	 * An error for every key without the logging prefix that only java.util.logging uses,
	 * since its configuration file shares the logging.properties name.
	 */
	List<String> julErrors() {
		return invalidEntries.stream().filter(e -> isJulKey(e.key())).map(this::julError).toList();
	}

	/*
	 * Keys no Rainbow Gum property has, even written without the logging prefix: a key
	 * ending in .level is not one of them because Rainbow Gum has such keys.
	 */
	static boolean isJulKey(String key) {
		return key.equals("handlers") || key.endsWith(".handlers") || key.endsWith(".useParentHandlers")
				|| key.startsWith(".") || key.equals("config") || key.startsWith("java.util.logging.");
	}

	private String prefixError(PropertyEntry e) {
		return "Property key should start with: '" + LogProperties.ROOT_PREFIX + "'. key: '" + e.key() + "' from "
				+ LogProperties.descriptionForResource(DESCRIPTION, resource, e.key(), e.line());
	}

	private String julError(PropertyEntry e) {
		return "Property key looks like java.util.logging configuration, which Rainbow Gum does not read. key: '"
				+ e.key() + "' from " + LogProperties.descriptionForResource(DESCRIPTION, resource, e.key(), e.line());
	}

	private static final String DESCRIPTION = "SIMPLE_PROPS";

	@Override
	public String description(String key) {
		var v = entries.get(key);
		if (v != null) {
			return LogProperties.descriptionForResource(DESCRIPTION, this.resource, v.key(), v.line());
		}
		return LogProperties.descriptionForResource(DESCRIPTION, this.resource, key);
	}

	@Override
	public void report(Appendable out) throws IOException {
		out.append(DESCRIPTION).append("[").append(resource).append("]");
	}

	/*
	 * Matches the order SimpleProperties.Builder previously passed to
	 * LogProperties.builder().order(100) for the classpath-file source: lower than
	 * SYSTEM_PROPERTIES (400) and ENVIRONMENT_VARIABLES (300), so the file stays the
	 * lowest-precedence, tried-last source when coalesced with those two.
	 */
	@Override
	public int order() {
		return 100;
	}

	static Map<String, PropertyEntry> readProperties(String input) {
		Map<String, PropertyEntry> m = new LinkedHashMap<>();
		try {
			readProperties(new StringReader(input), e -> {
				m.put(e.key, e);
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return m;
	}

	/*
	 * java.util.Properties#load(Reader) reads its input through its own large internal
	 * buffer before parsing any entries out of it, so wrapping the Reader in a
	 * LineNumberReader and reading Properties.load()'s line number off it inside an
	 * overridden put() does not work: by the time the first entry is parsed, the reader
	 * has already been drained to (or near) EOF, and every entry ends up reporting the
	 * same, final line number. Confirmed directly: a three-key input reported line 6 (the
	 * file's last line) for the first key too, not line 1.
	 *
	 * Instead, this pre-splits the input into logical entries (one key=value each,
	 * possibly spanning multiple physical lines via a trailing-backslash continuation)
	 * using the same comment (# or !) and continuation rules Properties.load() itself
	 * uses, so line numbers can be tracked while walking physical lines ourselves - then
	 * re-parses each isolated entry through a real Properties#load() call, so escaping,
	 * continuation joining, and key/value separation are still handled by the JDK's own
	 * parser rather than reimplemented here.
	 */
	static void readProperties(Reader reader, Consumer<PropertyEntry> consumer) throws IOException {
		BufferedReader br = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
		StringBuilder chunk = new StringBuilder();
		long chunkStartLine = -1;
		long lineNumber = 0;
		String line;
		while ((line = br.readLine()) != null) {
			lineNumber++;
			if (chunk.length() == 0) {
				String trimmed = line.stripLeading();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
					continue;
				}
				chunkStartLine = lineNumber;
			}
			chunk.append(line);
			if (endsWithContinuation(line)) {
				chunk.append('\n');
				continue;
			}
			parseChunk(chunk.toString(), chunkStartLine, consumer);
			chunk.setLength(0);
		}
		if (chunk.length() > 0) {
			parseChunk(chunk.toString(), chunkStartLine, consumer);
		}
	}

	private static void parseChunk(String chunkText, long line, Consumer<PropertyEntry> consumer) throws IOException {
		Properties p = new Properties();
		p.load(new StringReader(chunkText));
		for (var e : p.entrySet()) {
			consumer.accept(new PropertyEntry((String) e.getKey(), (String) e.getValue(), line));
		}
	}

	/*
	 * A logical line continues onto the next physical line when it ends in an odd number
	 * of backslashes (an even number is that many literal, escaped backslashes with no
	 * continuation; see java.util.Properties's own documented format).
	 */
	private static boolean endsWithContinuation(String line) {
		int count = 0;
		for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
			count++;
		}
		return count % 2 == 1;
	}

}
