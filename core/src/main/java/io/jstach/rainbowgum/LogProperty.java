package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.FoundProperty;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyValue;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Sealed marker interface for the shared property machinery - {@link Result} (and its
 * {@link PropertyValue} supertype), {@link Validator}, and the property-specific
 * exceptions. Looking up and converting a property is done directly against
 * {@link LogProperties} via {@link LogKeyed} (see {@link LogProperties#forKey(String)}),
 * which resolves into a {@link Result}.
 *
 * @see LogKeyed
 * @see LogProperties
 */
@CaseChanging
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
	 * Property helper mixin.
	 */
	interface PropertySupport {

		/**
		 * String key value properties.
		 * @return properties.
		 */
		public LogProperties properties();

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
	 * Thrown by {@link Validator#validate()} when one or more of the accumulated
	 * {@link Result}s failed, reporting every failure in a single message instead of just
	 * the first one encountered.
	 */
	final static class ValidationException extends RuntimeException implements PropertyProblem {

		private static final long serialVersionUID = -8416817560218813225L;

		ValidationException(String message, @Nullable Throwable cause) {
			super(message, cause);
		}

		/**
		 * Validates a list of results and throws {@link ValidationException} if any
		 * results are not {@link Result.Success}.
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
	 * Accumulates {@link Result}s from one or more property lookups and validates them
	 * together, so that a caller resolving several properties in sequence can report
	 * every failure at once instead of failing fast on the first bad property.
	 * <p>
	 * This is the mechanism generated {@code fromProperties(LogProperties)} builder
	 * implementations use: each property is looked up and its {@link Result} is added to
	 * the validator as it is read; {@link #validate()} is then called once at the end,
	 * after all properties have been read, so a user who misconfigured several properties
	 * sees all of the problems together rather than fixing them one at a time across
	 * repeated runs.
	 * <p>
	 * Use {@link #add(Result)} for a property that is required: a {@link Result.Missing}
	 * is treated as a failure. Use {@link #addIfError(Result)} for an optional property
	 * that already has a fallback applied (typically via {@link Result#or(Object)} with
	 * the builder's current field value): a {@link Result.Missing} is expected in that
	 * case and is silently ignored, but a {@link Result.Error} (the property was present
	 * but failed to convert) is still a real problem and is reported.
	 *
	 * @apiNote a {@code Validator} is single-use: create one with {@link #of(Class)}, add
	 * every result that should be checked, and call {@link #validate()} exactly once at
	 * the end.
	 */
	public static class Validator {

		private final List<Result<?>> results = new ArrayList<>();

		private final Class<?> builder;

		/**
		 * Creates a validator whose {@link ValidationException} messages will be prefixed
		 * with the name of the given class.
		 * @param builder the class on whose behalf properties are being resolved -
		 * typically the builder calling this method. It is only used to identify the
		 * source of a failure in the exception message; no state is read from it.
		 * @return newly created validator.
		 */
		public static Validator of(Class<?> builder) {
			return new Validator(builder);
		}

		private Validator(Class<?> builder) {
			this.builder = builder;
		}

		/**
		 * Adds a result that must be present. Both {@link Result.Missing} and
		 * {@link Result.Error} will cause {@link #validate()} to fail. Use this for a
		 * required property; for an optional property that already has a fallback value
		 * applied, use {@link #addIfError(Result)} instead so a missing value does not
		 * itself trigger a failure.
		 * @param result result to check.
		 * @return this.
		 */
		public Validator add(Result<?> result) {
			results.add(result);
			return this;
		}

		/**
		 * Adds a result only if it is a {@link Result.Error}; a {@link Result.Missing}
		 * result is silently ignored. Intended for an optional property that has already
		 * had a fallback value applied (for example via {@link Result#or(Object)}): the
		 * property being absent is expected and fine, but if it was present and failed to
		 * convert that should not be swallowed.
		 * @param result result to check.
		 * @return this.
		 */
		public Validator addIfError(Result<?> result) {
			if (result instanceof Result.Error<?> e) {
				results.add(e);
			}
			return this;
		}

		/**
		 * Validates every result added so far via {@link #add(Result)} or
		 * {@link #addIfError(Result)}, throwing a single exception listing all failures
		 * if one or more of them are not {@link Result.Success}.
		 * @throws ValidationException if any added result is a {@link Result.Missing}
		 * (added via {@link #add(Result)}) or a {@link Result.Error} (added via either
		 * method).
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
	 */
	interface PropertyValue<T> extends Supplier<Result<T>> {

		/**
		 * Gets result.
		 * @return result.
		 */
		@Override
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

}
