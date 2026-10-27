package io.jstach.rainbowgum.format;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.PercentCodec;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * The TTLL format assembled from its parts. Package private: the public API is the
 * generated TTLLFormatterBuilder, the ttll encoder scheme, and the TTLL choice enums.
 */
final class TTLLFormatter implements TTLL, LogFormatter.EventFormatter, LogReporter.Reportable {

	/** {@link io.jstach.rainbowgum.format.TTLL.TimestampFormat#TTLL ttll} */
	static final LogFormatter DEFAULT_TIMESTAMP = TTLL.TimestampFormat.TTLL.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.ThreadFormat#NAME name} */
	static final LogFormatter DEFAULT_THREAD = TTLL.ThreadFormat.NAME.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.LevelFormat#PADDED padded} */
	static final LogFormatter DEFAULT_LEVEL = TTLL.LevelFormat.PADDED.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.LoggerFormat#FULL full} */
	static final LogFormatter DEFAULT_LOGGER = TTLL.LoggerFormat.FULL.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.KeyValuesFormat#PERCENT percent} */
	static final LogFormatter DEFAULT_KEY_VALUES = TTLL.KeyValuesFormat.PERCENT.formatter();

	/** timestamp, thread, level, logger, keyValues */
	static final List<TTLL.Part> DEFAULT_ORDER = List.of(TTLL.Part.values());

	private final LogFormatter formatter;

	private final String description;

	private TTLLFormatter(LogFormatter formatter, String description) {
		this.formatter = formatter;
		this.description = description;
	}

	/**
	 * The TTLL (Time, Thread, Level, Logger) text format, the default encoder, with each
	 * part configurable, see {@link TTLL}.
	 * <p>
	 * The defaults produce <code>12:00:00.123 [main] INFO  com.example.App - hello</code>
	 * followed by any stack trace. Parts set to none are left out along with their
	 * separating space. As properties of the encoder, each part is one of the lowercase
	 * names of its {@link TTLL} enum, for example
	 * <code>logging.appender.console.encoder.ttll.keyValues=json5</code>; keyValues
	 * accepts <code>json</code> and <code>json5</code> as well as <code>true</code>
	 * (percent encoding) and <code>false</code> (none). The timestamp property also
	 * accepts a DateTimeFormatter pattern in UTC. The color property decides whether to
	 * color and the theme property which colors; when neither is set the rainbowgum theme
	 * is used if the console supports ANSI.
	 * @param name encoder name, used for property lookup.
	 * @param timestamp time formatter, see {@link TTLL.TimestampFormat}.
	 * @param thread thread formatter in square brackets, see {@link TTLL.ThreadFormat}.
	 * @param level level formatter, see {@link TTLL.LevelFormat}.
	 * @param logger logger name formatter, see {@link TTLL.LoggerFormat}.
	 * @param keyValues key values formatter in braces, see {@link TTLL.KeyValuesFormat}.
	 * @param color whether to color, see {@link TTLL.ColorMode}.
	 * @param theme ANSI color theme, see {@link TTLL.ColorTheme}.
	 * @param keyValuesWhenEmpty braces for no key values, see
	 * {@link TTLL.KeyValuesWhenEmpty}.
	 * @param order the parts in the order they are written, see {@link TTLL.Part}.
	 * @param serviceRegistry where themes chosen by name in properties are looked up.
	 * @return formatter.
	 */
	@LogConfigurable(name = "TTLLFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX + "ttll.")
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_TIMESTAMP") @LogConfigurable.ConvertParameter("convertTimestamp") LogFormatter timestamp,
			@LogConfigurable.DefaultParameter("DEFAULT_THREAD") @LogConfigurable.ConvertParameter("convertThread") LogFormatter thread,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("convertLevel") LogFormatter level,
			@LogConfigurable.DefaultParameter("DEFAULT_LOGGER") @LogConfigurable.ConvertParameter("convertLogger") LogFormatter logger,
			@LogConfigurable.DefaultParameter("DEFAULT_KEY_VALUES") @LogConfigurable.ConvertParameter("convertKeyValues") LogFormatter keyValues,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorMode color,
			@LogConfigurable.ConvertParameter("convertTheme") TTLL.@Nullable ColorTheme theme,
			@LogConfigurable.ConvertParameter("convertKeyValuesWhenEmpty") TTLL.@Nullable KeyValuesWhenEmpty keyValuesWhenEmpty,
			@LogConfigurable.DefaultParameter("DEFAULT_ORDER") @LogConfigurable.ConvertParameter("convertOrder") List<TTLL.Part> order,
			@LogConfigurable.PassThroughParameter @Nullable ServiceRegistry serviceRegistry) {
		var mode = color == null ? TTLL.ColorMode.DEFAULT : color;
		var resolvedTheme = PaletteColorTheme.resolve(theme, serviceRegistry);
		var palette = Palette.of(mode, resolvedTheme, AnsiSupport::isAnsiSupported);
		var timestampPart = palette.color(palette.timestamp(), KeyValueField.inPlace(timestamp));
		var threadInPlace = KeyValueField.inPlace(thread);
		var threadPart = threadInPlace.isNoop() ? threadInPlace : palette.color(palette.thread(),
				LogFormatter.builder().text("[").add(threadInPlace).text("]").build());
		var levelPart = palette.level(KeyValueField.inPlace(level));
		var loggerPart = palette.color(palette.logger(), KeyValueField.inPlace(logger));
		Function<TTLL.Part, LogFormatter> configured = p -> switch (p) {
			case TIMESTAMP -> timestamp;
			case THREAD -> thread;
			case LEVEL -> level;
			case LOGGER -> logger;
			case KEY_VALUES -> keyValues;
		};
		Function<TTLL.Part, LogFormatter> parts = p -> switch (p) {
			case TIMESTAMP -> timestampPart;
			case THREAD -> threadPart;
			case LEVEL -> levelPart;
			case LOGGER -> loggerPart;
			case KEY_VALUES -> keyValues;
		};
		var seen = EnumSet.noneOf(TTLL.Part.class);
		var fields = new ArrayList<KeyValueField>();
		for (var p : order) {
			if (!seen.add(p)) {
				throw new IllegalArgumentException("order lists " + p.name().toLowerCase(Locale.ROOT)
						+ " more than once: " + order.stream().map(o -> o.name().toLowerCase(Locale.ROOT)).toList());
			}
			if (configured.apply(p) instanceof KeyValueField field) {
				fields.add(field);
			}
		}
		if (!fields.isEmpty() && (keyValues.isNoop() || !seen.contains(TTLL.Part.KEY_VALUES))) {
			throw new IllegalArgumentException("A part set to key_value needs keyValues=percent, json, or json5, "
					+ "and keyValues in order, since it is written among the key values.");
		}
		/*
		 * The parts are added in order to one builder, so the line is one flat list of
		 * formatters whatever the order.
		 */
		var b = LogFormatter.builder();
		boolean empty = true;
		for (int i = 0; i < order.size(); i++) {
			var p = order.get(i);
			if (p != TTLL.Part.KEY_VALUES) {
				empty = part(b, parts.apply(p), empty);
			}
			else if (!fields.isEmpty()) {
				empty = part(b, palette.keyValueFields(KeyValueField.prepend(fields, keyValues)), empty);
			}
			else if (!keyValues.isNoop()) {
				boolean partAfter = order.subList(i + 1, order.size())
					.stream()
					.anyMatch(after -> !parts.apply(after).isNoop());
				b.add(keyValues(palette, keyValues, keyValuesWhenEmpty, !empty, partAfter));
			}
		}
		var line = LogFormatter.builder().add(new LinePrefixFormatter(b.build())).message().newline().throwable();
		return new TTLLFormatter(line.build(), describe(mode, resolvedTheme, palette));
	}

	/*
	 * Key values can write nothing, so rather than being a part they carry their own
	 * space: before them after another part, or after them when they come first and a
	 * part follows. The " - " before the message only follows text actually written.
	 */
	private static LogFormatter keyValues(Palette palette, LogFormatter keyValues,
			TTLL.@Nullable KeyValuesWhenEmpty keyValuesWhenEmpty, boolean partBefore, boolean partAfter) {
		boolean json = keyValues instanceof JsonKeyValuesFormatter j
				&& j.syntax() == KeyValuesFormatterBuilder.Format.JSON;
		var whenEmpty = keyValuesWhenEmpty == null ? TTLL.KeyValuesWhenEmpty.AUTO : keyValuesWhenEmpty;
		if (json && whenEmpty == TTLL.KeyValuesWhenEmpty.OMIT) {
			throw new IllegalArgumentException("keyValuesWhenEmpty=omit cannot be used with keyValues=json, "
					+ "since JSON readers expect an object on every line. Use keyValues=json5 or keyValues=percent, "
					+ "or keyValuesWhenEmpty=auto or show.");
		}
		boolean writeEmpty = whenEmpty == TTLL.KeyValuesWhenEmpty.SHOW
				|| (whenEmpty == TTLL.KeyValuesWhenEmpty.AUTO && json);
		return palette.keyValues(keyValues, writeEmpty, partBefore ? " " : "", !partBefore && partAfter ? " " : "");
	}

	/*
	 * What the reporter shows: the color settings and whether they resulted in color,
	 * since "why is my console not colored" is the question it answers. The parts are
	 * already formatters here and their choices are not tracked.
	 */
	static String describe(TTLL.ColorMode mode, TTLL.@Nullable ColorTheme theme, Palette palette) {
		String themeName = theme != null ? theme.name() : mode == TTLL.ColorMode.OFF || mode == TTLL.ColorMode.DETECT
				? "none" : TTLL.ColorTheme.RAINBOWGUM.name();
		return TTLL.SCHEMA + " color=" + mode.name().toLowerCase(Locale.ROOT) + " theme="
				+ themeName.toLowerCase(Locale.ROOT) + " colored=" + palette.colored();
	}

	@Override
	public void report(Appendable out) throws IOException {
		out.append(description);
	}

	private static boolean part(LogFormatter.Builder b, LogFormatter part, boolean empty) {
		if (part.isNoop()) {
			return empty;
		}
		if (!empty) {
			b.text(" ");
		}
		b.add(part);
		return false;
	}

	static LogFormatter convertTimestamp(String value) {
		try {
			return TTLL.TimestampFormat.parse(value).formatter();
		}
		catch (IllegalArgumentException notAChoice) {
			// not a choice name or alias, so a DateTimeFormatter pattern
		}
		try {
			return TimestampFormatter.of(DateTimeFormatter.ofPattern(value).withZone(ZoneOffset.UTC));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("'" + value
					+ "' is neither a timestamp format (ttll, iso, none, true, false, default) nor a valid DateTimeFormatter pattern: "
					+ e.getMessage(), e);
		}
	}

	static LogFormatter convertThread(String value) {
		return TTLL.ThreadFormat.parse(value).formatter();
	}

	static LogFormatter convertLevel(String value) {
		return TTLL.LevelFormat.parse(value).formatter();
	}

	static LogFormatter convertLogger(String value) {
		return TTLL.LoggerFormat.parse(value).formatter();
	}

	static TTLL.ColorMode convertColor(String value) {
		return TTLL.ColorMode.parse(value);
	}

	static TTLL.KeyValuesWhenEmpty convertKeyValuesWhenEmpty(String value) {
		return TTLL.KeyValuesWhenEmpty.parse(value);
	}

	static TTLL.ColorTheme convertTheme(String value) {
		return PaletteColorTheme.parse(value);
	}

	static List<TTLL.Part> convertOrder(String value) {
		return LogProperties.parseList(value).stream().map(TTLL.Part::parse).toList();
	}

	static LogFormatter convertKeyValues(String value) {
		return TTLL.KeyValuesFormat.parse(value).formatter();
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		formatter.format(output, event);
	}

}

/*
 * The last dot separated component of the logger name.
 */
enum ShortLoggerNameFormatter implements LogFormatter.EventFormatter {

	INSTANCE;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String name = event.loggerName();
		output.append(name, name.lastIndexOf('.') + 1, name.length());
	}

}

/*
 * Everything before the message, followed by " - " only if it wrote something.
 */
record LinePrefixFormatter(LogFormatter prefix) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		prefix.format(output, event);
		if (output.length() > start) {
			output.append(" - ");
		}
	}

}

/*
 * A part chosen with KEY_VALUE: TTLLFormatter.of writes its value among the key values
 * instead of in its place, so on its own it writes nothing.
 */
record KeyValueField(String key, LogFormatter value) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
	}

	/*
	 * What to write in the part's place: nothing when it is a field.
	 */
	static LogFormatter inPlace(LogFormatter part) {
		return part instanceof KeyValueField ? LogFormatter.noop() : part;
	}

	/*
	 * The fields followed by the event's key values, in the syntax of the key values
	 * formatter. Keys and separators are text, so the result is one flat list of
	 * formatters and nothing loops over the fields per event.
	 */
	static LogFormatter prepend(List<KeyValueField> fields, LogFormatter keyValues) {
		var syntax = switch (keyValues) {
			case PercentKeyValuesFormatter f -> KeyValuesFormatterBuilder.Format.PERCENT;
			case JsonKeyValuesFormatter f when !f.braces() -> f.syntax();
			case LogfmtKeyValuesFormatter f -> KeyValuesFormatterBuilder.Format.LOGFMT;
			default -> throw new IllegalArgumentException("A part set to key_value needs key values from "
					+ "TTLL.KeyValuesFormat or KeyValuesFormatterBuilder, not " + keyValues);
		};
		String separator = switch (syntax) {
			case PERCENT -> "&";
			case JSON, JSON5 -> ",";
			case LOGFMT -> " ";
		};
		var b = LogFormatter.builder();
		String before = "";
		for (var field : fields) {
			b.text(before + key(field.key(), syntax));
			b.add(new KeyValueFieldValue(field.value(), syntax));
			before = separator;
		}
		b.add(new FollowingKeyValues(separator, keyValues));
		return b.build();
	}

	/*
	 * The key and what separates it from its value.
	 */
	static String key(String key, KeyValuesFormatterBuilder.Format syntax) {
		var sb = new StringBuilder();
		switch (syntax) {
			case PERCENT -> {
				PercentCodec.encode(sb, key, StandardCharsets.UTF_8);
				sb.append('=');
			}
			case JSON -> {
				JsonKeyValuesFormatter.appendString(sb, key);
				sb.append(':');
			}
			case JSON5 -> {
				if (JsonKeyValuesFormatter.isIdentifier(key)) {
					sb.append(key);
				}
				else {
					JsonKeyValuesFormatter.appendString(sb, key);
				}
				sb.append(':');
			}
			case LOGFMT -> {
				LogfmtKeyValuesFormatter.appendKey(sb, key);
				sb.append('=');
			}
		}
		return sb.toString();
	}

}

/*
 * A key value field's value, encoded in place in the key values syntax.
 */
record KeyValueFieldValue(LogFormatter value,
		KeyValuesFormatterBuilder.Format syntax) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		value.format(output, event);
		switch (syntax) {
			case PERCENT -> percentEncodeInPlace(output, start);
			case JSON, JSON5 -> JsonKeyValuesFormatter.quoteInPlace(output, start);
			case LOGFMT -> LogfmtKeyValuesFormatter.quoteInPlace(output, start);
		}
	}

	/*
	 * Copies nothing when the value is all unreserved characters, the common case.
	 */
	static void percentEncodeInPlace(StringBuilder output, int start) {
		for (int i = start; i < output.length(); i++) {
			char c = output.charAt(i);
			boolean unreserved = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-'
					|| c == '.' || c == '_' || c == '~';
			if (!unreserved) {
				String raw = output.substring(start);
				output.setLength(start);
				PercentCodec.encode(output, raw, StandardCharsets.UTF_8);
				return;
			}
		}
	}

}

/*
 * The event's key values after the fields, with the separator only if there are any.
 */
record FollowingKeyValues(String separator, LogFormatter keyValues) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		output.append(separator);
		keyValues.format(output, event);
		if (output.length() == start + separator.length()) {
			output.setLength(start);
		}
	}

}

/*
 * Writes key values between an open and close text, and when there are none writes the
 * empty braces or leaves them out; the standard event formatter always leaves them out,
 * with their leading space.
 */
record BracedKeyValuesFormatter(LogFormatter keyValuesFormatter, String open, String close,
		boolean writeEmpty) implements LogFormatter.EventFormatter {

	/*
	 * TTLL: "{...}", with a leading space when it follows another part.
	 */
	static BracedKeyValuesFormatter ofTTLL(LogFormatter keyValuesFormatter, String open, String close,
			boolean writeEmpty) {
		return new BracedKeyValuesFormatter(keyValuesFormatter, open, close, writeEmpty);
	}

	/*
	 * Standard event formatter: " {...}", left out entirely when there are no key values.
	 */
	static BracedKeyValuesFormatter ofStandard(LogFormatter keyValuesFormatter) {
		return new BracedKeyValuesFormatter(keyValuesFormatter, " {", "}", false);
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		output.append(open);
		keyValuesFormatter.format(output, event);
		if (!writeEmpty && output.length() == start + open.length()) {
			output.setLength(start);
		}
		else {
			output.append(close);
		}
	}

}

/*
 * ANSI escape codes, the same sequences the pattern encoder writes.
 */
final class Ansi {

	static final String RESET = "\033[0;39m";

	private Ansi() {
	}

	static String start(String code) {
		return "\033[" + code + "m";
	}

}

/*
 * A theme with its colors: the built in themes and those made with
 * TTLL.ColorTheme.builder.
 */
record PaletteColorTheme(String name, Palette palette) implements TTLL.ColorTheme {

	static final PaletteColorTheme RAINBOWGUM = new PaletteColorTheme("rainbowgum", Palette.RAINBOWGUM);

	static final PaletteColorTheme SPRING = new PaletteColorTheme("spring", Palette.SPRING);

	static final PaletteColorTheme ONE_DARK = new PaletteColorTheme("one_dark", Palette.ONE_DARK);

	static final PaletteColorTheme DARCULA = new PaletteColorTheme("darcula", Palette.DARCULA);

	static final List<PaletteColorTheme> BUILT_IN = List.of(RAINBOWGUM, SPRING, ONE_DARK, DARCULA);

	private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");

	/*
	 * A built in theme, or a name to find among registered themes when the formatter is
	 * created, since converting the property cannot see the service registry.
	 */
	static TTLL.ColorTheme parse(String value) {
		String name = value.strip().toLowerCase(Locale.ROOT);
		if (name.equals("default")) {
			return RAINBOWGUM;
		}
		for (var theme : BUILT_IN) {
			if (theme.name().equals(name)) {
				return theme;
			}
		}
		if (!NAME.matcher(name).matches()) {
			throw invalid(value, List.of());
		}
		return new NamedColorTheme(name);
	}

	/*
	 * The theme for a name from properties, looked up among themes registered under their
	 * name.
	 */
	static TTLL.@Nullable ColorTheme resolve(TTLL.@Nullable ColorTheme theme, @Nullable ServiceRegistry registry) {
		if (!(theme instanceof NamedColorTheme named)) {
			return theme;
		}
		var registered = new ArrayList<String>();
		if (registry != null) {
			var found = registry.findOrNull(TTLL.ColorTheme.class, named.name());
			if (found instanceof PaletteColorTheme) {
				return found;
			}
			registry.forEach(TTLL.ColorTheme.class, (n, t) -> registered.add(n));
		}
		throw invalid(named.name(), registered);
	}

	static String requireThemeName(String name) {
		if (!NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Color theme name '" + name
					+ "' should be lowercase letters, digits, and underscores, starting with a letter.");
		}
		if (name.equals("default") || BUILT_IN.stream().anyMatch(t -> t.name().equals(name))) {
			throw new IllegalArgumentException("Color theme name '" + name + "' is taken by a built in theme.");
		}
		return name;
	}

	static Palette paletteOf(TTLL.ColorTheme theme) {
		return switch (theme) {
			case PaletteColorTheme t -> t.palette();
			case NamedColorTheme t ->
				throw new IllegalArgumentException("Color theme '" + t.name() + "' has not been looked up yet.");
		};
	}

	/*
	 * Same message as when this was an enum, with registered theme names after the built
	 * in ones.
	 */
	private static IllegalArgumentException invalid(String value, List<String> registered) {
		var valid = new ArrayList<String>();
		for (var theme : BUILT_IN) {
			valid.add("'" + theme.name() + "'");
		}
		valid.add("'default'");
		registered.stream().sorted().forEach(n -> valid.add("'" + n + "'"));
		return new IllegalArgumentException("'" + value + "' is not a valid value for "
				+ TTLL.ColorTheme.class.getCanonicalName() + ". Valid values: " + String.join(", ", valid));
	}

}

/*
 * A theme name from properties that is not built in, looked up when the formatter is
 * created.
 */
record NamedColorTheme(String name) implements TTLL.ColorTheme {
}

/*
 * The ANSI codes a color theme uses for each part of the TTLL layout and for each level.
 * Adding a theme is adding a palette. The codes are the same sequences the pattern
 * encoder writes for the equivalent pattern. An empty code means no color.
 */
record Palette(String timestamp, String thread, String logger, String keyValues, String error, String warn, String info,
		String debug, String trace) {

	static final Palette OFF = new Palette("", "", "", "", "", "", "", "", "");

	/*
	 * The pattern encoder's default: cyan time, faint thread, %highlight level with the
	 * default pattern's options and magenta logger name.
	 */
	static final Palette RAINBOWGUM = new Palette("36", "2;39", "35", "2;39", "1;31", "1;31", "1;34", "39", "39");

	/*
	 * Spring Boot's console colors: faint time and thread, %clr level colors (error red,
	 * warn yellow, the rest green) and cyan logger name.
	 */
	static final Palette SPRING = new Palette("2;39", "2;39", "36", "2;39", "31", "33", "32", "32", "32");

	/*
	 * Atom One Dark in 24 bit color, from the One Dark IntelliJ and Eclipse themes: cyan
	 * time, comment gray thread and key values, keyword magenta logger name, and levels
	 * bold red, yellow and blue (error, warn, info) or foreground gray.
	 */
	static final Palette ONE_DARK = new Palette(rgb("56b6c2"), rgb("969896"), rgb("c678dd"), rgb("969896"),
			"1;" + rgb("e06c75"), "1;" + rgb("e5c07b"), "1;" + rgb("61afef"), rgb("abb2bf"), rgb("abb2bf"));

	/*
	 * IntelliJ Darcula in 24 bit color: number blue time, comment gray thread and key
	 * values, keyword orange logger name, and levels bold in Darcula's log console red,
	 * yellow and string green (error, warn, info) or foreground gray.
	 */
	static final Palette DARCULA = new Palette(rgb("6897bb"), rgb("808080"), rgb("cc7832"), rgb("808080"),
			"1;" + rgb("ff6b68"), "1;" + rgb("bbb529"), "1;" + rgb("6a8759"), rgb("a9b7c6"), rgb("a9b7c6"));

	/*
	 * The 24 bit foreground color code for a hex RGB value like c678dd.
	 */
	static String rgb(String hex) {
		int value = Integer.parseInt(hex, 16);
		return "38;2;" + (value >> 16 & 0xff) + ";" + (value >> 8 & 0xff) + ";" + (value & 0xff);
	}

	/*
	 * The palette for the color mode and theme. ANSI detection is only asked for when the
	 * mode needs it.
	 */
	static Palette of(TTLL.ColorMode mode, TTLL.@Nullable ColorTheme theme, BooleanSupplier ansi) {
		var selected = switch (mode) {
			case OFF -> null;
			case DEFAULT -> ansi.getAsBoolean() ? theme == null ? TTLL.ColorTheme.RAINBOWGUM : theme : null;
			case DETECT -> theme != null && ansi.getAsBoolean() ? theme : null;
			case FORCE -> theme == null ? TTLL.ColorTheme.RAINBOWGUM : theme;
		};
		return selected == null ? OFF : of(selected);
	}

	static Palette of(TTLL.ColorTheme theme) {
		return PaletteColorTheme.paletteOf(theme);
	}

	boolean colored() {
		return !timestamp.isEmpty() || !thread.isEmpty() || !logger.isEmpty() || !keyValues.isEmpty()
				|| levelsColored();
	}

	private boolean levelsColored() {
		return !error.isEmpty() || !warn.isEmpty() || !info.isEmpty() || !debug.isEmpty() || !trace.isEmpty();
	}

	LogFormatter color(String code, LogFormatter formatter) {
		if (code.isEmpty() || formatter.isNoop()) {
			return formatter;
		}
		return LogFormatter.builder().text(Ansi.start(code)).add(formatter).text(Ansi.RESET).build();
	}

	LogFormatter level(LogFormatter level) {
		if (!levelsColored() || level.isNoop()) {
			return level;
		}
		return new LevelColorFormatter(level, this);
	}

	LogFormatter keyValues(LogFormatter keyValues, boolean writeEmpty, String before, String after) {
		if (keyValues().isEmpty()) {
			return BracedKeyValuesFormatter.ofTTLL(keyValues, before + "{", "}" + after, writeEmpty);
		}
		return BracedKeyValuesFormatter.ofTTLL(keyValues, before + Ansi.start(keyValues()) + "{",
				"}" + Ansi.RESET + after, writeEmpty);
	}

	/*
	 * Key values that always write something, so they are a part and their braces are
	 * plain text.
	 */
	LogFormatter keyValueFields(LogFormatter keyValues) {
		if (keyValues().isEmpty()) {
			return LogFormatter.builder().text("{").add(keyValues).text("}").build();
		}
		return LogFormatter.builder().text(Ansi.start(keyValues()) + "{").add(keyValues).text("}" + Ansi.RESET).build();
	}

	/*
	 * The level's color, or none when this palette does not color.
	 */
	String levelColor(java.lang.System.Logger.Level level) {
		return switch (level) {
			case ERROR -> error;
			case WARNING -> warn;
			case INFO -> info;
			case DEBUG -> debug;
			case TRACE -> trace;
			default -> levelsColored() ? "39" : "";
		};
	}

	/*
	 * Starts and ends a value colored with code, writing nothing for an empty code. Used
	 * by formatters that color values rather than whole parts.
	 */
	static void start(StringBuilder output, String code) {
		if (!code.isEmpty()) {
			output.append(Ansi.start(code));
		}
	}

	static void end(StringBuilder output, String code) {
		if (!code.isEmpty()) {
			output.append(Ansi.RESET);
		}
	}

	/*
	 * Removes the spaces a padding level formatter wrote after start and returns how
	 * many, so they can be written after a closing quote or color instead.
	 */
	static int removeTrailingSpaces(StringBuilder output, int start) {
		int end = output.length();
		int i = end;
		while (i > start && output.charAt(i - 1) == ' ') {
			i--;
		}
		output.setLength(i);
		return end - i;
	}

	static void spaces(StringBuilder output, int count) {
		for (int i = 0; i < count; i++) {
			output.append(' ');
		}
	}

}

/*
 * Colors the level with the palette's color for the event's level.
 */
record LevelColorFormatter(LogFormatter level, Palette palette) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String code = palette.levelColor(event.level());
		Palette.start(output, code);
		level.format(output, event);
		Palette.end(output, code);
	}

}
