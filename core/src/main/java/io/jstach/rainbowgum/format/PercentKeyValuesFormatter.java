package io.jstach.rainbowgum.format;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;
import io.jstach.rainbowgum.PercentCodec;

/*
 * Percent encoded key values for KeyValuesFormatterBuilder: key=value pairs separated by &,
 * like a URI query. A null value (KEEP) is written as the key alone, without =, so it
 * stays distinct from an empty value.
 */
record PercentKeyValuesFormatter(@Nullable List<String> keys,
		KeyValueNullStrategy nullStrategy) implements LogFormatter.EventFormatter, KeyValuesConsumer<StringBuilder> {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var keyValues = event.keyValues();
		var selected = keys;
		if (selected == null) {
			keyValues.forEach(this, 0, output);
		}
		else {
			int count = 0;
			for (String key : selected) {
				count = accept(keyValues, key, keyValues.getValueOrNull(key), count, output);
			}
		}
	}

	@Override
	public int accept(KeyValues values, String key, @Nullable String value, int count, StringBuilder output) {
		if (value == null) {
			switch (nullStrategy) {
				case SKIP -> {
					return count;
				}
				case EMPTY -> value = "";
				case KEEP -> {
					/* written as the key alone */
				}
			}
		}
		if (count > 0) {
			output.append('&');
		}
		PercentCodec.encode(output, key, StandardCharsets.UTF_8);
		if (value != null) {
			output.append('=');
			PercentCodec.encode(output, value, StandardCharsets.UTF_8);
		}
		return count + 1;
	}

}
