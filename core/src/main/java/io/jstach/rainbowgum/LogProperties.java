package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.PropertyFunction;
import io.jstach.rainbowgum.LogProperties.PropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.ChildPropertyGetter;
import io.jstach.rainbowgum.LogProperties.PropertyGetter.RootPropertyGetter;

/**
 * Provides String based properties like {@link System#getProperty(String)} for default
 * configuration of logging levels and output.
 * <p>
 * If a custom {@link LogProperties} is configured the default implementation uses System
 * properties.
 * <p>
 * The builtin propertiers to configure RainbowGum are modeled after <a href=
 * "https://docs.spring.io/spring-boot/docs/3.1.0/reference/html/features.html#features.logging">
 * Spring Boot logging </a>
 *
 * <table class="table">
 * <caption>Builtin Properties</caption>
 * <tr>
 * <th>Property Pattern</th>
 * <th>Description</th>
 * </tr>
 * <tr>
 * <td>{@value #LEVEL_PREFIX} + {@value #SEP} + logger = LEVEL</td>
 * <td>Sets the level for the logger. If logger name is missing then it is considered the
 * root logger. {@link Level#INFO} is the default level for the root logger.</td>
 * </tr>
 * <tr>
 * <td>{@value #CHANGE_PREFIX} + {@value #SEP} + logger = boolean</td>
 * <td>Allows runtime changing of levels for the logger. By design RainbowGum does not
 * allow loggers to change levels once initialized. This configuration will allow the
 * level to be changed for the logger and its children and by default is false as the
 * builtin {@link LogProperties} is generally static (system properties).</td>
 * </tr>
 * <tr>
 * <td>{@value #FILE_PROPERTY} = URI</td>
 * <td>A URI or file path to log to a file.</td>
 * </tr>
 * <tr>
 * <td>{@value #OUTPUT_PROPERTY} = URI</td>
 * <td>A URI to an {@linkplain LogOutput output}.</td>
 * </tr>
 * </table>
 */
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
	 * Logging level properties prefix. The value should be the name of a
	 * {@linkplain java.lang.System.Logger.Level level}.
	 */
	static final String LEVEL_PREFIX = ROOT_PREFIX + "level";

	/**
	 * Logging change properties prefix.
	 */
	static final String CHANGE_PREFIX = ROOT_PREFIX + "change";

	/**
	 * Logging file property for default single file appending. The value should be a URI.
	 */
	static final String FILE_PROPERTY = ROOT_PREFIX + "file.name";

	/**
	 * Logging output property for appending to a resource. The value should be a URI.
	 */
	static final String OUTPUT_PROPERTY = ROOT_PREFIX + "output";

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
	 * Creates log properties from a {@linkplain URI#getQuery() URI query} in <a href=
	 * "https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data">
	 * application/x-www-form-urlencoded </a> format useful for parsing {@link LogOutput}
	 * configuration. <strong>This parser unlike form encoding uses <code>%20</code> for
	 * space as the data is coming from a URI.</strong>
	 * @param uri uri to get query from.
	 * @return properties
	 * @see LogOutput
	 */
	public static LogProperties of(URI uri) {
		var m = parseUriQuery(uri.getRawQuery(), true);
		return new MapProperties(uri.toString(), m);
	}

	private static Map<String, String> parseUriQuery(String query, boolean decode) {

		Map<String, String> kvs = new LinkedHashMap<>();
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			String key;
			String value;
			if (idx == 0) {
				continue;
			}
			else if (idx < 0) {
				key = pair;
				value = "";
			}
			else {
				key = pair.substring(0, idx);
				value = pair.substring(idx + 1);
			}
			if (decode) {
				key = PercentCodec.decode(key, StandardCharsets.UTF_8);
				value = PercentCodec.decode(key, StandardCharsets.UTF_8);

			}
			if (key.isBlank()) {
				continue;
			}
			kvs.put(key, value);

		}
		return kvs;
	}

	/**
	 * Common log properties.
	 */
	enum StandardProperties implements LogProperties {

		/**
		 * Empty properties.
		 */
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
		/**
		 * Uses {@link System#getProperty(String)} for {@link #valueOrNull(String)}. The
		 * {@link #order()} is the same value as microprofile config ordinal.
		 */
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
	 * Replace "{name}" tokens in property names.
	 * @param name property name.
	 * @param parameters tokens to replace with entry values.
	 * @return interpolated property name.
	 */
	static String interpolateKey(String name, Map<String, String> parameters) {

		// if (validate) {
		// StringBuilder error = new StringBuilder();
		// for (Map.Entry<String, String> entry : parameters.entrySet()) {
		// String key = entry.getKey();
		// String p = "{" + key + "}";
		// if (! name.contains(p)) {
		// }
		// }
		// }
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			name = name.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return name;
	}

	/**
	 * A property value is a property and its lazily retrieved value.
	 *
	 * @param <T> value type.
	 * @param property backing property.
	 * @param properties the log properties instance.
	 */
	record PropertyValue<T>(Property<T> property, LogProperties properties) {
		/**
		 * Maps a property value to a new property value.
		 * @param <U> new value type.
		 * @param mapper function.
		 * @return new property value.
		 */
		public <U> PropertyValue<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new PropertyValue<>(property.map(mapper), properties);
		}

		/**
		 * Gets the value.
		 * @return value or <code>null</code>.
		 */
		public @Nullable T valueOrNull() {
			return property.propertyGetter.valueOrNull(properties, property.key);
		}

		/**
		 * Gets a value if there if not uses the fallback.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		public @Nullable T valueOrNull(@Nullable T fallback) {
			return property.propertyGetter.valueOrNull(properties, property.key, fallback);
		}

		/**
		 * Convenience that turns a value into an optional.
		 * @return optional.
		 */
		public Optional<T> optional() {
			return Optional.ofNullable(valueOrNull());
		}

		/**
		 * Gets the value and will fail with {@link NoSuchElementException} if there is no
		 * value.
		 * @return value.
		 * @throws NoSuchElementException if there is no value.
		 */
		public T value() throws NoSuchElementException {
			return property.propertyGetter.value(properties, property.key);
		}

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws NoSuchElementException if no property and fallback is
		 * <code>null</code>.
		 */
		public T value(@Nullable T fallback) throws NoSuchElementException {
			return property.propertyGetter.value(properties, property.key, fallback);
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

		/**
		 * Gets a property value.
		 * @param properties properties.
		 * @return property value from this property and passed in properties.
		 */
		public PropertyValue<T> get(LogProperties properties) {
			return new PropertyValue<>(this, properties);
		}

		/**
		 * Maps the property to another value type.
		 * @param <U> value type.
		 * @param mapper function to map.
		 * @return property.
		 */
		public <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new Property<>(propertyGetter.map(mapper), key);
		}

		/**
		 * Builder.
		 * @return builder.
		 */
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

		/**
		 * Value or null.
		 * @param props log properties.
		 * @param key key to use.
		 * @return value or <code>null</code>.
		 */
		@Nullable
		T valueOrNull(LogProperties props, String key);

		/**
		 * Value or fallback if property is missing.
		 * @param props log properties.
		 * @param key key to use.
		 * @param fallback to use can be null.
		 * @return value or <code>null</code>.
		 */
		default @Nullable T valueOrNull(LogProperties props, String key, @Nullable T fallback) {
			var t = valueOrNull(props, key);
			if (t == null) {
				return fallback;
			}
			return t;
		}

		/**
		 * Determines full name of key.
		 * @param key key.
		 * @return fully qualified key name.
		 */
		default String fullyQualifiedKey(String key) {
			return findRoot(this).fullyQualifiedKey(key);
		}

		/**
		 * Uses the key to get a property and fail if missing.
		 * @param props log properties.
		 * @param key key.
		 * @return value.
		 * @throws NoSuchElementException if no value is found for key.
		 */
		default T value(LogProperties props, String key) throws NoSuchElementException {
			var t = valueOrNull(props, key);
			if (t == null) {
				throw findRoot(this).throwMissing(props, key);
			}
			return t;
		}

		/**
		 * Value or fallback or exception if property is missing and fallback is null.
		 * @param props log properties.
		 * @param key key to use.
		 * @param fallback to use can be null.
		 * @return value or <code>null</code>.
		 * @throws NoSuchElementException if property is missing and fallback is
		 * <code>null</code>.
		 */
		default T value(LogProperties props, String key, @Nullable T fallback) throws NoSuchElementException {
			var t = valueOrNull(props, key);
			if (t == null) {
				t = fallback;
			}
			if (t == null) {
				throw findRoot(this).throwMissing(props, key);
			}
			return t;
		}

		/**
		 * Creates a Property from the given key and this getter.
		 * @param key key.
		 * @return property.
		 */
		default Property<T> property(String key) {
			return build(key);
		}

		/**
		 * Creates a Property from the given key and this getter.
		 * @param key key.
		 * @return property.
		 */
		default Property<T> build(String key) {
			String fqn = fullyQualifiedKey(key);
			if (!fqn.startsWith(LogProperties.ROOT_PREFIX)) {
				throw new IllegalArgumentException(
						"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'");
			}
			return new Property<>(this, key);
		}

		/**
		 * Find root property getter.
		 * @param e property getter.
		 * @return root.
		 */
		public static RootPropertyGetter findRoot(PropertyGetter<?> e) {
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

		/**
		 * Default root property getter.
		 * @return property getter.
		 */
		public static RootPropertyGetter of() {
			return new RootPropertyGetter("", false);
		}

		/**
		 * A property getter that has no conversion but maye prefix the key and search
		 * recursively up the key path.
		 *
		 * @param prefix added to the key before looking up in {@link LogProperties}.
		 * @param search if true will recursively search up the key path
		 */
		record RootPropertyGetter(String prefix, boolean search) implements PropertyGetter<String> {

			@Override
			public @Nullable String valueOrNull(LogProperties props, String key) {
				if (search) {
					return props.search(prefix, key);
				}
				return props.valueOrNull(LogProperties.concatKey(prefix, key));
			}

			RuntimeException throwError(LogProperties props, String key, Exception e) {
				throw LogProperties.throwPropertyError(props.description(fullyQualifiedKey(key)), e);
			}

			NoSuchElementException throwMissing(LogProperties props, String key) {
				throw LogProperties.throwMissingError(props.description(fullyQualifiedKey(key)));
			}

			/**
			 * Will search with prefix.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withSearch(String prefix) {
				return new RootPropertyGetter(prefix, true);
			}

			/**
			 * Will prefix key.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withPrefix(String prefix) {
				return new RootPropertyGetter(prefix, search);
			}

			@Override
			public String fullyQualifiedKey(String key) {
				return concatKey(prefix, key);
			}

			/**
			 * An integer property getter.
			 * @return getter that will convert to integers.
			 */
			public PropertyGetter<Integer> toInt() {
				return this.map(Integer::parseInt);
			}

			/**
			 * An enum property getter using {@link Enum#valueOf(Class, String)}.
			 * @param <T> enum type.
			 * @param enumClass enum class.
			 * @return getter that will convert to enum type.
			 */
			public <T extends Enum<T>> PropertyGetter<T> toEnum(Class<T> enumClass) {
				return this.map(s -> Enum.valueOf(enumClass, s));
			}

			/**
			 * A URI property getter that parses URIs.
			 * @return getter that will parse URIs.
			 */
			public PropertyGetter<URI> toURI() {
				return this.map(URI::new);
			}

		}

		/**
		 * Mapped property getter.
		 *
		 * @param <T> will convert to this type.
		 */
		public sealed interface ChildPropertyGetter<T> extends PropertyGetter<T> {

			/**
			 * Parent.
			 * @return parent.
			 */
			PropertyGetter<?> parent();

		}

		/**
		 * Sets up to converts a value.
		 * @param <U> value type
		 * @param mapper function.
		 * @return new property getter.
		 */
		default <U> PropertyGetter<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new MapExtractor<T, U>(this, mapper);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback fallback value.
		 * @return new property getter.
		 */
		default PropertyGetter<T> orElse(T fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, () -> fallback);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback supplier.
		 * @return new property getter.
		 */
		default PropertyGetter<T> orElseGet(Supplier<? extends T> fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, fallback);
		}

	}

}

record MapProperties(String description, Map<String, String> map) implements LogProperties {

	@Override
	public @Nullable String valueOrNull(String key) {
		return map.get(key);
	}

	@Override
	public String description(String key) {
		return description + " (" + key + ")";
	}

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
				throw PropertyGetter.findRoot(parent).throwError(props, key, e);
			}
		}
		return null;
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
