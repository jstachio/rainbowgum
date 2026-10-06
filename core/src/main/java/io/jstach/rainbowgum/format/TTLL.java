package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * The choices for each part of the TTLL (Time, Thread, Level, Logger) format, the default
 * text format, configured with {@link TTLLFormatterBuilder} or properties of the
 * {@value AbstractStandardEventFormatter#SCHEMA} encoder. Property values are the
 * lowercase constant names, for example: <pre>
 * logging.encoder.console.timestamp=iso
 * logging.encoder.console.keyValues=logfmt
 * </pre> The defaults produce the classic layout
 * <code>12:00:00.123 [main] INFO  com.example.App - hello</code>. With key values shown
 * they appear in braces after the logger name, and the braces are left out when an event
 * has none:
 * <code>12:00:00.123 [main] INFO  com.example.App {requestId=42} - hello</code>.
 */
public sealed interface TTLL permits TTLLFormatter {

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

	}

	/**
	 * Thread part, written in square brackets.
	 */
	@CaseChanging
	enum ThreadFormat {

		/**
		 * Thread name. The default.
		 */
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

	}

	/**
	 * Level part.
	 */
	@CaseChanging
	enum LevelFormat {

		/**
		 * Upper case, right padded to five characters so messages line up. The default.
		 */
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

	}

	/**
	 * Logger name part.
	 */
	@CaseChanging
	enum LoggerFormat {

		/**
		 * Full logger name. The default.
		 */
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

	}

	/**
	 * Key values part, written in braces after the logger name and left out when an event
	 * has no key values.
	 */
	@CaseChanging
	enum KeyValuesFormat {

		/**
		 * Key values are not shown. The default.
		 */
		NONE {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.noop();
			}
		},
		/**
		 * <a href="https://brandur.org/logfmt">logfmt</a>:
		 * <code>requestId=42 user="Ada Lovelace"</code>.
		 */
		LOGFMT {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().logfmtKeyValues().build();
			}
		},
		/**
		 * Percent encoded like a URI query:
		 * <code>requestId=42&amp;user=Ada%20Lovelace</code>.
		 */
		PERCENT {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().encodedKeyValues().build();
			}
		},
		/**
		 * Logback's <code>%X</code> style: <code>requestId=42, user=Ada Lovelace</code>.
		 */
		LOGBACK {
			@Override
			public LogFormatter formatter() {
				return LogFormatter.builder().keyValues().build();
			}
		};

		/**
		 * The formatter for this choice.
		 * @return formatter.
		 */
		public abstract LogFormatter formatter();

	}

	/**
	 * ANSI color theme. When not set, {@link #RAINBOWGUM} is used if the console supports
	 * ANSI (see {@link AnsiSupport#isAnsiSupported()}) and {@link #OFF} otherwise; the
	 * default encoder of outputs that are not a console is always {@link #OFF} unless
	 * set. As a property <code>true</code> and <code>false</code> are aliases for
	 * <code>rainbowgum</code> and <code>off</code>.
	 */
	@CaseChanging
	enum ColorTheme {

		/**
		 * No color. Alias <code>false</code>.
		 */
		OFF,
		/**
		 * The Rainbow Gum colors, the same as the pattern encoder's default: cyan time,
		 * faint thread and key values, level highlighted (error and warn bold red, info
		 * bold blue) and magenta logger name. Alias <code>true</code>.
		 */
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
				case "true" -> RAINBOWGUM;
				case "false" -> OFF;
				default -> io.jstach.rainbowgum.LogProperty.enumValue(ColorTheme.class, value, "true", "false");
			};
		}

	}

}
