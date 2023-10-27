package io.jstach.rainbowgum;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.PropertyGetter.RootPropertyGetter;

/**
 * Provides String based properties like {@link System#getProperty(String)} for default
 * configuration of logging levels and output.
 */
@FunctionalInterface
public interface LogProperties {

	/**
	 * properties separator.
	 */
	static final String SEP = ".";

	/**
	 * Logging properties prefix.
	 */
	static final String ROOT_PREFIX = "logging" + SEP;

	/**
	 * Logging level properties prefix.
	 */
	static final String LEVEL_PREFIX = ROOT_PREFIX + "level";

	/**
	 * Logging change properties prefix.
	 */
	static final String CHANGE_PREFIX = ROOT_PREFIX + "change";

	/**
	 * Logging file property for default single file appending.
	 */
	static final String FILE_PROPERTY = ROOT_PREFIX + "file";

	/**
	 * Analogous to {@link System#getProperty(String)}.
	 * @param key property key.
	 * @return property value.
	 */
	public @Nullable String valueOrNull(String key);

	/**
	 * Gets a value based on the passed in property.
	 * @param <T> value type.
	 * @param property property.
	 * @return property value.
	 */
	default <T> PropertyValue<T> property(Property<T> property) {
		return property.get(this);
	}

	/**
	 * Searches up a path using this properties to check for values.
	 * @param root prefix.
	 * @param key should start with prefix.
	 * @return closest value.
	 */
	default @Nullable String search(String root, String key) {
		return searchPath(key, k -> valueOrNull(concatKey(root, k)));

	}

	/**
	 * Describes a key and by default is usually just the key.
	 * @param key key.
	 * @return usually the key.
	 */
	default String description(String key) {
		return key;
	}

	/**
	 * When log properties are coalesced this method is used to resolve order. A higher
	 * number gives higher precedence.
	 * @return zero by default.
	 */
	default int order() {
		return 0;
	}

	/**
	 * Creates log properties from many log properties.
	 * @param logProperties list of properties.
	 * @param fallback if the logProperties list is empty.
	 * @return log properties
	 */
	public static LogProperties of(List<? extends LogProperties> logProperties, LogProperties fallback) {
		if (logProperties.isEmpty()) {
			return fallback;
		}
		if (logProperties.size() == 0) {
			return logProperties.get(0);
		}
		var array = logProperties.toArray(new LogProperties[] {});
		Arrays.sort(array, Comparator.comparingInt(LogProperties::order).reversed());
		return new CompositeLogProperties(array);
	}

	/**
	 * Creates log properties from many log properties.
	 * @param logProperties the list of properties.
	 * @return if the logProperties is empty {@link StandardProperties#EMPTY} will be
	 * used.
	 */
	public static LogProperties of(List<? extends LogProperties> logProperties) {
		return of(logProperties, StandardProperties.EMPTY);
	}

	/**
	 * Common log properties.
	 */
	enum StandardProperties implements LogProperties {

		EMPTY {
			@Override
			public @Nullable String valueOrNull(String key) {
				return null;
			}

			@Override
			public int order() {
				return -1;
			}
		},
		SYSTEM_PROPERTIES {
			@Override
			public @Nullable String valueOrNull(String key) {
				return System.getProperty(key);
			}

			@Override
			public int order() {
				return 400;
			}
		}

	}

	/**
	 * An error friendly {@link Function} for converting properties.
	 *
	 * @param <T> input type.
	 * @param <R> output type.
	 * @param <E> error type.
	 */
	public interface PropertyFunction<T extends @Nullable Object, R extends @Nullable Object, E extends Exception>
			extends Function<T, R> {

		@Override
		default R apply(T t) {
			try {
				return _apply(t);
			}
			catch (Exception e) {
				sneakyThrow(e);
				throw new RuntimeException(e);
			}
		}

		/**
		 * Apply that throws error.
		 * @param t input
		 * @return output
		 * @throws E if an error happened in function.
		 */
		public R _apply(T t) throws E;

		@SuppressWarnings("unchecked")
		private static <E extends Throwable> void sneakyThrow(final Throwable x) throws E {
			throw (E) x;
		}

	}

	private static RuntimeException throwPropertyError(String key, Exception e) {
		throw new RuntimeException("Error for property. key: " + key, e);
	}

	private static RuntimeException throwMissingError(String key) {
		throw new NoSuchElementException("Property missing. key: " + key);
	}

	/**
	 * Searches a property path recursing up the path.
	 * @param <T> type to return.
	 * @param name the initial path.
	 * @param resolveFunc function that returns the value at path or <code>null</code>.
	 * @return value or <code>null</code>.
	 */
	@SuppressWarnings("exports")
	public static <T> @Nullable T searchPath(String name, Function<String, @Nullable T> resolveFunc) {
		return searchPath(name, resolveFunc, SEP);
	}

	private static <T> @Nullable T searchPath(String name, Function<String, @Nullable T> resolveFunc, String sep) {
		String tempName = name;
		T level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = resolveFunc.apply(tempName);
			indexOfLastDot = tempName.lastIndexOf(sep);
		}
		if (level != null) {
			return level;
		}
		return null;
	}

	/**
	 * Concats a key using {@link #SEP} to the root prefix.
	 * @param name key.
	 * @return concat key.
	 */
	static String concatKey(String name) {
		return concatKey(LogProperties.ROOT_PREFIX, name);
	}

	/**
	 * Concats a keys using {@link #SEP} as the separator.
	 * @param prefix start of key.
	 * @param name second part of key.
	 * @return key.
	 */
	static String concatKey(String prefix, String name) {
		if (name.equals("")) {
			return prefix;
		}
		if (prefix.equals("")) {
			return name;
		}
		if (prefix.endsWith(".")) {
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		if (name.startsWith(SEP)) {
			return prefix + name;
		}
		return prefix + SEP + name;
	}

	/**
	 * A property value is a property and its lazily retrieved value.
	 *
	 * @param <T> value type.
	 * @param property backing property.
	 * @param properties the log properties instance.
	 */
	record PropertyValue<T>(Property<T> property, LogProperties properties) {
		public <U> PropertyValue<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new PropertyValue<>(property.map(mapper), properties);
		}

		public @Nullable T valueOrNull() {
			return property.propertyGetter.valueOrNull(properties, property.key);
		}

		public Optional<T> optional() {
			return Optional.ofNullable(valueOrNull());
		}

		public T value() {
			return property.propertyGetter.require(properties, property.key);
		}
	}

	/**
	 * A property description.
	 *
	 * @param <T> property type.
	 * @param propertyGetter getter to retrieve property value from {@link LogProperties}.
	 * @param key property key.
	 */
	record Property<T>(PropertyGetter<T> propertyGetter, String key) {

		public PropertyValue<T> get(LogProperties properties) {
			return new PropertyValue<>(this, properties);
		}

		private <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new Property<>(propertyGetter.map(mapper), key);
		}

		public static RootPropertyGetter builder() {
			return PropertyGetter.of();
		}
	}

	/**
	 * Extracts and converts from {@link LogProperties}.
	 *
	 * @param <T> value type.
	 */
	sealed interface PropertyGetter<T> {

		@Nullable
		T valueOrNull(LogProperties props, String key);

		default String fullyQualifiedKey(String key) {
			return findRoot(this).fullyQualifiedKey(key);
		}

		default T require(LogProperties props, String key) {
			var t = valueOrNull(props, key);
			if (t == null) {
				throw findRoot(this).throwMissing(props, key);
			}
			return t;
		}

		default Property<T> property(String key) {
			return build(key);
		}

		default Property<T> build(String key) {
			String fqn = fullyQualifiedKey(key);
			if (!fqn.startsWith(LogProperties.ROOT_PREFIX)) {
				throw new IllegalArgumentException(
						"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'");
			}
			return new Property<>(this, key);
		}

		private static RootPropertyGetter findRoot(PropertyGetter<?> e) {
			if (e instanceof RootPropertyGetter r) {
				return r;
			}
			else if (e instanceof ChildPropertyGetter c) {
				return findRoot(c.parent());
			}
			else {
				throw new IllegalStateException("bug");
			}

		}

		public static RootPropertyGetter of() {
			return new RootPropertyGetter("", false);
		}

		record RootPropertyGetter(String prefix, boolean search) implements PropertyGetter<String> {

			@Override
			public @Nullable String valueOrNull(LogProperties props, String key) {
				if (search) {
					return props.search(prefix, key);
				}
				return props.valueOrNull(LogProperties.concatKey(prefix, key));
			}

			private RuntimeException throwError(LogProperties props, String key, Exception e) {
				throw LogProperties.throwPropertyError(props.description(fullyQualifiedKey(key)), e);
			}

			private NoSuchElementException throwMissing(LogProperties props, String key) {
				throw LogProperties.throwMissingError(props.description(fullyQualifiedKey(key)));
			}

			public RootPropertyGetter withSearch(String prefix) {
				return new RootPropertyGetter(prefix, true);
			}

			public RootPropertyGetter withPrefix(String prefix) {
				return new RootPropertyGetter(prefix, search);
			}

			@Override
			public String fullyQualifiedKey(String key) {
				return concatKey(prefix, key);
			}

		}

		public sealed interface ChildPropertyGetter<T> extends PropertyGetter<T> {

			PropertyGetter<?> parent();

		}

		default <U> PropertyGetter<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new MapExtractor<T, U>(this, mapper);
		}

		default PropertyGetter<T> orElse(T fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, () -> fallback);
		}

		default PropertyGetter<T> orElseGet(Supplier<? extends T> fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, fallback);
		}

		record FallbackExtractor<T>(PropertyGetter<T> parent,
				Supplier<? extends T> fallback) implements ChildPropertyGetter<T> {

			@Override
			public T valueOrNull(LogProperties props, String key) {
				var n = parent.valueOrNull(props, key);
				if (n != null) {
					return n;
				}
				return fallback.get();
			}

		}

		record MapExtractor<T, R>(PropertyGetter<T> parent,
				PropertyFunction<? super T, ? extends R, ? super Exception> mapper) implements ChildPropertyGetter<R> {

			@Override
			public @Nullable R valueOrNull(LogProperties props, String key) {
				var n = parent.valueOrNull(props, key);
				if (n != null) {
					try {
						return mapper._apply(n);
					}
					catch (Exception e) {
						throw findRoot(parent).throwError(props, key, e);
					}
				}
				return null;
			}

		}

	}

}

record CompositeLogProperties(LogProperties[] properties) implements LogProperties {

	@Override
	public @Nullable String valueOrNull(String key) {
		for (var props : properties) {
			var value = props.valueOrNull(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

}
