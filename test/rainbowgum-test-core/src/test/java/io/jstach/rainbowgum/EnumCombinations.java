package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

/**
 * A test utility class for generating enum combinations for parameterized unit testing.
 */
public class EnumCombinations {

	/**
	 * Generates combinations of enums.
	 * @param first enum class
	 * @param others other enum classes.
	 * @return stream of JUnit arguments.
	 */
	@SuppressWarnings({ "null", "exports" })
	@SafeVarargs
	public static Stream<Arguments> args(Class<? extends Enum<?>> first, Class<? extends Enum<?>>... others) {
		List<List<Enum<?>>> result = combinations(first, others);
		Stream<Arguments> args = toArguments(result);
		return args;
	}

	/**
	 * Converts enum list to JUnit arguments
	 * @param combinations list of enum combinations.
	 * @return stream of JUnit arguments.
	 */
	@SuppressWarnings("exports")
	public static Stream<Arguments> toArguments(List<List<Enum<?>>> combinations) {
		@SuppressWarnings("null")
		Stream<Arguments> args = combinations.stream().map(list -> Arguments.arguments(list.toArray()));
		return args;
	}

	/**
	 * Generates combinations of enums.
	 * @param first enum class
	 * @param others other enum classes.
	 * @return list of combinations.
	 */
	@SafeVarargs
	public static List<List<Enum<?>>> combinations(Class<? extends Enum<?>> first, Class<? extends Enum<?>>... others) {
		List<Class<? extends Enum<?>>> enumClasses = new ArrayList<>();
		enumClasses.add(first);
		for (var o : others) {
			Objects.requireNonNull(o);
			enumClasses.add(o);
		}
		return combinations(enumClasses);
	}

	private static List<List<Enum<?>>> combinations(List<Class<? extends Enum<?>>> enumClasses) {
		List<List<Enum<?>>> result = new ArrayList<>();
		combinationsHelper(result, new ArrayList<>(), enumClasses);
		return result;
	}

	private static void combinationsHelper(List<List<Enum<?>>> result, List<Enum<?>> current,
			List<Class<? extends Enum<?>>> enumClasses) {
		if (enumClasses.isEmpty()) {
			result.add(new ArrayList<>(current));
		}
		else {
			@SuppressWarnings("rawtypes")
			Class currentEnumClass = enumClasses.get(0);
			@SuppressWarnings("unchecked")
			Set<Enum<?>> set = EnumSet.allOf(currentEnumClass);
			List<Class<? extends Enum<?>>> remainingEnumClasses = enumClasses.subList(1, enumClasses.size());
			for (Enum<?> value : set) {
				List<Enum<?>> nextCombination = new ArrayList<>(current);
				nextCombination.add(value);
				combinationsHelper(result, nextCombination, remainingEnumClasses);
			}
		}
	}

}
