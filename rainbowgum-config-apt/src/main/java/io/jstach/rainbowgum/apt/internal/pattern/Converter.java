package io.jstach.rainbowgum.apt.internal.pattern;

import java.util.List;

import static io.jstach.rainbowgum.apt.internal.pattern.Converter.FormatterType.*;

public interface Converter {

	// TimestampFormatter, ThrowableFormatter, KeyValuesFormatter, LevelFormatter,
	// NameFormatter, ThreadFormatter

	public enum FormatterType {

		Timestamp, Throwable, KeyValues, Level, Name, Thread, Message, Literal;

	}

	public enum StandardConverter {

		DATE(Timestamp, "d", "date"), MICROS(Timestamp, "ms", "micros"), THREAD(Thread, "t", "thread"),
		LEVEL(Level, "level", "le", "p"), LOGGER(Name, "lo", "logger", "c"), MESSAGE(Message, "m", "msg", "message"),
		MDC(KeyValues, "X", "mdc"), THROWABLE(Throwable, "ex", "exception", "throwable"), LINESEP(Literal, "n"),
		IGNORE(Name, "n");
		;

		private final FormatterType type;

		private final List<String> aliases;

		private StandardConverter(FormatterType type, List<String> aliases) {
			this.type = type;
			this.aliases = aliases;
		}

		private StandardConverter(FormatterType type, String... others) {
			this(type, List.of(others));
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
	// DEFAULT_CONVERTER_MAP.put("black", BlackCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("red", RedCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("green", GreenCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("yellow", YellowCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("blue", BlueCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("magenta", MagentaCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("cyan", CyanCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("white", WhiteCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("gray", GrayCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldRed", BoldRedCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldGreen",
	// BoldGreenCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldYellow",
	// BoldYellowCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldBlue", BoldBlueCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldMagenta",
	// BoldMagentaCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldCyan", BoldCyanCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("boldWhite",
	// BoldWhiteCompositeConverter.class.getName());
	// DEFAULT_CONVERTER_MAP.put("highlight",
	// HighlightingCompositeConverter.class.getName());
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
