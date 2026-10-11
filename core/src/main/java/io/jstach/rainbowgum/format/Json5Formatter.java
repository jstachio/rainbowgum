package io.jstach.rainbowgum.format;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.format.KeyValuesFormatterBuilder.Format;

/*
 * Each event as one JSON5 (or JSON) object, with logfmt's field names. Named json5 rather
 * than json so it is not mistaken for the way to log JSON, which is the rainbowgum-json
 * encoders. Package private: the public API is the generated Json5FormatterBuilder and the
 * json5 encoder scheme.
 */
final class Json5Formatter implements LogFormatter.EventFormatter {

	static final String KEY_VALUES_FIELD = "keyValues";

	/*
	 * The event's own fields, which merged key values must not repeat.
	 */
	static final Set<String> FIELDS = Set.of("time", "level", "logger", "thread", "msg", "error", "stacktrace",
			KEY_VALUES_FIELD);

	/*
	 * One space, the same as the rainbowgum-json encoders' pretty print.
	 */
	private static final String INDENT = " ";

	private static final TimestampFormatter TIME = TimestampFormatter.ofISO();

	private final boolean json5;

	private final KeyValuesPlacement keyValues;

	private final LevelFormatter levelFormatter;

	/*
	 * Written between the event object's members: a comma, followed by a space when
	 * spacing or by a newline and indent when full.
	 */
	private final String separator;

	/*
	 * Written between the nested key values object's members, which stay on one line.
	 */
	private final String nestedSeparator;

	/*
	 * After the opening brace and before the closing one: a newline when full.
	 */
	private final String open;

	private final String close;

	private final Palette palette;

	/*
	 * Built once so writing key values allocates nothing per event.
	 */
	private final Members flat;

	private final Members nestedAll;

	private final Members nestedColliding;

	private Json5Formatter(boolean json5, KeyValuesPlacement keyValues, Json5PrettyPrint prettyPrint, Palette palette) {
		this.json5 = json5;
		this.keyValues = keyValues;
		this.levelFormatter = switch (prettyPrint) {
			case OFF, FULL -> LevelFormatter.of();
			case LEVEL_PADDING, SPACING -> LevelFormatter.ofRightPadded();
		};
		this.separator = switch (prettyPrint) {
			case OFF, LEVEL_PADDING -> ",";
			case SPACING -> ", ";
			case FULL -> ",\n" + INDENT;
		};
		this.nestedSeparator = prettyPrint == Json5PrettyPrint.SPACING ? ", " : ",";
		boolean full = prettyPrint == Json5PrettyPrint.FULL;
		this.open = full ? "{\n" + INDENT : "{";
		this.close = full ? "\n}\n" : "}\n";
		this.palette = palette;
		this.flat = new Members(json5, separator, Members.Mode.FLAT);
		this.nestedAll = new Members(json5, nestedSeparator, Members.Mode.NESTED_ALL);
		this.nestedColliding = new Members(json5, nestedSeparator, Members.Mode.NESTED_COLLIDING);
	}

	/**
	 * Formats each event as a JSON5 (or JSON) object, one line unless pretty printed in
	 * full, for switching an application to structured logging without adding a module,
	 * for example:
	 * <code>{time:"2026-10-05T15:04:05.123Z",level:"INFO" ,logger:"com.example.App",thread:"main",msg:"hello world",requestId:"42"}</code>.
	 * <p>
	 * The fields use logfmt's names: <code>time</code> (UTC instant with milliseconds),
	 * <code>level</code>, <code>logger</code>, <code>thread</code>, <code>msg</code>, and
	 * for a throwable <code>error</code> (its <code>toString()</code>) and
	 * <code>stacktrace</code>. Every value, key values included, is a string, or
	 * <code>null</code> for a key value without one. For a specific schema (ECS, GELF,
	 * Logstash) and more options use the <code>rainbowgum-json</code> module.
	 * <p>
	 * With JSON5, values can be colored like the TTLL formatter, and by default the level
	 * is padded after its closing quote so the value stays the level name:
	 * <code>level:"INFO" ,logger:...</code>. With JSON the color, theme, and pretty print
	 * are ignored, so the output stays plain JSON.
	 * @param name encoder name, used for property lookup.
	 * @param format JSON5 (default), which leaves identifier keys unquoted, or JSON.
	 * @param keyValues where key values go, see {@link KeyValuesPlacement}.
	 * @param prettyPrint JSON5 only, added whitespace, see {@link Json5PrettyPrint}.
	 * @param color JSON5 only, whether to color values, see {@link TTLL.ColorMode}.
	 * @param theme JSON5 only, which colors, see {@link TTLL.ColorTheme}.
	 * @return formatter.
	 */
	@LogConfigurable(name = "Json5FormatterBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter(
					constant = "JSON5") @LogConfigurable.ConvertParameter("convertFormat") Format format,
			@LogConfigurable.DefaultParameter(
					constant = "MERGED") @LogConfigurable.ConvertParameter("convertKeyValues") KeyValuesPlacement keyValues,
			@LogConfigurable.DefaultParameter(
					constant = "LEVEL_PADDING") @LogConfigurable.ConvertParameter("convertPrettyPrint") Json5PrettyPrint prettyPrint,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorMode color,
			@LogConfigurable.ConvertParameter("convertTheme") TTLL.@Nullable ColorTheme theme) {
		return switch (format) {
			case JSON -> new Json5Formatter(false, keyValues, Json5PrettyPrint.OFF, Palette.OFF);
			case JSON5 -> new Json5Formatter(true, keyValues, prettyPrint,
					Palette.of(color == null ? TTLL.ColorMode.DEFAULT : color, theme, AnsiSupport::isAnsiSupported));
			case PERCENT, LOGFMT -> throw new IllegalArgumentException(
					"format=" + format.name().toLowerCase(java.util.Locale.ROOT) + " is not JSON. Use json or json5.");
		};
	}

	static Format convertFormat(String value) {
		return LogProperty.enumValue(Format.class, value);
	}

	static KeyValuesPlacement convertKeyValues(String value) {
		return KeyValuesPlacement.parse(value);
	}

	static Json5PrettyPrint convertPrettyPrint(String value) {
		return Json5PrettyPrint.parse(value);
	}

	static TTLL.ColorMode convertColor(String value) {
		return TTLLFormatter.convertColor(value);
	}

	static TTLL.ColorTheme convertTheme(String value) {
		return TTLLFormatter.convertTheme(value);
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var p = palette;
		output.append(open);
		appendKey(output, "time", json5);
		output.append(':');
		Palette.start(output, p.timestamp());
		output.append('"');
		TIME.formatTimestamp(output, event.timestamp());
		output.append('"');
		Palette.end(output, p.timestamp());
		output.append(separator);
		appendKey(output, "level", json5);
		output.append(':');
		String levelColor = p.levelColor(event.level());
		Palette.start(output, levelColor);
		output.append('"');
		int levelStart = output.length();
		levelFormatter.formatLevel(output, event.level());
		int padding = Palette.removeTrailingSpaces(output, levelStart);
		output.append('"');
		Palette.end(output, levelColor);
		Palette.spaces(output, padding);
		output.append(separator);
		appendKey(output, "logger", json5);
		output.append(':');
		Palette.start(output, p.logger());
		JsonKeyValuesFormatter.appendString(output, event.loggerName());
		Palette.end(output, p.logger());
		output.append(separator);
		appendKey(output, "thread", json5);
		output.append(':');
		Palette.start(output, p.thread());
		JsonKeyValuesFormatter.appendString(output, event.threadName());
		Palette.end(output, p.thread());
		output.append(separator);
		appendKey(output, "msg", json5);
		output.append(':');
		int start = output.length();
		event.formattedMessage(output);
		JsonKeyValuesFormatter.quoteInPlace(output, start);
		var kvs = event.keyValues();
		int keyValuesStart = output.length();
		switch (keyValues) {
			case MERGED -> {
				int colliding = kvs.forEach(flat, 0, output);
				if (colliding > 0) {
					appendNested(output, kvs, nestedColliding);
				}
			}
			case NESTED -> appendNested(output, kvs, nestedAll);
			case NONE -> {
			}
		}
		if (output.length() > keyValuesStart && !p.keyValues().isEmpty()) {
			// Key values start with the separator from msg, left uncolored.
			int colorStart = output.indexOf(separator, keyValuesStart) == keyValuesStart
					? keyValuesStart + separator.length() : keyValuesStart;
			output.insert(colorStart, Ansi.start(p.keyValues()));
			output.append(Ansi.RESET);
		}
		var throwable = event.throwableOrNull();
		if (throwable != null) {
			output.append(separator);
			appendKey(output, "error", json5);
			output.append(':');
			JsonKeyValuesFormatter.appendString(output, throwable.toString());
			output.append(separator);
			appendKey(output, "stacktrace", json5);
			output.append(':');
			start = output.length();
			ThrowableFormatter.appendThrowable(output, throwable);
			int end = output.length();
			while (end > start && (output.charAt(end - 1) == '\n' || output.charAt(end - 1) == '\r')) {
				end--;
			}
			output.setLength(end);
			JsonKeyValuesFormatter.quoteInPlace(output, start);
		}
		output.append(close);
	}

	private void appendNested(StringBuilder output, KeyValues kvs, Members members) {
		output.append(separator);
		appendKey(output, KEY_VALUES_FIELD, json5);
		output.append(":{");
		kvs.forEach(members, 0, output);
		output.append('}');
	}

	static void appendKey(StringBuilder output, String key, boolean json5) {
		if (json5 && JsonKeyValuesFormatter.isIdentifier(key)) {
			output.append(key);
		}
		else {
			JsonKeyValuesFormatter.appendString(output, key);
		}
	}

	/*
	 * Writes key values as object members. FLAT writes those not named like an event
	 * field, each after a comma since the event's own fields come first, and returns how
	 * many it skipped; the nested modes write comma separated members and return how many
	 * they wrote.
	 */
	record Members(boolean json5, String separator, Mode mode) implements KeyValuesConsumer<StringBuilder> {

		enum Mode {

			FLAT, NESTED_ALL, NESTED_COLLIDING

		}

		@Override
		public int accept(KeyValues values, String key, @Nullable String value, int count, StringBuilder output) {
			boolean field = FIELDS.contains(key);
			switch (mode) {
				case FLAT -> {
					if (field) {
						return count + 1;
					}
					output.append(separator);
					appendMember(output, key, value);
					return count;
				}
				case NESTED_COLLIDING -> {
					if (!field) {
						return count;
					}
				}
				case NESTED_ALL -> {
				}
			}
			if (count > 0) {
				output.append(separator);
			}
			appendMember(output, key, value);
			return count + 1;
		}

		private void appendMember(StringBuilder output, String key, @Nullable String value) {
			appendKey(output, key, json5);
			output.append(':');
			if (value == null) {
				output.append("null");
			}
			else {
				JsonKeyValuesFormatter.appendString(output, value);
			}
		}

	}

}
