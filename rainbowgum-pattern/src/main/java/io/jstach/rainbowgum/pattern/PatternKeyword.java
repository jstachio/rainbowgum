package io.jstach.rainbowgum.pattern;

import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A pattern keyword instance is the content of percent encoded keywords in a pattern.
 */
public interface PatternKeyword {

	/**
	 * Padding inforamation
	 * @return padding info.
	 */
	@Nullable
	PadInfo padInfo();

	/**
	 * Keyword used in pattern.
	 * @return keyword name or alias.
	 */
	String keyword();

	/**
	 * Parameters passed in <code>{</code> ... <code>}</code> comma separated.
	 * @return empty list if no parameters.
	 */
	List<String> optionList();

	/**
	 * Helper to get values from {@link #optionList()}.
	 * @param <T> converted type.
	 * @param index zero based index in option list.
	 * @param fallback if optionList does not have a value at the index use this value.
	 * @param f function to call for conversion.
	 * @return value.
	 */
	default <T> T opt(int index, T fallback, Function<String, T> f) {
		var v = optOrNull(index, f);
		if (v == null) {
			return fallback;
		}
		return v;
	}

	/**
	 * Helper to get values from {@link #optionList()}.
	 * @param <T> converted type.
	 * @param index zero based index in option list.
	 * @param f function to call for conversion.
	 * @return value.
	 */
	@SuppressWarnings("exports")
	default <T> @Nullable T optOrNull(int index, Function<String, T> f) {
		String s = optOrNull(index);
		if (s == null) {
			return null;
		}
		return f.apply(s);
	}

	/**
	 * Helper to get values from {@link #optionList()}.
	 * @param index zero based index in option list.
	 * @return value or null.
	 */
	default @Nullable String optOrNull(int index) {
		var optionList = optionList();
		int size = optionList.size();
		if (index < size) {
			String s = optionList.get(index);
			if (s.isBlank()) {
				return null;
			}
			return s;
		}
		return null;
	}

	/**
	 * Helper to get values from {@link #optionList()}
	 * @param index zero based index in option list.
	 * @param fallback if no value is at index return this value.
	 * @return found value or fallback.
	 */
	default String opt(int index, String fallback) {
		var v = optOrNull(index);
		if (v == null) {
			return fallback;
		}
		return v;
	}

}
