package io.jstach.rainbowgum.pattern.format;

import static io.jstach.rainbowgum.pattern.format.ConverterRegistry.FormatterType.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.pattern.Node;
import io.jstach.rainbowgum.pattern.Node.NodeKind;

class ConverterRegistry<T> {

	Map<String, ConverterKey> keys = new HashMap<>();

	Map<ConverterKey, T> converters = new HashMap<>();

	public void put(ConverterKey key, T conveter) {
		for (var a : key.aliases()) {
			if (keys.containsKey(a)) {
				throw new IllegalArgumentException("Key already registered: " + a);
			}
			keys.put(a, key);
		}
		converters.put(key, conveter);
	}

	public @Nullable T getOrNull(String key) {
		var ck = keys.get(key);
		if (ck == null)
			return null;
		return converters.get(ck);
	}

	public sealed interface ConverterKey {

		FormatterType type();

		List<String> aliases();

	}

	record CustomConverterKey(FormatterType type, List<String> aliases) implements ConverterKey {

	}

	enum FormatterType {

		Timestamp(NodeKind.KEYWORD), Throwable(NodeKind.KEYWORD), KeyValues(NodeKind.KEYWORD), Level(NodeKind.KEYWORD),
		Name(NodeKind.KEYWORD), Thread(NodeKind.KEYWORD), Message(NodeKind.KEYWORD), Literal(NodeKind.LITERAL),
		Bare(NodeKind.COMPOSITE), Color(NodeKind.COMPOSITE);

		private final Node.NodeKind kind;

		private FormatterType(NodeKind kind) {
			this.kind = kind;
		}

		Node.NodeKind kind() {
			return this.kind;
		}

	}

	enum KeywordKey implements ConverterKey {

		DATE(Timestamp, "d", "date"), //
		MICROS(Timestamp, "ms", "micros"), //
		THREAD(Thread, "t", "thread"), //
		LEVEL(Level, "level", "le", "p"), //
		LOGGER(Name, "lo", "logger", "c"), //
		MESSAGE(Message, "m", "msg", "message"), //
		MDC(KeyValues, "X", "mdc"), //
		THROWABLE(Throwable, "ex", "exception", "throwable"), //
		LINESEP(Literal, "n");

		private final FormatterType type;

		private final List<String> aliases;

		private KeywordKey(FormatterType type, List<String> aliases) {
			this.type = type;
			this.aliases = aliases;
		}

		private KeywordKey(FormatterType type, String... others) {
			this(type, List.of(others));
		}

		public FormatterType type() {
			return this.type;
		}

		public List<String> aliases() {
			return this.aliases;
		}

	}

	enum BareKey implements ConverterKey {

		BARE() {
			@Override
			public FormatterType type() {
				return FormatterType.Bare;
			}

			@Override
			public List<String> aliases() {
				return List.of("BARE");
			}

		}

	}

	enum ColorKey implements ConverterKey {

		BLACK(Color, "black"), //
		RED(Color, "red"), //
		GREEN(Color, "green"), //
		YELLOW(Color, "yellow"), //
		BLUE(Color, "blue"), //
		MAGENTA(Color, "magenta"), //
		CYAN(Color, "cyan"), //
		WHITE(Color, "white"), //
		GRAY(Color, "gray"), //
		BOLD_RED(Color, "boldRed"), //
		BOLD_GREEN(Color, "boldGreen"), //
		BOLD_YELLOW(Color, "boldYellow"), //
		BOLD_BLUE(Color, "boldBlue"), //
		BOLD_MAGENTA(Color, "boldMagenta"), //
		BOLD_CYAN(Color, "boldCyan"), //
		BOLD_WHITE(Color, "boldWhite"), //
		HIGHLIGHT(Color, "highlight"), //
		;

		private final FormatterType type;

		private final List<String> aliases;

		private ColorKey(FormatterType type, List<String> aliases) {
			this.type = type;
			this.aliases = aliases;
		}

		private ColorKey(FormatterType type, String... others) {
			this(type, List.of(others));
		}

		public FormatterType type() {
			return this.type;
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
