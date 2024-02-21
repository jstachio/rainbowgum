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
import io.jstach.rainbowgum.pattern.FormatInfo;
import io.jstach.rainbowgum.pattern.Node;
import io.jstach.rainbowgum.pattern.Node.CompositeNode;
import io.jstach.rainbowgum.pattern.Node.End;
import io.jstach.rainbowgum.pattern.Node.FormattingNode;
import io.jstach.rainbowgum.pattern.Node.KeywordNode;
import io.jstach.rainbowgum.pattern.Node.LiteralNode;
import io.jstach.rainbowgum.pattern.Parser;
import io.jstach.rainbowgum.pattern.format.Compiler.FormatterFactory.CompositeFactory;
import io.jstach.rainbowgum.pattern.format.Compiler.FormatterFactory.KeywordFactory;
import io.jstach.rainbowgum.pattern.format.ConverterRegistry.BareKey;
import io.jstach.rainbowgum.pattern.format.ConverterRegistry.ColorKey;
import io.jstach.rainbowgum.pattern.format.ConverterRegistry.KeywordKey;

class Compiler {

	private final ConverterRegistry<FormatterFactory> registry;

	Compiler() {
		this.registry = registerDefaults(new ConverterRegistry<Compiler.FormatterFactory>());
	}

	static ConverterRegistry<FormatterFactory> registerDefaults(ConverterRegistry<FormatterFactory> registry) {
		for (KeywordKey c : KeywordKey.values()) {
			KeywordFactory keyword = switch (c) {
				case MESSAGE -> KeywordFactory.of(LogFormatter.MessageFormatter.of());
				case DATE -> StandardKeywordFactory.DATE;
				case LEVEL -> KeywordFactory.of(LogFormatter.LevelFormatter.of());
				case LINESEP -> KeywordFactory.of(new LogFormatter.StaticFormatter("\n"));
				case LOGGER -> KeywordFactory.of(LogFormatter.NameFormatter.of());
				case MDC -> KeywordFactory.of(LogFormatter.KeyValuesFormatter.of());
				case MICROS -> KeywordFactory.of(LogFormatter.TimestampFormatter.ofMicros());
				case THREAD -> KeywordFactory.of(LogFormatter.ThreadFormatter.of());
				case THROWABLE -> KeywordFactory.of(LogFormatter.ThrowableFormatter.of());
			};

			registry.put(c, keyword);
		}

		for (ColorKey c : ColorKey.values()) {
			var factory = switch (c) {
				case RED -> ColorCompositeFactory.RED;
				case BLACK -> ColorCompositeFactory.BLACK;
				case BLUE -> ColorCompositeFactory.BLUE;
				case CYAN -> ColorCompositeFactory.CYAN;
				case GRAY -> ColorCompositeFactory.GRAY;
				case GREEN -> ColorCompositeFactory.GREEN;
				case MAGENTA -> ColorCompositeFactory.MAGENTA;
				case WHITE -> ColorCompositeFactory.WHITE;
				case YELLOW -> ColorCompositeFactory.YELLOW;
				case BOLD_BLUE -> ColorCompositeFactory.BOLD_BLUE;
				case BOLD_CYAN -> ColorCompositeFactory.BOLD_CYAN;
				case BOLD_GREEN -> ColorCompositeFactory.BOLD_GREEN;
				case BOLD_MAGENTA -> ColorCompositeFactory.BOLD_MAGENTA;
				case BOLD_RED -> ColorCompositeFactory.BOLD_RED;
				case BOLD_WHITE -> ColorCompositeFactory.BOLD_WHITE;
				case BOLD_YELLOW -> ColorCompositeFactory.BOLD_YELLOW;
				case HIGHLIGHT -> HightlightCompositeFactory.HIGHTLIGHT;
			};
			registry.put(c, factory);
		}

		registry.put(BareKey.BARE, BareCompositeFactory.BARE);
		return registry;
	}

	public sealed interface FormatterFactory {

		public LogFormatter create(Compiler compiler, FormattingNode node);

		public non-sealed interface CompositeFactory extends FormatterFactory {

			default LogFormatter create(Compiler compiler, FormattingNode node) {
				var n = switch (node) {
					case CompositeNode cn -> cn.childNode();
					case KeywordNode kn -> null;
				};
				LogFormatter child = n == null ? null : compiler.compile(n);
				return create(node, child);
			}

			public LogFormatter create(FormattingNode node, @Nullable LogFormatter child);

		}

		public non-sealed interface KeywordFactory extends FormatterFactory {

			default LogFormatter create(Compiler compiler, FormattingNode node) {
				return create(node);
			}

			public LogFormatter create(FormattingNode node);

			public static KeywordFactory of(LogFormatter formatter) {
				return (n) -> PadFormatter.of(formatter, n.formatInfo());
			}

		}

	}

	public static final String ISO8601_STR = "ISO8601";

	public static final String ISO8601_PATTERN = "yyyy-MM-dd HH:mm:ss,SSS";

	enum StandardKeywordFactory implements KeywordFactory {

		DATE() {
			@Override
			protected LogFormatter _create(FormattingNode node) {
				String pattern = node.opt(0, ISO8601_PATTERN);
				if (pattern.equals(ISO8601_STR)) {
					pattern = ISO8601_PATTERN;
				}
				ZoneId zoneId = node.opt(1, ZoneId.systemDefault(), s -> ZoneId.of(s));
				@Nullable
				Locale locale = node.opt(2, s -> Locale.forLanguageTag(s));
				var dtf = DateTimeFormatter.ofPattern(pattern).withZone(zoneId);
				if (locale != null) {
					dtf = dtf.withLocale(locale);
				}
				return LogFormatter.TimestampFormatter.of(dtf);
			}
		};

		// MICROS, //
		// THREAD, //
		// LEVEL, //
		// LOGGER, //
		// MESSAGE, //
		// MDC, //
		// THROWABLE, //
		// LINESEP;

		@Override
		public LogFormatter create(FormattingNode node) {
			var formatter = _create(node);
			var formatInfo = node.formatInfo();
			return PadFormatter.of(formatter, formatInfo);
		}

		protected abstract LogFormatter _create(FormattingNode node);

	}

	enum BareCompositeFactory implements CompositeFactory {

		BARE() {
			@Override
			public LogFormatter create(FormattingNode node, @Nullable LogFormatter child) {
				if (child == null) {
					child = new LogFormatter.StaticFormatter("");
				}
				return PadFormatter.of(child, node.formatInfo());
			}
		};

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
			public LogFormatter create(FormattingNode node, @Nullable LogFormatter child) {
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

		public LogFormatter create(FormattingNode node, @Nullable LogFormatter child) {
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

	public LogFormatter compile(String pattern) {
		Parser p = new Parser(pattern);
		return compile(p.parse());
	}

	LogFormatter compile(Node start) {
		var b = LogFormatter.builder();
		if (start == Node.end()) {
			return b.flatten();
		}

		for (Node n = start; n != Node.end();) {
			n = switch (n) {
				case End e -> {
					yield e;
				}
				case LiteralNode ln -> {
					b.text(ln.value());
					yield ln.next();
				}
				case FormattingNode fn -> {
					FormatterFactory f = registry.getOrNull(fn.keyword());
					if (f == null) {
						throw new IllegalStateException("Missing formater for key: " + fn.keyword());
					}
					b.add(f.create(this, fn));
					yield fn.next();
				}
			};
		}
		return b.flatten();
	}

	public record PadFormatter(FormatInfo formatInfo, LogFormatter formatter) implements LogFormatter.EventFormatter {

		public static LogFormatter of(LogFormatter formatter, @Nullable FormatInfo formatInfo) {
			if (formatInfo == null) {
				return formatter;
			}
			if (formatter instanceof LogFormatter.StaticFormatter sf) {
				StringBuilder b = new StringBuilder();
				formatInfo.format(b, sf.content());
				return new LogFormatter.StaticFormatter(b.toString());
			}
			return new PadFormatter(formatInfo, formatter);
		}

		@Override
		public void format(StringBuilder output, LogEvent event) {
			StringBuilder buf = new StringBuilder();
			formatter.format(buf, event);
			formatInfo.format(output, buf);
		}

	}

}
