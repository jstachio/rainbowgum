package io.jstach.rainbowgum.pattern.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.annotation.CaseChanging;
import io.jstach.rainbowgum.pattern.format.DefaultPatternRegistry.CustomConverterKey;
import io.jstach.rainbowgum.pattern.format.PatternFormatterFactory.KeywordFactory;

/**
 * Pattern registry to register custom keywords.
 */
public sealed interface PatternRegistry {

	/**
	 * Gets the formatter factory for the pattern key.
	 * @param key keyword.
	 * @return factory or null.
	 */
	public @Nullable PatternFormatterFactory getOrNull(String key);

	/**
	 * Registers a pattern key with a formatter factory. You may replace the built-in
	 * pattern formatters with {@link KeywordKey} or {@link ColorKey}s or create a new key
	 * with {@link PatternKey#of(String)}.
	 * @param key pattern keys.
	 * @param factory factory to create formatters from pattern keywords.
	 */
	public void register(PatternKey key, PatternFormatterFactory factory);

	/**
	 * Convenience function for {@link #register(PatternKey, PatternFormatterFactory)} as
	 * keyword factory is a lambda.
	 * @param key pattern keys.
	 * @param factory factory create formatter from a simple keyword.
	 */
	default void keyword(PatternKey key, KeywordFactory factory) {
		register(key, factory);
	}

	/**
	 * Creates a pattern registry with the defaults.
	 * @return default registry.
	 */
	public static PatternRegistry of() {
		return DefaultPatternRegistry.registerDefaults(new DefaultPatternRegistry());
	}

	/**
	 * Pattern keywords.
	 *
	 * @see KeywordKey
	 * @see ColorKey
	 */
	public sealed interface PatternKey {

		/**
		 * Keyword names.
		 * @return keywords should not be empty.
		 */
		List<String> aliases();

		/**
		 * Creates a pattern key of one key name.
		 * @param key key name.
		 * @return pattern key.
		 */
		public static PatternKey of(String key) {
			return new CustomConverterKey(List.of(key));
		}

		/**
		 * Creates a pattern key of one key and aliases.
		 * @param key key name.
		 * @param aliases other names.
		 * @return pattern key.
		 */
		public static PatternKey of(String key, String... aliases) {
			List<String> keys = new ArrayList<>();
			keys.add(Objects.requireNonNull(key));
			for (var a : aliases) {
				keys.add(Objects.requireNonNull(a));
			}
			return new CustomConverterKey(List.copyOf(keys));
		}

	}

	/**
	 * Built-in supported pattern keys.
	 */
	@CaseChanging
	enum KeywordKey implements PatternKey {

		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#date">Date keywords</a>
		 */
		DATE("d", "date"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#micros">Micros keywords</a>
		 */
		MICROS("ms", "micros"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#file">File</a>
		 */
		FILE("f", "file"),
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#line">Line no</a>
		 */
		LINE("L", "line"),
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#class">Calling class</a>
		 */
		CLASS("C", "class"),
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#line">Line no</a>
		 */
		METHOD("M", "method"),
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#relative">Thread
		 * keywords</a>
		 */
		THREAD("t", "thread"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#level">Level keywords</a>
		 */
		LEVEL("level", "le", "p"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#logger">Logger keywords</a>
		 */
		LOGGER("lo", "logger", "c"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#message">Message
		 * keywords</a>
		 */
		MESSAGE("m", "msg", "message"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#mdc">MDC keywords</a>
		 */
		MDC("X", "mdc"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#ex">Throwable keywords</a>
		 */
		THROWABLE("ex", "exception", "throwable"), //
		/**
		 * <a href="https://logback.qos.ch/manual/layouts.html#newline">Newline
		 * keywords</a>
		 */
		LINESEP("n");

		private final List<String> aliases;

		private KeywordKey(List<String> aliases) {
			this.aliases = aliases;
		}

		private KeywordKey(String... others) {
			this(List.of(others));
		}

		public List<String> aliases() {
			return this.aliases;
		}

	}

	/**
	 * Built-in supported color pattern keys.
	 */
	@CaseChanging
	enum ColorKey implements PatternKey {

		/**
		 * ANSI black color
		 */
		BLACK("black"), //
		/**
		 * ANSI red color
		 */
		RED("red"), //
		/**
		 * ANSI green color
		 */
		GREEN("green"), //
		/**
		 * ANSI yellow color
		 */
		YELLOW("yellow"), //
		/**
		 * ANSI blue color
		 */
		BLUE("blue"), //
		/**
		 * ANSI magenta color
		 */
		MAGENTA("magenta"), //
		/**
		 * ANSI cyan color
		 */
		CYAN("cyan"), //
		/**
		 * ANSI white color
		 */
		WHITE("white"), //
		/**
		 * ANSI gray color
		 */
		GRAY("gray"), //
		/**
		 * ANSI boldRed color
		 */
		BOLD_RED("boldRed"), //
		/**
		 * ANSI boldGreen color
		 */
		BOLD_GREEN("boldGreen"), //
		/**
		 * ANSI boldYellow color
		 */
		BOLD_YELLOW("boldYellow"), //
		/**
		 * ANSI boldBlue color
		 */
		BOLD_BLUE("boldBlue"), //
		/**
		 * ANSI boldMagenta color
		 */
		BOLD_MAGENTA("boldMagenta"), //
		/**
		 * ANSI boldCyan color
		 */
		BOLD_CYAN("boldCyan"), //
		/**
		 * ANSI boldWhite color
		 */
		BOLD_WHITE("boldWhite"), //
		/**
		 * A special color composite key that will highlight based on logger level.
		 */
		HIGHLIGHT("highlight"), //
		;

		private final List<String> aliases;

		private ColorKey(List<String> aliases) {
			this.aliases = aliases;
		}

		private ColorKey(String... others) {
			this(List.of(others));
		}

		public List<String> aliases() {
			return this.aliases;
		}

	}

}

final class DefaultPatternRegistry implements PatternRegistry {

	private final Map<String, PatternKey> keys = new HashMap<>();

	private final Map<PatternKey, PatternFormatterFactory> converters = new HashMap<>();

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	public DefaultPatternRegistry() {
		super();
	}

	public void register(PatternKey key, PatternFormatterFactory factory) {
		lock.writeLock().lock();
		try {
			var f = converters.get(key);
			if (f == null) {
				for (var a : key.aliases()) {
					if (keys.containsKey(a)) {
						throw new IllegalArgumentException("Key already registered: " + a);
					}
					keys.put(a, key);
				}
			}
			converters.put(key, factory);
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	public @Nullable PatternFormatterFactory getOrNull(String key) {
		lock.readLock().lock();
		try {
			var ck = keys.get(key);
			if (ck == null)
				return null;
			return converters.get(ck);
		}
		finally {
			lock.readLock().unlock();
		}
	}

	static PatternRegistry registerDefaults(PatternRegistry registry) {
		for (KeywordKey c : KeywordKey.values()) {
			KeywordFactory keyword = switch (c) {
				case MESSAGE -> KeywordFactory.of(LogFormatter.builder().message().build());
				case DATE -> StandardKeywordFactory.DATE;
				case LEVEL -> KeywordFactory.of(LogFormatter.LevelFormatter.of());
				case LINESEP -> (fc, n) -> new LogFormatter.StaticFormatter(fc.lineSeparator());
				case LOGGER -> StandardKeywordFactory.LOGGER;
				case MDC -> StandardKeywordFactory.MDC;
				case MICROS -> KeywordFactory.of(LogFormatter.TimestampFormatter.ofMicros());
				case THREAD -> KeywordFactory.of(LogFormatter.builder().threadName().build());
				case THROWABLE -> KeywordFactory.of(LogFormatter.ThrowableFormatter.of());
				case CLASS -> KeywordFactory.of(CallerInfoFormatter.CLASS);
				case FILE -> KeywordFactory.of(CallerInfoFormatter.FILE);
				case LINE -> KeywordFactory.of(CallerInfoFormatter.LINE);
				case METHOD -> KeywordFactory.of(CallerInfoFormatter.METHOD);
				default -> throw new IllegalArgumentException("Unexpected value: " + c);
			};

			registry.register(c, keyword);
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
			registry.register(c, factory);
		}

		registry.register(BareKey.BARE, BareCompositeFactory.BARE);
		return registry;
	}

	record CustomConverterKey(List<String> aliases) implements PatternKey {

	}

	enum BareKey implements PatternKey {

		BARE() {
			@Override
			public List<String> aliases() {
				return List.of("BARE");
			}

		}

	}

	// DEFAULT_CONVERTER_MAP.putAll(Parser.DEFAULT_COMPOSITE_CONVERTER_MAP);
	//
	//
	// DEFAULT_CONVERTER_MAP.put("r", RelativeTimeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("relative", RelativeTimeConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(RelativeTimeConverter.class.getName(), "relative");
	//
	//

	//
	// DEFAULT_CONVERTER_MAP.put("C", ClassOfCallerConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("class", ClassOfCallerConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(ClassOfCallerConverter.class.getName(), "class");
	//
	// DEFAULT_CONVERTER_MAP.put("M", MethodOfCallerConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("method", MethodOfCallerConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(MethodOfCallerConverter.class.getName(), "method");
	//
	// DEFAULT_CONVERTER_MAP.put("L", LineOfCallerConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("line", LineOfCallerConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(LineOfCallerConverter.class.getName(), "line");
	//
	// DEFAULT_CONVERTER_MAP.put("F", FileOfCallerConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("file", FileOfCallerConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(FileOfCallerConverter.class.getName(), "file");
	//
	// DEFAULT_CONVERTER_MAP.put("X", MDCConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("mdc", MDCConverter.class.getName());
	//
	// DEFAULT_CONVERTER_MAP.put("rEx",
	// RootCauseFirstThrowableProxyConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("rootException",
	// RootCauseFirstThrowableProxyConverter.class.getName());
	//
	// DEFAULT_CONVERTER_MAP.put("xEx", ExtendedThrowableProxyConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("xException",
	// ExtendedThrowableProxyConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("xThrowable",
	// ExtendedThrowableProxyConverter.class.getName());
	//
	// DEFAULT_CONVERTER_MAP.put("nopex",
	// NopThrowableInformationConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("nopexception",
	// NopThrowableInformationConverter.class.getName());
	//
	// DEFAULT_CONVERTER_MAP.put("cn", ContextNameConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("contextName", ContextNameConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(ContextNameConverter.class.getName(),
	// "contextName");
	//
	// DEFAULT_CONVERTER_MAP.put("caller", CallerDataConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(CallerDataConverter.class.getName(), "caller");
	//
	// DEFAULT_CONVERTER_MAP.put("marker", MarkerConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(MarkerConverter.class.getName(), "marker");
	//
	// DEFAULT_CONVERTER_MAP.put("kvp", KeyValuePairConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(KeyValuePairConverter.class.getName(), "kvp");
	//
	// DEFAULT_CONVERTER_MAP.put("property", PropertyConverter.class.getName());
	//
	//

	//
	// DEFAULT_CONVERTER_MAP.put("lsn", LocalSequenceNumberConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(LocalSequenceNumberConverter.class.getName(),
	// "lsn");
	//
	// DEFAULT_CONVERTER_MAP.put("sn", SequenceNumberConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("sequenceNumber",
	// SequenceNumberConverter.class.getName());
	// CONVERTER_CLASS_TO_KEY_MAP.put(SequenceNumberConverter.class.getName(),
	// "sequenceNumber");
	//
	// DEFAULT_CONVERTER_MAP.put("prefix", PrefixCompositeConverter.class.getName());

}
