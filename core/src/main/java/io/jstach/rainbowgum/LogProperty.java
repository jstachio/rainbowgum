package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyGetter.ChildPropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyGetter.RequiredPropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyGetter.RootPropertyGetter;
import io.jstach.rainbowgum.LogProperty.RequiredResult;
import io.jstach.rainbowgum.LogProperty.Result;

/**
 * A single property or value.
 */
public interface LogProperty {

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

	/**
	 * Parent interface for property exceptions.
	 */
	sealed interface PropertyProblem {

	}

	/**
	 * Thrown if an error happens while converting a property.
	 */
	final static class PropertyConvertException extends RuntimeException implements PropertyProblem {

		private static final long serialVersionUID = -6260241455268426342L;

		/**
		 * Key.
		 */
		private final String key;

		/**
		 * Creates convert exception.
		 * @param key property key.
		 * @param message error message
		 * @param cause maybe null.
		 */
		PropertyConvertException(String key, String message, @Nullable Throwable cause) {
			super(message, cause);
			this.key = key;
		}

		/**
		 * Property key.
		 * @return key.
		 */
		public String key() {
			return this.key;
		}

	}

	/**
	 * Throw if property is missing.
	 */
	final static class PropertyMissingException extends NoSuchElementException implements PropertyProblem {

		private static final long serialVersionUID = 4203076848052565692L;

		/**
		 * Creates a missing property exception.
		 * @param s error message.
		 */
		PropertyMissingException(String s) {
			super(s);
		}

	}

	/**
	 * The result of a property fetched from properties.
	 *
	 * @param <T> property type.
	 */
	sealed interface Result<T> {

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
		 * @throws PropertyMissingException if there is no value.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		public T value() throws PropertyMissingException, PropertyConvertException;

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws PropertyMissingException if no property and fallback is
		 * <code>null</code>.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		default T value(@Nullable T fallback) throws PropertyMissingException, PropertyConvertException {
			if (fallback == null) {
				return value();
			}
			return value(() -> fallback);
		}

		/**
		 * Gets a value if there if not uses the fallback if not null otherwise throws an
		 * exception.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 * @throws PropertyMissingException if no property and fallback is
		 * <code>null</code>.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
				throws PropertyMissingException, PropertyConvertException;

		/**
		 * Convenience that turns a value into an optional.
		 * @return optional.
		 */
		default Optional<T> optional() {
			return Optional.ofNullable(valueOrNull());
		}

		/**
		 * A property that is present.
		 *
		 * @param <T> property type.
		 * @param value actual value.
		 */
		public record Success<T>(T value) implements RequiredResult<T> {
			/**
			 * Successfully found property value.
			 * @param value actual value should not be <code>null</code>.
			 */
			public Success {
				if (value == null) {
					throw new NullPointerException("value");
				}
			}

			@Override
			public @Nullable T valueOrNull() {
				return value();
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				return value;
			}
		}

		/**
		 * A property that is missing (<code>null</code>).
		 *
		 * @param <T> property type.
		 * @param message description of where the property is missing.
		 */
		public record Missing<T>(String message) implements Result<T> {
			@Override
			public @Nullable T valueOrNull() {
				return null;
			}

			@Override
			public T value() throws NoSuchElementException {
				throw new PropertyMissingException(message);
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				var v = fallback.get();
				if (v != null) {
					return v;
				}
				throw new PropertyMissingException(message);
			}

			/**
			 * Helper just to cast the result to a different parameterized type.
			 * @param <R> parameterized type.
			 * @return this.
			 */
			@SuppressWarnings("unchecked")
			public <R> Missing<R> convert() {
				return (Missing<R>) this;
			}

		}

		/**
		 * A property that was present but failed conversion.
		 *
		 * @param <T> property type.
		 * @param key property key that failed to convert.
		 * @param message failure message.
		 * @param cause exception thrown while trying to convert.
		 */
		@SuppressWarnings("JavaLangClash")
		public record Error<T>(String key, String message, Exception cause) implements RequiredResult<T> {
			/*
			 * TODO consider rename to Failure
			 */
			@Override
			public @Nullable T valueOrNull() {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public T value() throws NoSuchElementException {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws NoSuchElementException {
				throw new PropertyConvertException(key, message, cause);
			}

			/**
			 * Helper just to cast the result to a different parameterized type.
			 * @param <R> parameterized type.
			 * @return this.
			 */
			@SuppressWarnings("unchecked")
			public <R> Error<R> convert() {
				return (Error<R>) this;
			}
		}

	}

	/**
	 * A result that is not missing and will either be an error or success.
	 *
	 * @param <T> property type.
	 */
	sealed interface RequiredResult<T> extends Result<T> {

	}

	/**
	 * A property description that can retrieve a property result by being passed
	 * {@link LogProperties}. <em>The property instance does not actually contain the
	 * value of the property!</em>.
	 *
	 * @param <T> property type.
	 * @see RequiredProperty
	 * @see Result
	 */
	sealed interface Property<T> {

		/**
		 * Gets the first key.
		 * @return key.
		 */
		public String key();

		/**
		 * Gets a property value as a result.
		 * @param properties key values.
		 * @return result.
		 */
		public Result<T> get(LogProperties properties);

		/**
		 * Maps the property to another value type.
		 * @param <U> value type.
		 * @param mapper function to map.
		 * @return property.
		 */
		public <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper);

		/**
		 * Converts the value into a String that can be parsed by the built-in properties
		 * parsing of types. Supported types: String, Boolean, Integer, URI, Map, List.
		 * @param value to be converted to string.
		 * @return property representation of value.
		 */
		public String propertyString(T value);

		/**
		 * Set a property if its not null.
		 * @param value value to set.
		 * @param consumer first parameter is first key and second parameter is non null
		 * value.
		 */
		public void set(T value, BiConsumer<String, T> consumer);

		/**
		 * Programmatically check if a value that corresponds to this property is not
		 * null.
		 * @param value maybe <code>null</code> but an exception will be thrown if it is.
		 * @return value if not null.
		 * @throws NoSuchElementException if the value is null.
		 * @apiNote this is an expected exception and should not be treated as crash-able
		 * exception that should avoided with static analysis such as Checker Framework
		 * and is why the input argument is nullable.
		 */
		default T require(@Nullable T value) {
			if (value == null) {
				throw new PropertyMissingException("Value is required not null. property key='" + key() + "'");
			}
			return value;
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
	 * Property Builder that can have keys added to try.
	 *
	 * @param <T> value type.
	 */
	final class PropertyBuilder<T> {

		private final List<String> keys = new ArrayList<>();

		private final PropertyGetter<T> getter;

		private PropertyBuilder(String key, PropertyGetter<T> getter) {
			keys.add(key);
			this.getter = getter;
		}

		/**
		 * Adds an additional key to try.
		 * @param key key to try.
		 * @return this
		 */
		public PropertyBuilder<T> addKey(String key) {
			keys.add(key);
			return this;
		}

		/**
		 * Adds a key with a <code>{name}</code> parameter.
		 * @param key key to be interpolated.
		 * @param name name.
		 * @return this
		 */
		public PropertyBuilder<T> addKeyWithName(String key, String name) {
			var parameters = Map.of(LogProperties.NAME, name);
			LogProperties.validateKeyParameters(key, parameters.keySet());
			String fqn = LogProperties.interpolateKey(key, parameters);
			return addKey(fqn);
		}

		/**
		 * Builds the property with the supplied keys.
		 * @return property
		 */
		public Property<T> build() {
			return getter.build(keys.get(0), keys.subList(1, keys.size()).toArray(new String[] {}));
		}

	}

	/**
	 * A property that should never be missing usually because there is non-null fallback.
	 *
	 * @param <T> property type.
	 */
	sealed interface RequiredProperty<T> extends Property<T> {

		/**
		 * Gets a property value as a result.
		 * @param properties key values.
		 * @return result.
		 */
		@Override
		public RequiredResult<T> get(LogProperties properties);

		/**
		 * Maps the property to another value type.
		 * @param <U> value type.
		 * @param mapper function to map.
		 * @return property.
		 */
		@Override
		public <U> RequiredProperty<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper);

	}

	/**
	 * Extracts and converts from {@link LogProperties} and is also an immutable builder
	 * for {@link Property}s.
	 *
	 * @param <T> value type.
	 */
	sealed interface PropertyGetter<T> {

		/**
		 * Gets a result from properties.
		 * @param props properties.
		 * @param key property key usually dotted.
		 * @return result.
		 */
		Result<T> get(LogProperties props, String key);

		/**
		 * Gets a result from properties by trying a list of keys.
		 * @param props properties.
		 * @param keys list of property keys usually dotted.
		 * @return result.
		 */
		default Result<T> get(LogProperties props, List<String> keys) {
			for (String key : keys) {
				var r = get(props, key);
				@SuppressWarnings("null") // TODO eclipse bug
				@Nullable
				Result<T> found = switch (r) {
					case Result.Success<T> s -> s;
					case Result.Error<T> s -> s;
					case Result.Missing<T> s -> null;
				};
				if (found != null) {
					return found;
				}
			}
			return findRoot(this).missingResult(props, keys);
		}

		/**
		 * Converts the value into a String that can be parsed by the built-in properties
		 * parsing of types. Supported types: String, Boolean, Integer, URI, Map, List.
		 * @param value to be converted to string.
		 * @return property representation of value.
		 */
		String propertyString(T value);

		/**
		 * Determines full name of key.
		 * @param key key.
		 * @return fully qualified key name.
		 */
		default String fullyQualifiedKey(String key) {
			return findRoot(this).fullyQualifiedKey(key);
		}

		/**
		 * Validates the key is correctly prefixed.
		 * @param key dotted key.
		 */
		default void validateKey(String key) {
			String fqn = fullyQualifiedKey(key);
			if (!fqn.startsWith(LogProperties.ROOT_PREFIX)) {
				throw new IllegalArgumentException(
						"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'. key = " + key);
			}
			if (fqn.endsWith(".") || fqn.startsWith(".")) {
				throw new IllegalArgumentException(
						"Property key should not start or end with '" + LogProperties.SEP + "'");
			}
			LogProperties.validateKeyParameters(key, Set.of());
		}

		/**
		 * Creates a property builder with the given first key.
		 * @param key first key to try.
		 * @return builder to add more keys.
		 */
		default PropertyBuilder<T> withKey(String key) {
			return new PropertyBuilder<>(key, this);
		}

		/**
		 * Creates a Property from the given key and this getter.
		 * @param key key.
		 * @return property.
		 * @throws IllegalArgumentException if the key is malformed.
		 */
		default Property<T> build(String key) throws IllegalArgumentException {
			validateKey(key);
			return new DefaultProperty<>(this, List.of(key));
		}

		/**
		 * Creates a Property from the given key and this getter.
		 * @param key key.
		 * @param otherKeys additional keys to try.
		 * @return property.
		 * @throws IllegalArgumentException if the key is malformed.
		 */
		default Property<T> build(String key, String... otherKeys) throws IllegalArgumentException {
			List<String> keys = combine(key, otherKeys);
			for (var k : keys) {
				validateKey(k);
			}
			return new DefaultProperty<>(this, keys);
		}

		private static List<String> combine(String key, String... otherKeys) {
			List<String> keys = new ArrayList<>();
			keys.add(key);
			for (var o : otherKeys) {
				keys.add(o);
			}
			return keys.stream().distinct().toList();
		}

		/**
		 * Creates a Property from the given key and its {@value LogProperties#NAME}
		 * parameter.
		 * @param key key.
		 * @param name interpolates <code>{name}</code> in property name with this value.
		 * @return property.
		 */
		default Property<T> buildWithName(String key, String name) {
			var parameters = Map.of(LogProperties.NAME, name);
			LogProperties.validateKeyParameters(key, parameters.keySet());
			String fqn = LogProperties.interpolateKey(key, parameters);
			return build(fqn);
		}

		/**
		 * Find root property getter.
		 * @param e property getter.
		 * @return root.
		 */
		@SuppressWarnings("null") // TODO eclipse bug
		public static RootPropertyGetter findRoot(PropertyGetter<?> e) {
			return switch (e) {
				case RootPropertyGetter r -> r;
				case ChildPropertyGetter<?> c -> findRoot(c.parent());
			};
		}

		/**
		 * Default root property getter.
		 * @return property getter.
		 */
		public static RootPropertyGetter of() {
			return new RootPropertyGetter("", false);
		}

		/**
		 * A property getter and <strong>builder</strong> that has no conversion but may
		 * prefix the key and search recursively up the key path.
		 * <p>
		 * While you can map the string properties with {@link #map(PropertyFunction)} it
		 * is preferred to use the <code>to</code> methods on this class for basic types
		 * especially for list or map types as the implementation will call the
		 * implementation specific in LogProperties. An example is {@link #toMap()} which
		 * will call the LogProperties implementation of
		 * {@link LogProperties#mapOrNull(String)}.
		 *
		 * @param prefix added to the key before looking up in {@link LogProperties}.
		 * @param search if true will recursively search up the key path
		 */
		record RootPropertyGetter(String prefix, boolean search) implements PropertyGetter<String> {

			// @Override
			private @Nullable String valueOrNull(LogProperties props, String key) {
				if (search) {
					return props.findOrNull(prefix, key);
				}
				return props.valueOrNull(fullyQualifiedKey(key));
			}

			private <T> @Nullable T valueOrNull(LogProperties props, String key,
					BiFunction<LogProperties, String, @Nullable T> func) {
				if (search) {
					return props.findOrNull(prefix, key, func);
				}
				return func.apply(props, fullyQualifiedKey(key));
			}

			<T> Result<T> get(LogProperties props, String key, BiFunction<LogProperties, String, @Nullable T> func) {
				var v = valueOrNull(props, key, func);
				if (v == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success<>(v);
			}

			@Override
			public Result<String> get(LogProperties props, String key) {
				var v = valueOrNull(props, key);
				if (v == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success<>(v);
			}

			@Override
			public String propertyString(String value) {
				return value;
			}

			<T> Result.Error<T> errorResult(LogProperties props, String key, Exception e) {
				String resolvedKey = props.description(fullyQualifiedKey(key));
				String message = "Error for property. key: '" + resolvedKey + "', " + e.getMessage();
				return new Result.Error<>(resolvedKey, message, e);
			}

			<T> Result.Missing<T> missingResult(LogProperties props, List<String> keys) {
				List<String> resolvedKeys = describeKeys(props, keys);
				String message = "Property missing. keys: " + resolvedKeys;
				return new Result.Missing<>(message);
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
				return LogProperties.concatKey(prefix, key);
			}

			/**
			 * An integer property getter.
			 * @return getter that will convert to integers.
			 */
			public PropertyGetter<Integer> toInt() {
				return new FuncGetter<>(this, Integer::parseInt, String::valueOf);
			}

			/**
			 * A boolean property getter.
			 * @return getter that will convert to boolean.
			 */
			public PropertyGetter<Boolean> toBoolean() {
				return new FuncGetter<>(this, Boolean::parseBoolean, String::valueOf);
			}

			/**
			 * A Map property that will use {@link LogProperties#mapOrNull(String)}.
			 * @return getter that will convert string property to a Map.
			 */
			public PropertyGetter<Map<String, String>> toMap() {
				return new MapGetter(this);
			}

			/**
			 * A list property that will use {@link LogProperties#listOrNull(String)}.
			 * @return getter that will convert string property to a List.
			 */
			public PropertyGetter<List<String>> toList() {
				return new ListGetter(this);
			}

			/**
			 * An enum property getter using {@link Enum#valueOf(Class, String)}.
			 * @param <T> enum type.
			 * @param enumClass enum class.
			 * @return getter that will convert to enum type.
			 */
			public <T extends Enum<T>> PropertyGetter<T> toEnum(Class<T> enumClass) {
				return new FuncGetter<>(this, s -> Enum.valueOf(enumClass, s), String::valueOf);
			}

			/**
			 * A URI property getter that parses URIs.
			 * @return getter that will parse URIs.
			 */
			public PropertyGetter<URI> toURI() {
				return new FuncGetter<>(this, URI::new, String::valueOf);
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

			@SuppressWarnings("null") // TODO eclipse bug
			@Override
			default String propertyString(T value) {
				return switch (value) {
					case null -> throw new NullPointerException("null value passed into propertyString");
					case String s -> s;
					case Boolean b -> String.valueOf(b);
					case Integer i -> String.valueOf(i);
					case URI u -> String.valueOf(u);
					case Map<?, ?> m -> MapGetter._propertyString(m);
					case List<?> list -> ListGetter._propertyString(list);
					default -> throw new RuntimeException("Unable to convert to property string. value = " + value);
				};
			}

		}

		/**
		 * Sets up to converts a value.
		 * @param <U> value type
		 * @param mapper function.
		 * @return new property getter.
		 */
		default <U> PropertyGetter<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new FuncGetter<T, U>(this, mapper, null);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback fallback value.
		 * @return new property getter.
		 */
		default RequiredPropertyGetter<T> orElse(T fallback) {
			if (fallback == null) {
				throw new NullPointerException();
			}
			return new FallbackGetter<T>(this, () -> fallback);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback supplier.
		 * @return new property getter.
		 */
		default RequiredPropertyGetter<T> orElseGet(Supplier<? extends T> fallback) {
			Objects.requireNonNull(fallback, "fallback");
			return new FallbackGetter<T>(this, fallback);
		}

		/**
		 * A property getter that guarantees the result will not be missing usually
		 * because a non-null fallback is provided somewhere.
		 *
		 * @param <T> property type.
		 */
		public sealed interface RequiredPropertyGetter<T> extends ChildPropertyGetter<T> {

			/**
			 * This call unlike the parent returns a required result. {@inheritDoc}
			 */
			@Override
			RequiredResult<T> get(LogProperties props, String key);

			/**
			 * Gets a result from properties by trying a list of keys.
			 * @param props properties.
			 * @param keys list of property keys usually dotted.
			 * @return result.
			 */
			@Override
			default RequiredResult<T> get(LogProperties props, List<String> keys) {
				if (keys.isEmpty()) {
					throw new IllegalArgumentException("Keys is empty");
				}
				String key = keys.getFirst();
				return get(props, key);
			}

			/**
			 * Sets up to converts a value.
			 * @param <U> value type
			 * @param mapper function.
			 * @return new property getter.
			 */
			@Override
			default <U> RequiredPropertyGetter<U> map(
					PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
				return new RequiredFuncGetter<T, U>(this, mapper, null);
			}

			/**
			 * Creates a Property from the given key and this getter.
			 * @param key key.
			 * @return property.
			 * @throws IllegalArgumentException if the key is malformed.
			 */
			@Override
			default RequiredProperty<T> build(String key) throws IllegalArgumentException {
				validateKey(key);
				return new DefaultRequiredProperty<>(this, List.of(key));
			}

			/**
			 * Creates a Property from the given key and its {@value LogProperties#NAME}
			 * parameter.
			 * @param key key.
			 * @param name interpolates <code>{name}</code> in property name with this
			 * value.
			 * @return property.
			 */
			@Override
			default RequiredProperty<T> buildWithName(String key, String name) {
				var parameters = Map.of(LogProperties.NAME, name);
				LogProperties.validateKeyParameters(key, parameters.keySet());
				String fqn = LogProperties.interpolateKey(key, parameters);
				return build(fqn);
			}

			/**
			 * Creates a Property from the given key and this getter.
			 * @param key key.
			 * @param otherKeys other keys to try.
			 * @return property.
			 * @throws IllegalArgumentException if the key is malformed.
			 */
			@Override
			default RequiredProperty<T> build(String key, String... otherKeys) throws IllegalArgumentException {
				List<String> keys = combine(key, otherKeys);
				for (var k : keys) {
					validateKey(k);
				}
				return new DefaultRequiredProperty<>(this, keys);
			}

		}

	}

}

record DefaultProperty<T>(PropertyGetter<T> propertyGetter, List<String> keys) implements LogProperty.Property<T> {

	public DefaultProperty {
		Objects.requireNonNull(propertyGetter);
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("should have at least one key");
		}
	}

	/**
	 * Gets the first key.
	 * @return key.
	 */
	@Override
	public String key() {
		return keys.get(0);
	}

	@Override
	public Result<T> get(LogProperties properties) {
		return propertyGetter.get(properties, keys);
	}

	@Override
	public <U> LogProperty.Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		return new DefaultProperty<>(propertyGetter.map(mapper), keys);
	}

	@Override
	public String propertyString(T value) {
		return propertyGetter.propertyString(value);
	}

	@Override
	public void set(T value, BiConsumer<String, T> consumer) {
		if (value != null) {
			consumer.accept(key(), value);
		}
	}

}

record DefaultRequiredProperty<T>(RequiredPropertyGetter<T> propertyGetter,
		List<String> keys) implements LogProperty.RequiredProperty<T> {

	public DefaultRequiredProperty {
		Objects.requireNonNull(propertyGetter);
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("should have at least one key");
		}
	}

	/**
	 * Gets the first key.
	 * @return key.
	 */
	@Override
	public String key() {
		return keys.get(0);
	}

	@Override
	public RequiredResult<T> get(LogProperties properties) {
		return propertyGetter.get(properties, keys);
	}

	@Override
	public <U> LogProperty.RequiredProperty<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		return new DefaultRequiredProperty<>(propertyGetter.map(mapper), keys);
	}

	@Override
	public String propertyString(T value) {
		return propertyGetter.propertyString(value);
	}

	@Override
	public void set(T value, BiConsumer<String, T> consumer) {
		if (value != null) {
			consumer.accept(key(), value);
		}
	}

}

record FallbackGetter<T>(PropertyGetter<T> parent,
		Supplier<? extends T> fallback) implements RequiredPropertyGetter<T> {

	@SuppressWarnings("null") // TODO eclipse bug
	@Override
	public RequiredResult<T> get(LogProperties props, String key) {
		var r = parent.get(props, key);
		RequiredResult<T> req = switch (r) {
			case Result.Missing<T> m -> {
				var f = fallback.get();
				if (f == null) {
					yield PropertyGetter.findRoot(parent) //
						.errorResult(props, key, new LogProperty.PropertyMissingException("fallback returned null"));
				}
				yield new Result.Success<>(f);
			}
			case Result.Success<T> s -> s;
			case Result.Error<T> e -> e;
		};
		return req;
	}

	@Override
	public String propertyString(T value) {
		return parent.propertyString(value);
	}

}

record MapGetter(RootPropertyGetter parent) implements ChildPropertyGetter<Map<String, String>> {

	@Override
	public Result<Map<String, String>> get(LogProperties props, String key) {
		return parent.get(props, key, (p, k) -> p.mapOrNull(k));
	}

	@Override
	public String propertyString(Map<String, String> value) {
		return _propertyString(value);
	}

	static String _propertyString(Map<?, ?> value) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : value.entrySet()) {
			if (first) {
				first = true;
			}
			else {
				sb.append("&");
			}
			PercentCodec.encode(sb, String.valueOf(e.getKey()), StandardCharsets.UTF_8);
			Object v = e.getValue();
			if (v != null) {
				sb.append("=");
				PercentCodec.encode(sb, String.valueOf(v), StandardCharsets.UTF_8);
			}
		}
		return sb.toString();
	}

}

record ListGetter(RootPropertyGetter parent) implements ChildPropertyGetter<List<String>> {

	@Override
	public Result<List<String>> get(LogProperties props, String key) {
		return parent.get(props, key, (p, k) -> p.listOrNull(k));
	}

	@Override
	public String propertyString(List<String> list) {
		return _propertyString(list);
	}

	static String _propertyString(List<?> list) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : list) {
			if (first) {
				first = true;
			}
			else {
				sb.append(",");
			}
			PercentCodec.encode(sb, String.valueOf(e), StandardCharsets.UTF_8);
		}
		return sb.toString();
	}

}

record FuncGetter<T, R>(PropertyGetter<T> parent, PropertyFunction<? super T, ? extends R, ? super Exception> mapper,
		@Nullable PropertyFunction<? super R, ? extends String, ? super Exception> stringFunc)
		implements
			ChildPropertyGetter<R> {

	@Override
	public Result<R> get(LogProperties props, String key) {
		var result = parent.get(props, key);
		return switch (result) {
			case Result.Success<T> s -> {
				try {
					R r = mapper._apply(s.value());
					if (r == null) {
						yield PropertyGetter.findRoot(parent).missingResult(props, List.of(key));
					}
					yield new Result.Success<>(r);
				}
				catch (Exception e) {
					yield PropertyGetter.findRoot(parent).errorResult(props, key, e);
				}
			}
			case Result.Error<T> e -> e.convert();
			case Result.Missing<T> m -> m.convert();
		};
	}

	@Override
	public String propertyString(R value) {
		var f = stringFunc;
		if (f != null) {
			return f.apply(value);
		}
		return ChildPropertyGetter.super.propertyString(value);
	}

}

record RequiredFuncGetter<T, R>(RequiredPropertyGetter<T> parent,
		PropertyFunction<? super T, ? extends R, ? super Exception> mapper,
		@Nullable PropertyFunction<? super R, ? extends String, ? super Exception> stringFunc)
		implements
			RequiredPropertyGetter<R> {

	@Override
	public RequiredResult<R> get(LogProperties props, String key) {
		var result = parent.get(props, key);
		return switch (result) {
			case Result.Success<T> s -> {
				try {
					R r = mapper._apply(s.value());
					if (r == null) {
						throw new NullPointerException(
								"The property function returned a null. props=" + props + ", key=" + key);
					}
					yield new Result.Success<>(r);
				}
				catch (Exception e) {
					yield PropertyGetter.findRoot(parent).errorResult(props, key, e);
				}
			}
			case Result.Error<T> e -> e.convert();
		};
	}

	@Override
	public String propertyString(R value) {
		var f = stringFunc;
		if (f != null) {
			return f.apply(value);
		}
		return RequiredPropertyGetter.super.propertyString(value);
	}

}
