package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * The choices for each part of the TTLL (Time, Thread, Level, Logger) format, the default
 * text format, configured with {@link TTLLFormatterBuilder} or properties of the
 * {@value #SCHEMA} encoder. Property values are the lowercase constant names, for
 * example: <pre>
 * logging.encoder.ttll.console.timestamp=iso
 * logging.encoder.ttll.console.keyValues=json5
 * </pre> The defaults produce the classic layout with the event's key values, percent
 * encoded, in braces after the logger name:
 * <code>12:00:00.123 [main] INFO  com.example.App {requestId=42} - hello</code>. When an
 * event has no key values the braces are left out, or written as <code>{}</code>,
 * according to {@link KeyValuesWhenEmpty}, and {@link KeyValuesFormat#NONE} leaves the
 * key values out entirely.
 * <p>
 * Every part also accepts <code>default</code>, which restores its default without naming
 * it, and <code>true</code> and <code>false</code> to show or hide it; the
 * {@link io.jstach.rainbowgum.annotation.EnumAlias} on each constant lists them.
 */
public sealed interface TTLL permits TTLLFormatter {

	/**
	 * URI scheme of the TTLL encoder, the default encoder when no other is configured.
	 */
	String SCHEMA = "ttll";

	/**
	 * Time part. A property value that is not one of these names is used as a
	 * {@link java.time.format.DateTimeFormatter} pattern in UTC, like slf4j-simple's
	 * <code>dateTimeFormat</code>.
	 */
	@CaseChanging
	enum TimestampFormat {

		/**
		 * <code>HH:mm:ss.SSS</code> in UTC. The default.
		 */
		@EnumAlias({ "default", "true" })
		TTLL {
			@Override
			public LogFormatter formatter() {
				return TimestampFormatter.of();
			}
		},
		/**
		 * ISO 8601 UTC instant with milliseconds, <code>2026-10-05T12:00:00.123Z</code>.
		 */
		ISO {
			@Override
			public LogFormatter formatter() {
				return TimestampFormatter.ofISO();
			}
		},
		/**
		 * No time.
		 */
		@EnumAlias("false")
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		};

		/**
		 * The formatter for this choice.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

		static TimestampFormat parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default", "true" -> TTLL;
				case "false" -> NONE;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(TimestampFormat.class, value, "true", "false",
						"default");
			};
		}

	}

	/**
	 * Thread part, written in square brackets.
	 */
	@CaseChanging
	enum ThreadFormat {

		/**
		 * Thread name. The default.
		 */
		@EnumAlias({ "default", "true" })
		NAME {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().threadName().build();
			}
		},
		/**
		 * Thread id.
		 */
		ID {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().threadId().build();
			}
		},
		/**
		 * No thread.
		 */
		@EnumAlias("false")
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		};

		/**
		 * The formatter for this choice.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

		static ThreadFormat parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default", "true" -> NAME;
				case "false" -> NONE;
				default ->
					io.jstach.rainbowgum.LogProperty.enumValue(ThreadFormat.class, value, "true", "false", "default");
			};
		}

	}

	/**
	 * Level part.
	 */
	@CaseChanging
	enum LevelFormat {

		/**
		 * Upper case, right padded to five characters so messages line up. The default.
		 */
		@EnumAlias({ "default", "true" })
		PADDED {
			@Override
			public LogFormatter formatter() {
				return LevelFormatter.ofRightPadded();
			}
		},
		/**
		 * Upper case without padding.
		 */
		PLAIN {
			@Override
			public LogFormatter formatter() {
				return LevelFormatter.of();
			}
		},
		/**
		 * Upper case in square brackets, like slf4j-simple's
		 * <code>levelInBrackets</code>: <code>[INFO]</code>.
		 */
		BRACKETED {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().text("[").add(LevelFormatter.of()).text("]").build();
			}
		},
		/**
		 * No level.
		 */
		@EnumAlias("false")
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		};

		/**
		 * The formatter for this choice.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

		static LevelFormat parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default", "true" -> PADDED;
				case "false" -> NONE;
				default ->
					io.jstach.rainbowgum.LogProperty.enumValue(LevelFormat.class, value, "true", "false", "default");
			};
		}

	}

	/**
	 * Logger name part.
	 */
	@CaseChanging
	enum LoggerFormat {

		/**
		 * Full logger name. The default.
		 */
		@EnumAlias({ "default", "true" })
		FULL {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().loggerName().build();
			}
		},
		/**
		 * Only the last component of the name, like slf4j-simple's
		 * <code>showShortLogName</code>: <code>App</code> for
		 * <code>com.example.App</code>.
		 */
		SHORT {
			@Override
			public LogFormatter formatter() {
				return ShortLoggerNameFormatter.INSTANCE;
			}
		},
		/**
		 * No logger name.
		 */
		@EnumAlias("false")
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		};

		/**
		 * The formatter for this choice.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

		static LoggerFormat parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default", "true" -> FULL;
				case "false" -> NONE;
				default ->
					io.jstach.rainbowgum.LogProperty.enumValue(LoggerFormat.class, value, "true", "false", "default");
			};
		}

	}

	/**
	 * Key values part, written in braces after the logger name; see
	 * {@link KeyValuesWhenEmpty} for events with no key values. {@link #NONE} leaves out
	 * the key values and their braces.
	 */
	@CaseChanging
	enum KeyValuesFormat {

		/**
		 * Key values and their braces are left out.
		 */
		@EnumAlias("false")
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		},
		/**
		 * Percent encoded like a URI query:
		 * <code>requestId=42&amp;user=Ada%20Lovelace</code>. The default.
		 */
		@EnumAlias({ "true", "default" })
		PERCENT {
			@Override
			public LogFormatter formatter() {
				return new KeyValuesFormatterBuilder().format(KeyValuesFormatterBuilder.Format.PERCENT).build();
			}
		},
		/**
		 * JSON: <code>{"requestId":"42","user":"Ada Lovelace"}</code>. Null values are
		 * written as {@code null}.
		 */
		JSON {
			@Override
			public LogFormatter formatter() {
				return new KeyValuesFormatterBuilder().format(KeyValuesFormatterBuilder.Format.JSON).buildUnbraced();
			}
		},
		/**
		 * JSON5, with valid identifier keys left unquoted:
		 * <code>{requestId:"42",user:"Ada Lovelace"}</code>. Null values are written as
		 * {@code null}.
		 */
		JSON5 {
			@Override
			public LogFormatter formatter() {
				return new KeyValuesFormatterBuilder().format(KeyValuesFormatterBuilder.Format.JSON5).buildUnbraced();
			}
		};

		/**
		 * The formatter for this choice, without the enclosing braces supplied by TTLL.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

		static KeyValuesFormat parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "true", "default" -> PERCENT;
				case "false" -> NONE;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(KeyValuesFormat.class, value, "true", "false",
						"default");
			};
		}

	}

	/**
	 * Whether the key values braces are written when an event has no key values.
	 */
	@CaseChanging
	enum KeyValuesWhenEmpty {

		/**
		 * Left out for percent encoding and JSON5; <code>{}</code> for JSON, since
		 * whatever reads JSON key values expects an object on every line. The default.
		 */
		@EnumAlias("default")
		AUTO,
		/**
		 * Left out. Not allowed with {@link KeyValuesFormat#JSON}, whose readers expect
		 * an object on every line.
		 */
		@EnumAlias("false")
		OMIT,
		/**
		 * Always written, <code>{}</code> when there are no key values.
		 */
		@EnumAlias("true")
		SHOW;

		static KeyValuesWhenEmpty parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default" -> AUTO;
				case "false" -> OMIT;
				case "true" -> SHOW;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(KeyValuesWhenEmpty.class, value, "true", "false",
						"default");
			};
		}

	}

	/**
	 * Whether to color, see {@link ColorTheme} for which colors. Color detection uses
	 * {@link AnsiSupport#isAnsiSupported()}, which only knows about the console, so the
	 * default encoder of outputs that are not a console is {@link #OFF} unless set. As a
	 * property <code>true</code> and <code>false</code> are aliases for
	 * <code>default</code> and <code>off</code>.
	 */
	@CaseChanging
	enum ColorMode {

		/**
		 * No color.
		 */
		@EnumAlias("false")
		OFF,
		/**
		 * The theme, or {@link ColorTheme#RAINBOWGUM} when no theme is set, if ANSI is
		 * detected. The default.
		 */
		@EnumAlias("true")
		DEFAULT,
		/**
		 * The theme if one is set and ANSI is detected; no color without a theme.
		 */
		DETECT,
		/**
		 * The theme, or {@link ColorTheme#RAINBOWGUM} when no theme is set, regardless of
		 * ANSI detection.
		 */
		FORCE;

		static ColorMode parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "true" -> DEFAULT;
				case "false" -> OFF;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(ColorMode.class, value, "true", "false");
			};
		}

	}

	/**
	 * ANSI color theme, the colors used when {@link ColorMode} decides to color.
	 */
	@CaseChanging
	enum ColorTheme {

		/**
		 * The Rainbow Gum colors, the same as the pattern encoder's default: cyan time,
		 * faint thread and key values, level highlighted (error and warn bold red, info
		 * bold blue) and magenta logger name. Used when no theme is set.
		 */
		@EnumAlias("default")
		RAINBOWGUM,
		/**
		 * Spring Boot's console colors: faint time and thread, level colored by severity
		 * (error red, warn yellow, the rest green) and cyan logger name.
		 */
		SPRING,
		/**
		 * Atom One Dark's colors in 24 bit color, so they show the same in any terminal
		 * that supports it: cyan time, gray thread and key values, magenta logger name,
		 * and level bold red, yellow, or blue (error, warn, info) with debug and trace
		 * gray. Meant for a dark terminal background. Property value
		 * <code>one_dark</code>.
		 */
		ONE_DARK,
		/**
		 * IntelliJ Darcula's colors in 24 bit color: blue time, gray thread and key
		 * values, orange logger name, and level bold red, yellow, or green (error, warn,
		 * info) with debug and trace gray. Meant for a dark terminal background.
		 */
		DARCULA;

		static ColorTheme parse(String value) {
			return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
				case "default" -> RAINBOWGUM;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(ColorTheme.class, value, "default");
			};
		}

	}

}
