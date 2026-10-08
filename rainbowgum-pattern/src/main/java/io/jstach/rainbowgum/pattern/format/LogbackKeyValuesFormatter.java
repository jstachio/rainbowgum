package io.jstach.rainbowgum.pattern.format;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/*
 * Logback's %X/%mdc with no key: every key value as comma space separated key=value
 * pairs, with no surrounding braces (unlike Log4j2) and no encoding. A key mapped to null
 * is written without "=" to tell it apart from an empty string, and no key values write
 * nothing. Lives here rather than in core since it exists for Logback pattern
 * compatibility; core's own key values formats are logfmt and percent encoding.
 */
enum LogbackKeyValuesFormatter implements LogFormatter.EventFormatter, KeyValuesConsumer<StringBuilder> {

	INSTANCE;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		event.keyValues().forEach(this, 0, output);
	}

	@Override
	public int accept(KeyValues values, String key, @Nullable String value, int index, StringBuilder storage) {
		if (index > 0) {
			storage.append(", ");
		}
		storage.append(key);
		if (value != null) {
			storage.append('=').append(value);
		}
		return index + 1;
	}

}
