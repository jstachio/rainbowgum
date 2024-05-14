package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

import io.jstach.rainbowgum.LogProperties.FoundProperty;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyGetter.ChildPropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyGetter.RootPropertyGetter;
import io.jstach.rainbowgum.LogProperty.PropertyValue;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.Result.Success;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * A single property or value.
 */
@CaseChanging
public sealed interface LogProperty {

	/**
	 * Builder
	 * @return property getter builder.
	 */
	public static RootPropertyGetter builder() {
		return Property.builder();
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

	/**
	 * Parent interface for property exceptions.
	 */
	sealed interface PropertyProblem {

	}

	/**
	 * Property helper mixin.
	 */
	interface PropertySupport {

		/**
		 * String key value properties.
		 * @return properties.
		 */
		public LogProperties properties();

		/**
		 * Gets the value for the property.
		 * @param <T> property type.
		 * @param property to use for lookup.
		 * @return value.
		 */
		default <T> T value(Property<T> property) {
			return property.get(properties()).value();
		}

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
	 * Thrown if any {@link Result} is not successful for combined valitation of many
	 * properties.
	 */
	final static class ValidationException extends RuntimeException implements PropertyProblem {

		private static final long serialVersionUID = -8416817560218813225L;

		ValidationException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}

		/**
		 * Validates a list of results and throws {@link ValidationException} if any
		 * results are not {@link Success}.
		 * @param builder class to prefix to full message.
		 * @param results list of results.
		 * @throws ValidationException if any result in the supplied list is not
		 * successful.
		 */
		public static void validate(Class<?> builder, List<Result<?>> results) throws ValidationException {
			StringBuilder sb = new StringBuilder().append("Validation failed for ")
				.append(builder.getName())
				.append(":");
			Throwable cause = null;
			boolean failed = false;
			for (var r : results) {
				switch (r) {
					case Result.Success<?> s -> {
						continue;
					}
					case Result.Missing<?> m -> {
						sb.append("\n");
						sb.append(m.message());
						failed = true;
					}
					case Result.Error<?> e -> {
						sb.append("\n");
						sb.append(e.message());
						if (cause == null) {
							cause = e.cause();
						}
						failed = true;
					}
				}
			}
			if (failed) {
				throw new ValidationException(sb.toString(), cause);
			}

		}

	}

	/**
	 * A builder that Validates results.
	 */
	public static class Validator {

		private final List<Result<?>> results = new ArrayList<>();

		private final Class<?> builder;

		/**
		 * Creates a validator based on a builder.
		 * @param builder builder class
		 * @return newly created validator
		 */
		public static Validator of(Class<?> builder) {
			return new Validator(builder);
		}

		private Validator(Class<?> builder) {
			this.builder = builder;
		}

		/**
		 * Adds a result to check.
		 * @param result result to check.
		 * @return this
		 */
		public Validator add(Result<?> result) {
			results.add(result);
			return this;
		}

		/**
		 * Adds a result to check only if it is error.
		 * @param result result to check.
		 * @return this
		 */
		public Validator addIfError(Result<?> result) {
			if (result instanceof Result.Error<?> e) {
				results.add(e);
			}
			return this;
		}

		/**
		 * Validates the currently added results and if any are error or missing an
		 * exception will be thrown.
		 * @throws ValidationException if validation fails.
		 */
		public void validate() throws ValidationException {
			ValidationException.validate(builder, results);
		}

	}

	/**
	 * A supplier of a property result. Can be lazy but does not have to be. A non lazy
	 * PropertyValue is {@link Result}.
	 *
	 * @param <T> property type.
	 * @see Result
	 * @see Property#bind(LogProperties)
	 */
	interface PropertyValue<T> {

		/**
		 * Gets result.
		 * @return result.
		 */
		public Result<T> get();

		/**
		 * Gets the value and will fail with {@link NoSuchElementException} if there is no
		 * value.
		 * @return value.
		 * @throws PropertyMissingException if there is no value.
		 * @throws PropertyConvertException if the property failed conversion.
		 */
		default T value() throws PropertyMissingException, PropertyConvertException {
			return get().value();
		}

		/**
		 * Returns the current result if fallback is null or returns fallback as a result
		 * if this result is missing.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		public PropertyValue<T> or(@Nullable T fallback);

		/**
		 * Returns the current result if success or error otherwise fallback supplier is
		 * used. If the supplier returns <code>null</code> then the result will be mising.
		 * @param fallback may return <code>null</code> but not recommended.
		 * @return value.
		 */
		public PropertyValue<T> or(Supplier<T> fallback);

		/**
		 * Overrides the result with a value if it is not <code>null</code> regardless if
		 * the original Result is an error or not. <strong>This overrides even an error
		 * result from {@link #get()}!</strong> This is equivalent to
		 * <code>replacement == null ? value.value() : replacement;</code>.
		 * @param replacement value to use to override.
		 * @return result with replacement if it is not null.
		 */
		default T override(@Nullable T replacement) {
			if (replacement != null) {
				return replacement;
			}
			return value();
		}

		/**
		 * Map a result
		 * @param <U> result type
		 * @param mapper mapping function.
		 * @return mapped result.
		 */
		public <U> PropertyValue<U> map(PropertyFunction<T, U, ? super Exception> mapper);

	}

	/**
	 * The result of a property fetched from properties.
	 *
	 * @param <T> property type.
	 */
	sealed interface Result<T> extends PropertyValue<T> {

		@Override
		default Result<T> get() {
			return this;
		}

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
		@Override
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
		 * Returns the current result if fallback is null or returns fallback as a result
		 * if this result is missing.
		 * @param fallback maybe <code>null</code>.
		 * @return value.
		 */
		@Override
		public Result<T> or(@Nullable T fallback);

		/**
		 * Returns the current result if success or error otherwise fallback supplier is
		 * used. If the supplier returns <code>null</code> then the result will be mising.
		 * @param fallback may return <code>null</code> but not recommended.
		 * @return value.
		 */
		@Override
		public Result<T> or(Supplier<T> fallback);

		/**
		 * Map a result
		 * @param <U> result type
		 * @param mapper mapping function.
		 * @return mapped result.
		 */
		@Override
		public <U> Result<U> map(PropertyFunction<T, U, ? super Exception> mapper);

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
		 * A description of the result for error messages.
		 * @return description.
		 */
		public String describe();

		/**
		 * A property that is present.
		 *
		 * @param <T> property type.
		 */
		public sealed interface Success<T> extends RequiredResult<T> {

			@Override
			default @Nullable T valueOrNull() {
				return value();
			}

			/**
			 * Gets the value and will never fail for Success.
			 * @return value.
			 */
			@Override
			public T value();

			@Override
			default T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback) {
				return value();
			}

			@Override
			default Success<T> or(@Nullable T fallback) {
				return this;
			}

			@Override
			default Success<T> or(Supplier<T> fallback) {
				return this;
			}

			/**
			 * Original property key where the value was derived from or was passed a
			 * fallback.
			 * @return key.
			 */
			public String key();

			/**
			 * A property that was not found in properties but had a fallback value
			 * supplied.
			 *
			 * @param <T> property type.
			 * @param key original property key.
			 * @param value actual value.
			 */
			public record ValueSuccess<T>(String key, T value) implements Success<T> {
				/**
				 * Successfully found property value.
				 * @param key key of the original property.
				 * @param value actual value should not be <code>null</code>.
				 */
				public ValueSuccess {
					if (value == null) {
						throw new NullPointerException("value");
					}
				}

				@Override
				public <U> Result<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
					try {
						U u = mapper._apply(value);
						return new ValueSuccess<>(key, u);
					}
					catch (Exception e) {
						return Error.of(key, e);
					}
				}

				@Override
				public String describe() {
					return "Fallback[" + key + "]=" + value;
				}

			}

			/**
			 * A property that is present.
			 *
			 * @param <T> property type.
			 * @param value actual value.
			 * @param property found property.
			 */
			public record PropertySuccess<T>(FoundProperty property, T value) implements Success<T> {
				/**
				 * Successfully found property value.
				 * @param value actual value should not be <code>null</code>.
				 * @param property found property.
				 */
				public PropertySuccess {
					if (value == null) {
						throw new NullPointerException("value");
					}
				}

				@Override
				public String key() {
					return property.key();
				}

				@Override
				public <U> Result<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
					try {
						U u = mapper._apply(value);
						return new PropertySuccess<>(property, u);
					}
					catch (Exception e) {
						return Error.of(property.key(), e);
					}
				}

				@Override
				public String describe() {
					return "Property[" + property.key() + "]=" + property.valueDescription();
				}

			}

		}

		/**
		 * A property that is missing (<code>null</code>).
		 *
		 * @param <T> property type.
		 * @param keys keys.
		 * @param message description of where the property is missing.
		 */
		public record Missing<T>(List<String> keys, String message) implements Result<T> {
			/**
			 * A property that is missing (<code>null</code>).
			 * @param keys keys.
			 * @param message description of where the property is missing.
			 */
			public Missing {
				if (keys.isEmpty()) {
					throw new IllegalArgumentException("one key is required");
				}
			}

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

			@Override
			public Result<T> or(@Nullable T fallback) {
				if (fallback != null) {
					return new Success.ValueSuccess<>(keys.get(0), fallback);
				}
				return this;
			}

			@Override
			public Result<T> or(Supplier<T> fallback) {
				return or(fallback.get());
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

			@Override
			public <U> Missing<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
				return convert();
			}

			@Override
			public String describe() {
				return "Missing[" + keys + "]";
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
			public T value() throws PropertyConvertException {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public T value(@SuppressWarnings("exports") Supplier<? extends @Nullable T> fallback)
					throws PropertyConvertException {
				throw new PropertyConvertException(key, message, cause);
			}

			@Override
			public Error<T> or(@Nullable T fallback) {
				return this;
			}

			@Override
			public Error<T> or(Supplier<T> fallback) {
				return this;
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

			@Override
			public <U> Error<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
				return convert();
			}

			static <U> Error<U> of(String resolvedKey, Exception cause) {
				String message = "Error for property. key: " + resolvedKey + ", " + cause.getMessage();
				return new Error<U>(resolvedKey, message, cause);
			}

			@Override
			public String describe() {
				return "Error[" + key + "](" + message + ")";
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
	 * @see Result
	 */
	sealed interface Property<T> extends LogProperty {

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

		/*
		 * TODO do we really need propertyString?
		 */
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

		/**
		 * Creates a memoized supplier for result from the properties.
		 * @param properties properties
		 * @return lazy supplier of result of property based on passed in properties.
		 */
		default PropertyValue<T> bind(LogProperties properties) {
			return new MemoizingValue<>(() -> get(properties));
		}

	}

	/**
	 * Builds property keys.
	 *
	 * @param <B> this.
	 */
	sealed abstract class AbstractKeysBuilder<B> {

		private List<PartialKey> keys = new ArrayList<>();

		private Map<String, String> params = new LinkedHashMap<>();

		AbstractKeysBuilder() {
		}

		/**
		 * Adds an additional key to try.
		 * @param key key to try.
		 * @return this
		 */
		public B addKey(String key) {
			keys.add(new PartialKey(key, new LinkedHashMap<>()));
			return self();
		}

		/**
		 * Adds a fallback paramter that will be used on all keys.
		 * @param name parameter name.
		 * @param value value of parameter.
		 * @return this
		 */
		public B addFallbackParam(String name, String value) {
			params.put(name, value);
			return self();
		}

		/**
		 * Adds a key with a <code>{name}</code> parameter.
		 * @param key key to be interpolated.
		 * @param name name.
		 * @return this
		 */
		public B addKeyWithName(String key, String name) {
			addKey(key);
			return addNameParam(name);
		}

		/**
		 * Adds a parameter to the last added key
		 * @param name parameter name.
		 * @param value value of parameter.
		 * @return this.
		 */
		public B addParam(String name, String value) {
			var key = keys.getLast();
			key.params.put(name, value);
			return self();
		}

		/**
		 * Adds a a <code>{name}</code> parameter the last added key.
		 * @param value value of parameter.
		 * @return this.
		 */
		public B addNameParam(String value) {
			return addParam(LogProperties.NAME, value);

		}

		PartialKeys buildKeys() {
			return new PartialKeys(keys, params);
		}

		record PartialKey(String key, Map<String, String> params) {
			PartialKey makeImmutable() {
				return new PartialKey(key, Map.copyOf(params));
			}

			String interpolate(Function<String, @Nullable String> fallback) {
				return LogProperties.interpolateKey(key, fallback(params::get, fallback));
			}
		}

		record PartialKeys(List<PartialKey> keys, Map<String, String> params) {

			PartialKeys {
				keys = keys.stream().map(PartialKey::makeImmutable).toList();
				params = Map.copyOf(params);
			}

			List<String> interpolate(Function<String, @Nullable String> paramFunction) {
				return keys.stream().map(e -> {
					return e.interpolate(fallback(paramFunction, params::get));
				}).distinct().toList();
			}

			List<String> interpolate() {
				return keys.stream().map(e -> {
					return e.interpolate(params::get);
				}).distinct().toList();
			}
		}

		abstract B self();

	}

	private static Function<String, @Nullable String> fallback(Function<String, @Nullable String> a,
			Function<String, @Nullable String> b) {
		return k -> {
			var v = a.apply(k);
			if (v != null)
				return v;
			return b.apply(k);
		};
	}

	/**
	 * Property Builder that can have keys added to try.
	 *
	 * @param <T> value type.
	 */
	final class PropertyKeyBuilder<T> extends AbstractKeysBuilder<PropertyKeyBuilder<T>> {

		private final PropertyGetter<T> getter;

		private PropertyKeyBuilder(PropertyGetter<T> getter) {
			this.getter = getter;
		}

		/**
		 * Builds the property with the supplied keys.
		 * @return property
		 */
		public Property<T> build() {
			return property(buildKeys().interpolate());
		}

		private Property<T> property(List<String> keys) {
			return getter.build(keys.get(0), keys.subList(1, keys.size()).toArray(new String[] {}));
		}

		/**
		 * Creates a provider that will replace <code>{name}</code> property key
		 * parameters.
		 * @param <U> provider type.
		 * @param mapper function to maper property value to provider type.
		 * @return provider.
		 */
		public <U> LogProvider<U> provider(PropertyFunction<T, LogProvider<U>, ? super Exception> mapper) {
			return (n, c) -> {
				Map<String, String> named = Map.of(LogProperties.NAME, n);
				return property(buildKeys().interpolate(named::get)).get(c.properties())
					.map(mapper)
					.value()
					.provide(n, c);
			};
		}

		@Override
		protected PropertyKeyBuilder<T> self() {
			return this;
		}

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
		default PropertyKeyBuilder<T> withKey(String key) {
			return new PropertyKeyBuilder<>(this).addKey(key);
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
			return withKey(key).addNameParam(name).build();
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
			return new RootPropertyGetter("");
		}

		/**
		 * A property getter and <strong>builder</strong> that has no conversion but may
		 * prefix the key and search recursively up the key path.
		 * <p>
		 * While you can map the string properties with {@link #map(PropertyFunction)} it
		 * is preferred to use the <code>of</code> methods on this class for basic types
		 * especially for list or map types as the implementation will call the
		 * implementation specific in LogProperties. An example is {@link #ofMap()} which
		 * will call the LogProperties implementation of
		 * {@link LogProperties#mapOrNull(String)}.
		 *
		 */
		public sealed class RootPropertyGetter implements PropertyGetter<String> {

			/**
			 * Prefix to be added to key.
			 */
			protected final String prefix;

			RootPropertyGetter(String prefix) {
				super();
				this.prefix = prefix;
			}

			private final static class SearchPropertyGetter extends RootPropertyGetter {

				SearchPropertyGetter(String prefix) {
					super(prefix);
				}

				@Override
				<T extends @Nullable Object> @Nullable T findOrNull(LogProperties props, String key,
						BiFunction<LogProperties, String, @Nullable T> func) {
					return props.findOrNull(prefix, key, func);
				}

				/**
				 * Will prefix key.
				 * @param prefix prefix.
				 * @return new property getter.
				 */
				@Override
				public SearchPropertyGetter withPrefix(String prefix) {
					return new SearchPropertyGetter(prefix);
				}

			}

			<T extends @Nullable Object> @Nullable T findOrNull(LogProperties props, String key,
					BiFunction<LogProperties, String, @Nullable T> func) {
				return func.apply(props, fullyQualifiedKey(key));
			}

			FoundProperty.@Nullable StringProperty stringOrNull(LogProperties props, String key) {
				return findOrNull(props, key, LogProperties::stringPropertyOrNull);
			}

			FoundProperty.@Nullable ListProperty listOrNull(LogProperties props, String key) {
				return findOrNull(props, key, LogProperties::listPropertyOrNull);
			}

			FoundProperty.@Nullable MapProperty mapOrNull(LogProperties props, String key) {
				return findOrNull(props, key, LogProperties::mapPropertyOrNull);
			}

			static <T, R> Success<R> convert(Success<T> success, R value) {
				return switch (success) {
					case Success.PropertySuccess<T> ps -> new Success.PropertySuccess<R>(ps.property, value);
					case Success.ValueSuccess<T> vs -> new Success.ValueSuccess<R>(vs.key(), value);
				};
			}

			@SuppressWarnings("null") // TODO eclipse null bug
			Result<List<String>> getList(LogProperties props, String key) {
				FoundProperty.@Nullable ListProperty prop = listOrNull(props, key);
				if (prop == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success.PropertySuccess<>(prop, prop.value());
			}

			@SuppressWarnings("null") // TODO eclipse null bug
			Result<Map<String, String>> getMap(LogProperties props, String key) {
				FoundProperty.@Nullable MapProperty prop = mapOrNull(props, key);
				if (prop == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success.PropertySuccess<>(prop, prop.value());
			}

			@SuppressWarnings("null") // TODO eclipse null bug
			@Override
			public Result<String> get(LogProperties props, String key) {
				var v = stringOrNull(props, key);
				if (v == null) {
					return missingResult(props, List.of(key));
				}
				return new Result.Success.PropertySuccess<>(v, v.value());
			}

			@Override
			public String propertyString(String value) {
				return value;
			}

			<T> Result.Error<T> errorResult(LogProperties props, String fqk, Exception e) {
				String resolvedKey = props.description(fqk);
				return Result.Error.of(resolvedKey, e);
			}

			<T> Result.Error<T> errorResult(LogProperties props, String key, Exception e, Success<?> previousResult) {
				@SuppressWarnings("null")
				FoundProperty fp = switch (previousResult) {
					case Success.ValueSuccess<?> vs -> null;
					case Success.PropertySuccess<?> ps -> ps.property();
				};
				String fqk = fullyQualifiedKey(key);
				if (fp == null) {
					return errorResult(props, fqk, e);
				}
				var badProps = fp.properties();
				String resolvedKey = "'" + fqk + "' from " + badProps.description(fqk);
				String message;
				if (e instanceof PropertyConvertException || e instanceof ValidationException) {
					message = "Error converting property. key: " + resolvedKey + ", value: '" + fp.valueDescription()
							+ "' cause:\n" + e.getMessage();
				}
				else {
					message = "Error for property. key: " + resolvedKey + ", " + e.getClass().getName() + " "
							+ e.getMessage();
				}

				message += "\nTried: '" + fqk + "' from " + props.description(fqk);

				return new Result.Error<>(resolvedKey, message, e);
			}

			<T> Result.Missing<T> missingResult(LogProperties props, List<String> keys) {
				List<String> resolvedKeys = describeKeys(props, keys);
				String message = "Property missing. keys: " + resolvedKeys;
				return new Result.Missing<>(keys, message);
			}

			List<String> describeKeys(LogProperties props, List<String> keys) {
				return keys.stream().<String>map(k -> {
					String fqk = fullyQualifiedKey(k);
					return "'" + fqk + "' from " + props.description(fullyQualifiedKey(k));
				}).toList();
			}

			/**
			 * Will search with prefix.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withSearch(String prefix) {
				return new SearchPropertyGetter(prefix);
			}

			/**
			 * Will prefix key.
			 * @param prefix prefix.
			 * @return new property getter.
			 */
			public RootPropertyGetter withPrefix(String prefix) {
				return new RootPropertyGetter(prefix);
			}

			@Override
			public String fullyQualifiedKey(String key) {
				return LogProperties.concatKey(prefix, key);
			}

			/**
			 * An integer property getter.
			 * @return getter that will convert to integers.
			 */
			public PropertyGetter<Integer> ofInt() {
				return map(Integer::parseInt);
			}

			/**
			 * A boolean property getter.
			 * @return getter that will convert to boolean.
			 */
			public PropertyGetter<Boolean> ofBoolean() {
				return map(Boolean::parseBoolean);
			}

			/**
			 * A Map property that will use {@link LogProperties#mapOrNull(String)}.
			 * @return getter that will convert string property to a Map.
			 */
			public PropertyGetter<Map<String, String>> ofMap() {
				return new MapGetter(this);
			}

			/**
			 * A list property that will use {@link LogProperties#listOrNull(String)}.
			 * @return getter that will convert string property to a List.
			 */
			public PropertyGetter<List<String>> ofList() {
				return new ListGetter(this);
			}

			/**
			 * A URI property getter that parses URIs.
			 * @return getter that will parse URIs.
			 */
			public PropertyGetter<URI> ofURI() {
				return map(URI::new);
			}

			/**
			 * A URI based provider reference for component provision.
			 * @return getter that will parse provider ref
			 * @see LogOutput#of(LogProviderRef)
			 */
			@SuppressWarnings("null") // TODO eclipse bug
			public PropertyGetter<LogProviderRef> ofProviderRef() {
				return ofURI().mapResult(r -> LogProviderRef.of(r));
			}

			/**
			 * A URI based provider reference for component provision.
			 * @param <U> provider type
			 * @param mapper function to map to provider.
			 * @return getter that will parse provider ref
			 * @see LogOutput#of(LogProviderRef)
			 */
			@SuppressWarnings("null") // TODO eclipse bug
			public <U> PropertyGetter<LogProvider<U>> ofProvider(
					PropertyFunction<? super LogProviderRef, LogProvider<U>, ? super Exception> mapper) {
				return ofURI().mapResult(r -> LogProviderRef.of(r)).map(mapper);
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
			PropertyFunction<Result.Success<T>, ? extends U, ? super Exception> resultMapper = r -> mapper
				.apply(r.value());
			return mapResult(resultMapper);
		}

		/**
		 * Sets up to converts a value.
		 * @param <U> value type
		 * @param mapper function.
		 * @return new property getter.
		 */
		default <U> PropertyGetter<U> mapResult(
				PropertyFunction<Result.Success<T>, ? extends U, ? super Exception> mapper) {
			return new ResultFuncGetter<T, U>(this, mapper, null);
		}

		/**
		 * Fallback to value if property not found.
		 * @param fallback fallback value.
		 * @return new property getter.
		 */
		default PropertyGetter<T> orElse(T fallback) {
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
		default PropertyGetter<T> orElseGet(Supplier<? extends T> fallback) {
			Objects.requireNonNull(fallback, "fallback");
			return new FallbackGetter<T>(this, fallback);
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

enum NoProperty implements LogProperty {

}

final class MemoizingValue<T> implements LogProperty.PropertyValue<T> {

	final Supplier<Result<T>> delegate;

	transient volatile boolean initialized;

	// "value" does not need to be volatile; visibility piggy-backs
	// on volatile read of "initialized".
	transient @Nullable Result<T> value = null;

	MemoizingValue(Supplier<Result<T>> delegate) {
		this.delegate = delegate;
	}

	@SuppressWarnings({"nullness", "null"})
	@Override
	public Result<T> get() {
		// A 2-field variant of Double Checked Locking.
		
		if (!initialized) {
			synchronized (this) {
				if (!initialized) {
					Result<T> t = Objects.requireNonNull(delegate.get());
					value = t;
					initialized = true;
					return t;
				}
			}
		}
		return value;
	}

	@Override
	public String toString() {
		return "PropertyValue.memoize(" + delegate + ")";
	}

	@Override
	public PropertyValue<T> or(@Nullable T fallback) {
		return get().or(fallback);
	}

	@Override
	public PropertyValue<T> or(Supplier<T> fallback) {
		return get().or(fallback);
	}

	@Override
	public <U> PropertyValue<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
		return get().map(mapper);
	}

}

record FallbackGetter<T>(PropertyGetter<T> parent, Supplier<? extends T> fallback) implements ChildPropertyGetter<T> {

	@Override
	public String propertyString(T value) {
		return parent.propertyString(value);
	}

	@Override
	public Result<T> get(LogProperties props, String key) {
		return get(props, List.of(key));
	}

	@SuppressWarnings("null")
	@Override
	public Result<T> get(LogProperties props, List<String> keys) {

		Result<T> r = parent.get(props, keys);
		return switch (r) {
			case Result.Success<T> s -> s;
			case Result.Error<T> s -> s;
			case Result.Missing<T> s -> {
				if (keys.isEmpty()) {
					yield s;
				}
				String key = keys.getFirst();
				var f = fallback.get();
				if (f == null) {
					yield PropertyGetter.findRoot(parent) //
						.errorResult(props, keys.get(0),
								new LogProperty.PropertyMissingException("fallback returned null"));
				}
				yield new Result.Success.ValueSuccess<>(key, f);
			}
		};

	}

}

record MapGetter(RootPropertyGetter parent) implements ChildPropertyGetter<Map<String, String>> {

	@Override
	public Result<Map<String, String>> get(LogProperties props, String key) {
		return parent.getMap(props, key);
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
		return parent.getList(props, key);
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

record ResultFuncGetter<T, R>(PropertyGetter<T> parent,
		PropertyFunction<Result.Success<T>, ? extends R, ? super Exception> mapper,
		@Nullable PropertyFunction<? super R, ? extends String, ? super Exception> stringFunc)
		implements
			ChildPropertyGetter<R> {

	@Override
	@SuppressWarnings("nullness") // TODO checker seems to have issues with pattern
									// matching.
	public Result<R> get(LogProperties props, String key) {
		var result = parent.get(props, key);
		Result<R> rvalue = switch (result) {
			case Result.Success<T> s -> {
				try {
					R r = mapper._apply(s);
					if (r == null) {
						yield PropertyGetter.findRoot(parent).missingResult(props, List.of(key));
					}
					else {
						yield RootPropertyGetter.convert(s, Objects.requireNonNull(r));
					}
				}
				catch (Exception e) {
					yield PropertyGetter.findRoot(parent).errorResult(props, key, e, s);
				}
			}
			case Result.Error<T> e -> e.convert();
			case Result.Missing<T> m -> m.convert();
			case null -> throw new NullPointerException("bug");
		};
		return rvalue;
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
