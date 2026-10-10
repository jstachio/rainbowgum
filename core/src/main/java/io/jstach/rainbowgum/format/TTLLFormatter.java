package io.jstach.rainbowgum.format;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
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
	 * <code>logging.encoder.console.keyValues=json5</code>; keyValues accepts
	 * <code>json</code> and <code>json5</code> as well as <code>true</code> (logfmt) and
	 * <code>false</code> (none). The timestamp property also accepts a DateTimeFormatter
	 * pattern in UTC. The color property decides whether to color and the theme property
	 * which colors; when neither is set the rainbowgum theme is used if the console
	 * supports ANSI.
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
	 * @param serviceRegistry where themes chosen by name in properties are looked up.
	 * @return formatter.
	 */
	@LogConfigurable(name = "TTLLFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_TIMESTAMP") @LogConfigurable.ConvertParameter("convertTimestamp") LogFormatter timestamp,
			@LogConfigurable.DefaultParameter("DEFAULT_THREAD") @LogConfigurable.ConvertParameter("convertThread") LogFormatter thread,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("convertLevel") LogFormatter level,
			@LogConfigurable.DefaultParameter("DEFAULT_LOGGER") @LogConfigurable.ConvertParameter("convertLogger") LogFormatter logger,
			@LogConfigurable.DefaultParameter("DEFAULT_KEY_VALUES") @LogConfigurable.ConvertParameter("convertKeyValues") LogFormatter keyValues,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorMode color,
			@LogConfigurable.ConvertParameter("convertTheme") TTLL.@Nullable ColorTheme theme,
			@LogConfigurable.ConvertParameter("convertKeyValuesWhenEmpty") TTLL.@Nullable KeyValuesWhenEmpty keyValuesWhenEmpty,
			@LogConfigurable.PassThroughParameter @Nullable ServiceRegistry serviceRegistry) {
		var mode = color == null ? TTLL.ColorMode.DEFAULT : color;
		var resolvedTheme = PaletteColorTheme.resolve(theme, serviceRegistry);
		var palette = Palette.of(mode, resolvedTheme, AnsiSupport::isAnsiSupported);
		var b = LogFormatter.builder();
		boolean empty = true;
		empty = part(b, palette.color(palette.timestamp(), timestamp), empty);
		if (!thread.isNoop()) {
			var bracketed = LogFormatter.builder().text("[").add(thread).text("]").build();
			empty = part(b, palette.color(palette.thread(), bracketed), empty);
		}
		empty = part(b, palette.level(level), empty);
		empty = part(b, palette.color(palette.logger(), logger), empty);
		if (!keyValues.isNoop()) {
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
			/*
			 * Key values can write nothing, so they carry their own leading space rather
			 * than being a part, and the " - " below only follows text actually written.
			 */
			b.add(palette.keyValues(keyValues, writeEmpty, !empty));
		}
		var line = LogFormatter.builder().add(new LinePrefixFormatter(b.build())).message().newline().throwable();
		return new TTLLFormatter(line.build(), describe(mode, resolvedTheme, palette));
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
		return !error.isEmpty();
	}

	LogFormatter color(String code, LogFormatter formatter) {
		if (code.isEmpty() || formatter.isNoop()) {
			return formatter;
		}
		return LogFormatter.builder().text(Ansi.start(code)).add(formatter).text(Ansi.RESET).build();
	}

	LogFormatter level(LogFormatter level) {
		if (error.isEmpty() || level.isNoop()) {
			return level;
		}
		return new LevelColorFormatter(level, this);
	}

	LogFormatter keyValues(LogFormatter keyValues, boolean writeEmpty, boolean afterPart) {
		String space = afterPart ? " " : "";
		if (keyValues().isEmpty()) {
			return BracedKeyValuesFormatter.ofTTLL(keyValues, space + "{", "}", writeEmpty);
		}
		return BracedKeyValuesFormatter.ofTTLL(keyValues, space + Ansi.start(keyValues()) + "{", "}" + Ansi.RESET,
				writeEmpty);
	}

	String levelCode(java.lang.System.Logger.Level level) {
		return switch (level) {
			case ERROR -> error;
			case WARNING -> warn;
			case INFO -> info;
			case DEBUG -> debug;
			case TRACE -> trace;
			default -> "39";
		};
	}

}

/*
 * Colors the level with the palette's color for the event's level.
 */
record LevelColorFormatter(LogFormatter level, Palette palette) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		output.append(Ansi.start(palette.levelCode(event.level())));
		level.format(output, event);
		output.append(Ansi.RESET);
	}

}
