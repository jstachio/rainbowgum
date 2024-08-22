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
import static io.jstach.rainbowgum.pattern.format.ANSIConstants.FAINT;

import java.lang.System.Logger.Level;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.pattern.Padding;
import io.jstach.rainbowgum.pattern.PatternKeyword;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.CompositeFactory;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.KeywordFactory;

/**
 * Creates formatters from pattern keywords and are registered to a
 * {@link PatternRegistry}.
 */
public sealed interface PatternFormatterFactory {

	/**
	 * A composite formatter factory expects possible children. For example
	 * {@code %keyword(child) }.
	 */
	public non-sealed interface CompositeFactory extends PatternFormatterFactory {

		/**
		 * Creates a formatter from a keyword.
		 * @param config config to help create formatter.
		 * @param node current keyword info.
		 * @param child the embedded child in the keyword or null if non is provided.
		 * @return formatter.
		 */
		public LogFormatter create(PatternConfig config, PatternKeyword node, @Nullable LogFormatter child);

	}

	/**
	 * A keyword formatter factory expects keywords and option list. It is usually in the
	 * format of {@code %keyword}.
	 */
	public non-sealed interface KeywordFactory extends PatternFormatterFactory {

		/**
		 * Creates a formatter from a keyword.
		 * @param config config to help create formatter.
		 * @param node current keyword info.
		 * @return formatter.
		 */
		public LogFormatter create(PatternConfig config, PatternKeyword node);

		/**
		 * Creates a keyword factory from a formatter that only cares about padding info.
		 * @param formatter existing formatter.
		 * @return factory.
		 */
		static PatternFormatterFactory.KeywordFactory of(LogFormatter formatter) {
			return (c, n) -> PadFormatter.of(formatter, n.padding());
		}

	}

}

enum CallerInfoFormatter implements EventFormatter {

	METHOD() {

		@Override
		protected void format(StringBuilder output, Caller info) {
			output.append(info.methodName());
		}

	},
	CLASS {
		@Override
		protected void format(StringBuilder output, Caller info) {
			output.append(info.className());

		}
	},
	FILE {
		@Override
		protected void format(StringBuilder output, Caller info) {
			output.append(info.fileNameOrNull());

		}
	},
	LINE {
		@Override
		protected void format(StringBuilder output, Caller info) {
			output.append(info.lineNumber());

		}
	};

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var info = event.callerOrNull();
		if (info != null) {
			format(output, info);
		}

	}

	protected abstract void format(StringBuilder output, Caller caller);

}

enum StandardKeywordFactory implements KeywordFactory {

	DATE() {
		@Override
		protected LogFormatter _create(PatternConfig config, PatternKeyword node) {
			String pattern = node.opt(0, ISO8601_PATTERN);
			if (pattern.equals(ISO8601_STR)) {
				pattern = ISO8601_PATTERN;
			}
			ZoneId zoneId = node.opt(1, config.zoneId(), s -> ZoneId.of(s));
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
		protected LogFormatter _create(PatternConfig config, PatternKeyword node) {
			Integer length = node.optOrNull(0, Integer::parseInt);
			if (length == null) {
				return LogFormatter.builder().loggerName().build();
			}
			return new LoggerFormatter(Abbreviator.of(length));
		}

	},
	MDC() {

		@Override
		protected LogFormatter _create(PatternConfig config, PatternKeyword node) {
			String key = node.optOrNull(0);
			if (key == null) {
				return LogFormatter.builder().keyValues().build();
			}
			String[] s = key.split(":-");
			String k;
			String fallback;
			if (s.length == 2) {
				k = s[0];
				fallback = s[1];
			}
			else {
				k = key;
				fallback = null;
			}
			return LogFormatter.builder().keyValue(k, fallback).build();

		}

	},
	PROPERTY() {

		@Override
		protected LogFormatter _create(PatternConfig config, PatternKeyword node) {
			String key = node.optOrNull(0);
			if (key == null) {
				return LogFormatter.builder().text("Property_HAS_NO_KEY").build();
			}
			var f = config.propertyFunction();
			var v = f.apply(key);
			if (v == null) {
				return LogFormatter.noop();
			}
			return LogFormatter.builder().text(v).build();
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
	public LogFormatter create(PatternConfig config, PatternKeyword node) {
		var formatter = _create(config, node);
		var formatInfo = node.padding();
		return PadFormatter.of(formatter, formatInfo);
	}

	protected abstract LogFormatter _create(PatternConfig config, PatternKeyword node);

	record LoggerFormatter(Abbreviator abbreviator) implements LogFormatter.EventFormatter {

		@Override
		public void format(StringBuilder output, LogEvent event) {
			String out = abbreviator.abbreviate(event.loggerName());
			output.append(out);

		}

	}

}

enum BareCompositeFactory implements CompositeFactory {

	BARE() {
		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node, @Nullable LogFormatter child) {
			if (child == null) {
				child = new LogFormatter.StaticFormatter("");
			}
			return PadFormatter.of(child, node.padding());
		}
	};

}

final class ANSIConstants {

	final static String ESC_START = "\033[";

	final static String ESC_END = "m";

	final static String BOLD = "1;";

	final static String FAINT = "2;";

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

record HighlightFormatter(@Nullable LogFormatter child) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		/*
		 * TODO should a null child be a noop? Need to see what logback does for compat.
		 */
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

record ClrLevelFormatter(@Nullable LogFormatter child) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		/*
		 * TODO should a null child be a noop? Need to see what logback does for compat.
		 */
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
			case ERROR -> RED_FG;
			case WARNING -> YELLOW_FG;
			case INFO -> GREEN_FG;
			case DEBUG -> GREEN_FG;
			case TRACE -> GREEN_FG;
			default -> DEFAULT_FG;
		};
	}

}

record ClrStaticFormatter(@Nullable LogFormatter child, String code) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		output.append(ANSIConstants.ESC_START);
		output.append(code);
		output.append(ANSIConstants.ESC_END);
		if (child != null) {
			child.format(output, event);
		}
		output.append(ANSIConstants.SET_DEFAULT_COLOR);
	}

}

enum HighlightCompositeFactory implements CompositeFactory {

	HIGHTLIGHT() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node, @Nullable LogFormatter child) {
			if (config.ansiDisabled()) {
				if (child == null) {
					return LogFormatter.noop();
				}
				return child;
			}
			return new HighlightFormatter(child);
		}

	},

	CLR() {

		@Override
		public LogFormatter create(PatternConfig config, PatternKeyword node, @Nullable LogFormatter child) {
			if (config.ansiDisabled()) {
				if (child == null) {
					return LogFormatter.noop();
				}
				return child;
			}
			String color = node.optOrNull(0, HighlightCompositeFactory::parseColor);
			if (color == null) {
				return new ClrLevelFormatter(child);
			}
			return new ClrStaticFormatter(child, color);
		}

	};

	static String parseColor(String color) {
		color = color.toLowerCase(Locale.ROOT);
		return switch (color) {
			case "blue" -> BLACK_FG;
			case "cyan" -> CYAN_FG;
			case "faint" -> FAINT + DEFAULT_FG;
			case "green" -> GREEN_FG;
			case "magenta" -> MAGENTA_FG;
			case "red" -> RED_FG;
			case "yellow" -> YELLOW_FG;
			default -> throw new IllegalArgumentException("Bad color:" + color);
		};
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

	public LogFormatter create(PatternConfig config, PatternKeyword node, @Nullable LogFormatter child) {
		return create(node, child, !config.ansiDisabled());
	}

	LogFormatter create(PatternKeyword node, @Nullable LogFormatter child, boolean ansi) {
		var b = LogFormatter.builder();
		if (ansi) {
			b.text(ANSIConstants.ESC_START);
			b.text(fg);
			b.text(ANSIConstants.ESC_END);
		}
		if (child != null) {
			b.add(child);
		}
		if (ansi) {
			b.text(ANSIConstants.SET_DEFAULT_COLOR);
		}
		return b.build();

	}

	public String alias() {
		return name;
	}

}

record PadFormatter(Padding padding, LogFormatter formatter) implements LogFormatter.EventFormatter {

	public static LogFormatter of(LogFormatter formatter, @Nullable Padding padding) {
		if (padding == null) {
			return formatter;
		}
		if (formatter instanceof LogFormatter.StaticFormatter sf) {
			StringBuilder b = new StringBuilder();
			padding.format(b, sf.content());
			return new LogFormatter.StaticFormatter(b.toString());
		}
		return new PadFormatter(padding, formatter);
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		/*
		 * TODO revisit formatting without using a temp buffer
		 */
		StringBuilder buf = new StringBuilder();
		formatter.format(buf, event);
		padding.format(output, buf);
	}

}