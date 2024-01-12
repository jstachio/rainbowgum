package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * <td>{@value #APPENDERS_PROPERTY} = <code>List&lt;String&gt;</code></td>
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

	// /**
	// * Logging output property for appending to a resource. The value should be a list
	// of
	// * names.
	// */
	// static final String OUTPUT_PROPERTY = ROOT_PREFIX + "output";

	/**
	 * A common prefix parameter is called name.
	 */
	static final String NAME = "name";

	/**
	 * Logging output prefix for configuration.
	 */
	static final String OUTPUT_PREFIX = ROOT_PREFIX + "output.{" + NAME + "}.";

	/**
	 * Logging output prefix for configuration.
	 */
	static final String ENCODER_PREFIX = ROOT_PREFIX + "encoder.{" + NAME + "}.";

	/**
	 * Logging appenders. The value should be a list of names.
	 */
	static final String APPENDERS_PROPERTY = ROOT_PREFIX + "appenders";

	/**
	 * Logging appender prefix for configuration.
	 */
	static final String APPENDER_PREFIX = ROOT_PREFIX + "appender.{" + NAME + "}.";

	/**
	 * Analogous to {@link System#getProperty(String)}.
	 * @param key property key.
	 * @return property value.
	 */
	public @Nullable String valueOrNull(String key);

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
		Map<String, String> m = new LinkedHashMap<>();
		parseUriQuery(uri.getRawQuery(), (k, v) -> {
			if (v != null) {
				m.put(k, v);
			}
		});
		return new MapProperties(uri.toString(), m);
	}

	/**
	 * Parse a {@linkplain URI#getRawQuery() URI query} in <a href=
	 * "https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data">
	 * application/x-www-form-urlencoded </a> format useful for parsing properties values
	 * that have key values embedded in them. <strong>This parser unlike form encoding
	 * uses <code>%20</code> for space as the data is coming from a URI.</strong>
	 * @param query raw query component of URI.
	 * @param consumer accept will be called for each entry and if key (left hand) does
	 * not have a "<code>=</code>" following it the second parameter on the consumer
	 * accept will be passed <code>null</code>.
	 */
	public static void parseUriQuery(String query,
			@SuppressWarnings("exports") BiConsumer<String, @Nullable String> consumer) {
		parseUriQuery(query, true, consumer);
	}

	/**
	 * Parses a URI query formatted string to a Map.
	 * @param query raw query component of URI.
	 * @return decoded key values with last key winning over previous equal keys.
	 * @see #parseUriQuery(String, BiConsumer)
	 */
	public static Map<String, String> parseMap(String query) {
		Map<String, String> m = new LinkedHashMap<>();
		parseUriQuery(query, (k, v) -> {
			if (v != null) {
				m.put(k, v);
			}
		});
		return m;
	}

	/**
	 * Parses a URI query for a multi value map. The list values of the map maybe empty if
	 * the query parameter does not have any values associated with it which would be the
	 * case if there is a parameter (key) with no "<code>=</code>" following it. For
	 * example the following would have three entries of <code>a,b,c</code> all with empty
	 * list: <pre>
	 * <code>
	 * a&amp;b&amp;c&amp;
	 * </code> </pre>
	 * @param query raw query component of URI.
	 * @return decoded key values with multiple keys grouped together in order found.
	 */
	public static Map<String, List<String>> parseMultiMap(String query) {
		Map<String, List<String>> m = new LinkedHashMap<>();
		BiConsumer<String, String> f = (k, v) -> {
			var list = m.computeIfAbsent(k, _k -> new ArrayList<String>());
			if (v != null) {
				list.add(v);
			}
		};
		parseUriQuery(query, true, f);
		return m;
	}

	/**
	 * Parses a list of strings from a string that is <a href=
	 * "https://www.w3.org/TR/2014/REC-html5-20141028/forms.html#url-encoded-form-data">
	 * percent encoded for escaping (application/x-www-form-urlencoded)</a> where the
	 * separator can be either "<code>&amp;</code>" or "<code>,</code>".
	 * <p>
	 * An example would be using ampersand: <pre><code>
	 *  "a&amp;b%20B&amp;c" -> ["a","b B","c"]
	 *  </code> </pre> and comma: <pre><code>
	 *  "a,b,c" -> ["a","b","c"]
	 *  </code> </pre>
	 * @param query the comma or ampersand delimited string that is in URI query format.
	 * @return list of strings
	 */
	public static List<String> parseList(String query) {
		List<String> list = new ArrayList<>();
		parseUriQuery(query, true, "[&,]", (k, v) -> list.add(k));
		return list;
	}

	private static void parseUriQuery(String query, boolean decode, BiConsumer<String, @Nullable String> consumer) {
		parseUriQuery(query, decode, "&", consumer);
	}

	private static void parseUriQuery(String query, boolean decode, String sep,
			BiConsumer<String, @Nullable String> consumer) {
		String[] pairs = query.split(sep);
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			String key;
			String value;
			if (idx == 0) {
				continue;
			}
			else if (idx < 0) {
				key = pair;
				value = null;
			}
			else {
				key = pair.substring(0, idx);
				value = pair.substring(idx + 1);
			}
			if (decode) {
				key = PercentCodec.decode(key, StandardCharsets.UTF_8);
				if (value != null) {
					value = PercentCodec.decode(value, StandardCharsets.UTF_8);
				}

			}
			if (key.isBlank()) {
				continue;
			}
			consumer.accept(key, value);
		}
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

	private static RuntimeException throwMissingError(List<String> keys) {
		throw new NoSuchElementException("Property missing. key: " + keys);
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
		for (Map.Entry<String, String> entry : parameters.entrySet()) {
			name = name.replace("{" + entry.getKey() + "}", entry.getValue());
		}
		return name;
	}

	/**
	 * Property prefix parameter pattern.
	 */
	static final Pattern PARAMETER_PATTERN = Pattern.compile("\\{(.*?)\\}");

	/**
	 * Extract key parameters with {@link #PARAMETER_PATTERN}.
	 * @param key also known as property name.
	 * @return parameters in key.
	 */
	static Set<String> keyParameters(String key) {
		Set<String> tokens = new LinkedHashSet<>();
		Matcher matcher = PARAMETER_PATTERN.matcher(key);
		while (matcher.find()) {
			tokens.add(matcher.group(1));
		}
		return tokens;
	}

	private static void validateKeyParameters(String key, Set<String> parameters) {
		var keyParameters = keyParameters(key);
		if (!parameters.equals(keyParameters)) {
			throw new IllegalArgumentException("Keyed parameter mismatch. key is parameters: " + keyParameters
					+ " provided parameters: " + parameters);
		}
	}

	/**
	 * A lazily retrieved value.
	 *
	 * @param <T> value type.
	 */
	sealed interface Value<T> {

		/**
		 * Maps a property value to a new property value.
		 * @param <U> new value type.
		 * @param mapper function.
		 * @return new property value.
		 */
		public <U> Value<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper);

		/**
		 * Gets the value.
		 * @return value or <code>null</code>.
		 */
		public @Nullable T valueOrNull();

		/**
		 * Gets a value if there is if not uses the fallback.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		default @Nullable T valueOrNull(@Nullable T fallback) {
			var v = valueOrNull();
			if (v != null) {
				return v;
			}
			return fallback;
		}

		/**
		 * Gets the value and will fail with {@link NoSuchElementException} if there is no
		 * value.
		 * @return value.
		 * @throws NoSuchElementException if there is no value.
		 */
		public T value() throws NoSuchElementException;

		/**
		 * Append description of value.
		 * @param sb appended to.
		 */
		public void describe(StringBuilder sb);

	}

	// record ValueValue<T>(@Nullable T valueOrNull) implements Value<T>{
	//
	// @Override
	// public <U> Value<U> map(
	// PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
	// // TODO Auto-generated method stub
	// return null;
	// }
	//
	// @Override
	// public T value()
	// throws NoSuchElementException {
	// var t = valueOrNull();
	// if (t == null) {
	// throw new NoSuchElementException();
	// }
	// return t;
	// }
	//
	// }

	/**
	 * Multiple properties tried in order.
	 *
	 * @param <T> property type.
	 * @param propertyValues property values
	 */
	record PropertiesValue<T>(List<Value<T>> propertyValues) implements Value<T> {

		@Override
		public <U> Value<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {

			List<Value<U>> values = new ArrayList<>();
			for (var v : propertyValues) {
				values.add(v.map(mapper));
			}
			return new PropertiesValue<>(values);
		}

		@Override
		public @Nullable T valueOrNull() {
			for (var pv : propertyValues) {
				var v = pv.valueOrNull();
				if (v != null) {
					return v;
				}
			}
			return null;
		}

		@Override
		public T value() throws NoSuchElementException {
			var t = valueOrNull();
			if (t != null) {
				return t;
			}
			StringBuilder error = new StringBuilder();
			error.append("Property not found for: ");
			describe(error);
			throw new NoSuchElementException(error.toString());
		}

		@Override
		public void describe(StringBuilder sb) {
			for (var v : propertyValues) {
				v.describe(sb);
				sb.append(", ");
			}
		}

	}

	/**
	 * A property value is a property and its lazily retrieved value.
	 *
	 * @param <T> value type.
	 * @param property backing property.
	 * @param properties the log properties instance.
	 */
	record PropertyValue<T>(Property<T> property, LogProperties properties) implements Value<T> {
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
			return property.propertyGetter.valueOrNull(properties, property.keys);
		}

		/**
		 * Gets a value if there if not uses the fallback.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		public @Nullable T valueOrNull(@Nullable T fallback) {
			var v = valueOrNull();
			if (v != null) {
				return v;
			}
			return fallback;
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
			return property.propertyGetter.value(properties, property.keys);
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
			return property.propertyGetter.value(properties, property.keys, fallback);
		}

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws NoSuchElementException if no property and fallback is
		 * <code>null</code>.
		 */
		public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
				throws NoSuchElementException {
			return property.propertyGetter.value(properties, property.keys, fallback);
		}

		/**
		 * Set a property if its not null.
		 * @param consumer first parameter is first key and second parameter is non null
		 * value.
		 */
		public void set(BiConsumer<String, T> consumer) {
			T value = valueOrNull();
			if (value != null) {
				consumer.accept(property.key(), value);
			}
		}

		public void describe(StringBuilder b) {
			b.append(property);
		}
	}

	/**
	 * A property description.
	 *
	 * @param propertyGetter getter to retrieve property value from {@link LogProperties}.
	 * @param keys property keys to try in order
	 * @param <T> property type.
	 */
	record Property<T>(PropertyGetter<T> propertyGetter, List<String> keys) {

		/**
		 * Property Constructor.
		 * @param propertyGetter getter to retrieve property value from
		 * {@link LogProperties}.
		 * @param keys property keys to try in order
		 */
		public Property {
			Objects.requireNonNull(propertyGetter);
			if (keys.isEmpty()) {
				throw new IllegalArgumentException("should have at least one key");
			}
		}

		/**
		 * Gets the first key.
		 * @return key.
		 */
		public String key() {
			return keys.get(0);
		}

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
			return new Property<>(propertyGetter.map(mapper), keys);
		}

		/**
		 * Set a property if its not null.
		 * @param value value to set.
		 * @param consumer first parameter is first key and second parameter is non null
		 * value.
		 */
		public void set(T value, BiConsumer<String, T> consumer) {
			if (value != null) {
				consumer.accept(key(), value);
			}
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
		 * Value or null.
		 * @param props log properties.
		 * @param keys key to use in order.
		 * @return value or <code>null</code>.
		 */
		@Nullable
		default T valueOrNull(LogProperties props, List<String> keys) {
			for (String key : keys) {
				var v = valueOrNull(props, key);
				if (v != null) {
					return v;
				}
			}
			return null;
		}

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
			return value(props, List.of(key));
		}

		/**
		 * Uses the key to get a property and fail if missing.
		 * @param props log properties.
		 * @param keys keys to try in order.
		 * @return value.
		 * @throws NoSuchElementException if no value is found for key.
		 */
		default T value(LogProperties props, List<String> keys) throws NoSuchElementException {
			for (String key : keys) {
				var v = valueOrNull(props, key);
				if (v != null) {
					return v;
				}
			}
			throw findRoot(this).throwMissing(props, keys);
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
			return value(props, List.of(key), fallback);
		}

		/**
		 * Value or fallback or exception if property is missing and fallback is null.
		 * @param props log properties.
		 * @param keys key to use.
		 * @param fallback to use can be null.
		 * @return value or <code>null</code>.
		 * @throws NoSuchElementException if property is missing and fallback is
		 * <code>null</code>.
		 */
		default T value(LogProperties props, List<String> keys, @Nullable T fallback) throws NoSuchElementException {
			var t = valueOrNull(props, keys);
			if (t == null) {
				t = fallback;
			}
			if (t == null) {
				throw findRoot(this).throwMissing(props, keys);
			}
			return t;
		}

		/**
		 * Value or fallback or exception if property is missing and fallback is null.
		 * @param props log properties.
		 * @param keys key to use.
		 * @param fallback to use can be null returned.
		 * @return value or <code>null</code>.
		 * @throws NoSuchElementException if property is missing and fallback is
		 * <code>null</code>.
		 */
		default T value(LogProperties props, List<String> keys,
				@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback) throws NoSuchElementException {
			var t = valueOrNull(props, keys);
			if (t == null) {
				t = fallback.get();
			}
			if (t == null) {
				throw findRoot(this).throwMissing(props, keys);
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
		 * @throws IllegalArgumentException if the key is malformed.
		 */
		default Property<T> build(String key) throws IllegalArgumentException {
			String fqn = fullyQualifiedKey(key);
			if (!fqn.startsWith(LogProperties.ROOT_PREFIX)) {
				throw new IllegalArgumentException(
						"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'. key = " + key);
			}
			if (fqn.endsWith(".") || fqn.startsWith(".")) {
				throw new IllegalArgumentException(
						"Property key should not start or end with '" + LogProperties.SEP + "'");
			}
			validateKeyParameters(key, Set.of());
			return new Property<>(this, List.of(key));
		}

		/**
		 * Creates a Property from the given key and its {@value LogProperties#NAME}
		 * parameter.
		 * @param key key.
		 * @param name interpolates <code>{name}</code> in property name with this value.
		 * @return property.
		 */
		default Property<T> buildWithName(String key, String name) {
			var parameters = Map.of(NAME, name);
			validateKeyParameters(key, parameters.keySet());
			String fqn = LogProperties.interpolateKey(key, parameters);
			return build(fqn);
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

			NoSuchElementException throwMissing(LogProperties props, List<String> keys) {
				List<String> resolvedKeys = describeKeys(props, keys);
				throw LogProperties.throwMissingError(resolvedKeys);
			}

			List<String> describeKeys(LogProperties props, List<String> keys) {
				return keys.stream().map(k -> props.description(fullyQualifiedKey(k))).toList();
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
			 * A boolean property getter.
			 * @return getter that will convert to boolean.
			 */
			public PropertyGetter<Boolean> toBoolean() {
				return this.map(Boolean::parseBoolean);
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
