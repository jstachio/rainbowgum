package io.jstach.rainbowgum.pattern.format;

import static io.jstach.rainbowgum.pattern.format.ANSIConstants.BLACK_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.BLUE_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.BOLD;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.CYAN_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.DEFAULT_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.GREEN_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.MAGENTA_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.RED_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.WHITE_FG;
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.YELLOW_FG;

import java.lang.System.Logger.Level;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.pattern.PadInfo;
import io.jstach.rainbowgum.pattern.PatternKeyword;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.CompositeFactory;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.KeywordFactory;

/**
 * Creates formatters from pattern keywords.
 */
public sealed interface FormatterFactory {

	/**
	 * Config needed to create the formatter. Currently this interface is blank but in the
	 * future properties maybe added to access things like caching.
	 */
	public interface FormatterConfig {

		// public ServiceRegistry serviceRegistry();
		// public LogProperties logProperties();

		/**
		 * Empty config.
		 * @return empty config.
		 */
		public static FormatterConfig empty() {
			return new FormatterConfig() {
			};
		}

	}

	/**
	 * A composite formatter factory expects possible children. For example
	 * {@code %keyword(child) }.
	 */
	public non-sealed interface CompositeFactory extends FormatterFactory {

		/**
		 * Creates a formatter from a keyword.
		 * @param config config to help create formatter.
		 * @param node current keyword info.
		 * @param child the embedded child in the keyword or null if non is provided.
		 * @return formatter.
		 */
		public LogFormatter create(FormatterConfig config, PatternKeyword node, @Nullable LogFormatter child);

	}

	/**
	 * A keyword formatter factory expects keywords and option list..
	 */
	public non-sealed interface KeywordFactory extends FormatterFactory {

		/**
		 * Creates a formatter from a keyword.
		 * @param config config to help create formatter.
		 * @param node current keyword info.
		 * @return formatter.
		 */
		public LogFormatter create(FormatterConfig config, PatternKeyword node);

		/**
		 * Creates a keyword factory from a formatter that only cares about padding info.
		 * @param formatter existing formatter.
		 * @return factory.
		 */
		static FormatterFactory.KeywordFactory of(LogFormatter formatter) {
			return (c, n) -> PadFormatter.of(formatter, n.padInfo());
		}

	}

}

enum StandardKeywordFactory implements KeywordFactory {

	DATE() {
		@Override
		protected LogFormatter _create(FormatterConfig config, PatternKeyword node) {
			String pattern = node.opt(0, ISO8601_PATTERN);
			if (pattern.equals(ISO8601_STR)) {
				pattern = ISO8601_PATTERN;
			}
			ZoneId zoneId = node.opt(1, ZoneId.systemDefault(), s -> ZoneId.of(s));
			@Nullable
			Locale locale = node.optOrNull(2, s -> Locale.forLanguageTag(s));
			var dtf = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
			if (locale != null) {
				dtf = dtf.withLocale(locale);
			}
			return LogFormatter.TimestampFormatter.of(dtf);
		}
	},
	LOGGER() {

		@Override
		protected LogFormatter _create(FormatterConfig config, PatternKeyword node) {
			Integer length = node.optOrNull(0, Integer::parseInt);
			if (length == null) {
				return LogFormatter.NameFormatter.of();
			}
			return new LoggerFormatter(Abbreviator.of(length));
		}

	};

	static final String ISO8601_STR = "ISO8601";

	static final String ISO8601_PATTERN = "yyyy-MM-dd HH:mm:ss,SSS";

	// MICROS, //
	// THREAD, //
	// LEVEL, //
	// LOGGER, //
	// MESSAGE, //
	// MDC, //
	// THROWABLE, //
	// LINESEP;

	@Override
	public LogFormatter create(FormatterConfig config, PatternKeyword node) {
		var formatter = _create(config, node);
		var formatInfo = node.padInfo();
		return PadFormatter.of(formatter, formatInfo);
	}

	protected abstract LogFormatter _create(FormatterConfig config, PatternKeyword node);

	record LoggerFormatter(Abbreviator abbreviator) implements LogFormatter.NameFormatter {

		@Override
		public void formatName(StringBuilder output, String name) {
			String out = abbreviator.abbreviate(name);
			output.append(out);
		}

	}

}

enum BareCompositeFactory implements CompositeFactory {

	BARE() {
		@Override
		public LogFormatter create(FormatterConfig config, PatternKeyword node, @Nullable LogFormatter child) {
			if (child == null) {
				child = new LogFormatter.StaticFormatter("");
			}
			return PadFormatter.of(child, node.padInfo());
		}
	};

}

class ANSIConstants {

	final static String ESC_START = "\033[";

	final static String ESC_END = "m";

	final static String BOLD = "1;";

	final static String RESET = "0;";

	final static String BLACK_FG = "30";

	final static String RED_FG = "31";

	final static String GREEN_FG = "32";

	final static String YELLOW_FG = "33";

	final static String BLUE_FG = "34";

	final static String MAGENTA_FG = "35";

	final static String CYAN_FG = "36";

	final static String WHITE_FG = "37";

	final static String DEFAULT_FG = "39";

	final static String SET_DEFAULT_COLOR = ESC_START + RESET + DEFAULT_FG + ESC_END;

}

record HighlightLogFormatter(LogFormatter child) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var level = event.level();
		String code = levelToANSI(level);
		output.append(ANSIConstants.ESC_START);
		output.append(code);
		output.append(ANSIConstants.ESC_END);
		if (child != null) {
			child.format(output, event);
		}
		output.append(ANSIConstants.SET_DEFAULT_COLOR);
	}

	private static String levelToANSI(Level level) {
		return switch (level) {
			case ERROR -> BOLD + RED_FG;
			case WARNING -> BOLD + RED_FG;
			case INFO -> BLUE_FG;
			default -> DEFAULT_FG;
		};
	}

}

enum HightlightCompositeFactory implements CompositeFactory {

	HIGHTLIGHT() {

		@Override
		public LogFormatter create(FormatterConfig config, PatternKeyword node, @Nullable LogFormatter child) {
			return new HighlightLogFormatter(child);
		}

	}

}

enum ColorCompositeFactory implements CompositeFactory {

	BLACK(BLACK_FG, "black"), //
	RED(RED_FG, "red"), //
	GREEN(GREEN_FG, "green"), //
	YELLOW(YELLOW_FG, "yellow"), //
	BLUE(BLUE_FG, "blue"), //
	MAGENTA(MAGENTA_FG, "magenta"), //
	CYAN(CYAN_FG, "cyan"), //
	WHITE(WHITE_FG, "white"), //
	GRAY(DEFAULT_FG, "gray"), //
	BOLD_RED(BOLD + RED_FG, "boldRed"), //
	BOLD_GREEN(BOLD + GREEN_FG, "boldGreen"), //
	BOLD_YELLOW(BOLD + YELLOW_FG, "boldYellow"), //
	BOLD_BLUE(BOLD + BLUE_FG, "boldBlue"), //
	BOLD_MAGENTA(BOLD + MAGENTA_FG, "boldMagenta"), //
	BOLD_CYAN(BOLD + CYAN_FG, "boldCyan"), //
	BOLD_WHITE(BOLD + WHITE_FG, "boldWhite");

	private final String fg;

	private final String name;

	private ColorCompositeFactory(String fg, String name) {
		this.fg = fg;
		this.name = name;
	}

	public LogFormatter create(FormatterConfig config, PatternKeyword node, @Nullable LogFormatter child) {
		return create(node, child);
	}

	LogFormatter create(PatternKeyword node, @Nullable LogFormatter child) {
		var b = LogFormatter.builder();
		b.text(ANSIConstants.ESC_START);
		b.text(fg);
		b.text(ANSIConstants.ESC_END);
		if (child != null) {
			b.add(child);
		}
		b.text(ANSIConstants.SET_DEFAULT_COLOR);
		return b.flatten();

	}

	public String alias() {
		return name;
	}

}

record PadFormatter(PadInfo padInfo, LogFormatter formatter) implements LogFormatter.EventFormatter {

	public static LogFormatter of(LogFormatter formatter, @Nullable PadInfo padInfo) {
		if (padInfo == null) {
			return formatter;
		}
		if (formatter instanceof LogFormatter.StaticFormatter sf) {
			StringBuilder b = new StringBuilder();
			padInfo.format(b, sf.content());
			return new LogFormatter.StaticFormatter(b.toString());
		}
		return new PadFormatter(padInfo, formatter);
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		StringBuilder buf = new StringBuilder();
		formatter.format(buf, event);
		padInfo.format(output, buf);
	}

}