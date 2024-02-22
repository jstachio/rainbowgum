package io.jstach.rainbowgum.pattern.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.pattern.format.DefaultPatternRegistry.CustomConverterKey;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.KeywordFactory;

/**
 * Pattern registry to register custom keywords.
 */
public sealed interface PatternRegistry {

	/**
	 * Gets the formatter factory for the pattern key.
	 * @param key keyword.
	 * @return factory or null.
	 */
	public @Nullable FormatterFactory getOrNull(String key);

	/**
	 * Registers a pattern key with a formatter factory.
	 * @param key pattern keys.
	 * @param factory factory to create formatters from pattern keywords.
	 */
	public void register(PatternKey key, FormatterFactory factory);

	/**
	 * Creates a pattern registry with the defaults.
	 * @return default registry.
	 */
	public static PatternRegistry of() {
		return DefaultPatternRegistry.registerDefaults(new DefaultPatternRegistry());
	}

	/**
	 * Pattern keywords.
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

}

final class DefaultPatternRegistry implements PatternRegistry {

	final Map<String, PatternKey> keys = new HashMap<>();

	final Map<PatternKey, FormatterFactory> converters = new HashMap<>();

	public DefaultPatternRegistry() {
		super();
	}

	public void register(PatternKey key, FormatterFactory conveter) {
		for (var a : key.aliases()) {
			if (keys.containsKey(a)) {
				throw new IllegalArgumentException("Key already registered: " + a);
			}
			keys.put(a, key);
		}
		converters.put(key, conveter);
	}

	public @Nullable FormatterFactory getOrNull(String key) {
		var ck = keys.get(key);
		if (ck == null)
			return null;
		return converters.get(ck);
	}

	static PatternRegistry registerDefaults(PatternRegistry registry) {
		for (KeywordKey c : KeywordKey.values()) {
			KeywordFactory keyword = switch (c) {
				case MESSAGE -> KeywordFactory.of(LogFormatter.MessageFormatter.of());
				case DATE -> StandardKeywordFactory.DATE;
				case LEVEL -> KeywordFactory.of(LogFormatter.LevelFormatter.of());
				case LINESEP -> KeywordFactory.of(new LogFormatter.StaticFormatter("\n"));
				case LOGGER -> StandardKeywordFactory.LOGGER;
				case MDC -> KeywordFactory.of(LogFormatter.KeyValuesFormatter.of());
				case MICROS -> KeywordFactory.of(LogFormatter.TimestampFormatter.ofMicros());
				case THREAD -> KeywordFactory.of(LogFormatter.ThreadFormatter.of());
				case THROWABLE -> KeywordFactory.of(LogFormatter.ThrowableFormatter.of());
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

	// enum FormatterType {
	//
	// Timestamp(NodeKind.KEYWORD), Throwable(NodeKind.KEYWORD),
	// KeyValues(NodeKind.KEYWORD), Level(NodeKind.KEYWORD),
	// Name(NodeKind.KEYWORD), Thread(NodeKind.KEYWORD), Message(NodeKind.KEYWORD),
	// Literal(NodeKind.LITERAL),
	// Bare(NodeKind.COMPOSITE), Color(NodeKind.COMPOSITE);
	//
	// private final Node.NodeKind kind;
	//
	// private FormatterType(NodeKind kind) {
	// this.kind = kind;
	// }
	//
	// Node.NodeKind kind() {
	// return this.kind;
	// }
	//
	// }

	enum KeywordKey implements PatternKey {

		DATE("d", "date"), //
		MICROS("ms", "micros"), //
		THREAD("t", "thread"), //
		LEVEL("level", "le", "p"), //
		LOGGER("lo", "logger", "c"), //
		MESSAGE("m", "msg", "message"), //
		MDC("X", "mdc"), //
		THROWABLE("ex", "exception", "throwable"), //
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

	enum BareKey implements PatternKey {

		BARE() {
			@Override
			public List<String> aliases() {
				return List.of("BARE");
			}

		}

	}

	enum ColorKey implements PatternKey {

		BLACK("black"), //
		RED("red"), //
		GREEN("green"), //
		YELLOW("yellow"), //
		BLUE("blue"), //
		MAGENTA("magenta"), //
		CYAN("cyan"), //
		WHITE("white"), //
		GRAY("gray"), //
		BOLD_RED("boldRed"), //
		BOLD_GREEN("boldGreen"), //
		BOLD_YELLOW("boldYellow"), //
		BOLD_BLUE("boldBlue"), //
		BOLD_MAGENTA("boldMagenta"), //
		BOLD_CYAN("boldCyan"), //
		BOLD_WHITE("boldWhite"), //
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
