package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Describes a key (or several fallback keys) resolved against a {@link LogProperties}
 * instance - start with {@link LogProperties#forKey(String)} - and also serves as the
 * namespace for the shared property machinery: {@link Result} (and its
 * {@link PropertyValue} supertype), {@link Validator}, and the property-specific
 * exceptions.
 *
 * @see LogProperties
 */
@CaseChanging
public interface LogProperty {

	/**
	 * The first key tried.
	 * @return key.
	 */
	public String key();

	/**
	 * Every key tried, in order - the first key given to
	 * {@link LogProperties#forKey(String)}/{@link LogProperties#forKey(String, String)}
	 * followed by any added via {@link #or(String)}/{@link #or(String, String)}.
	 * @return keys.
	 */
	public List<String> keys();

	/**
	 * The properties this key (or keys) will be looked up against.
	 * @return properties.
	 */
	public LogProperties properties();

	/**
	 * Resolves the property as a raw string.
	 * @return result.
	 */
	public Result<String> ofString();

	/**
	 * Resolves the property as a list, using {@link LogProperties#listOrNull(String)}.
	 * @return result.
	 */
	public Result<List<String>> ofList();

	/**
	 * Resolves the property as a map, using {@link LogProperties#mapOrNull(String)}.
	 * @return result.
	 */
	public Result<Map<String, String>> ofKeyValues();

	/**
	 * Resolves the property as an int.
	 * @return result.
	 */
	default Result<Integer> ofInt() {
		return ofString().convert(properties(), Integer::parseInt);
	}

	/**
	 * Resolves the property as a boolean.
	 * @return result.
	 */
	default Result<Boolean> ofBoolean() {
		return ofString().convert(properties(), Boolean::parseBoolean);
	}

	/**
	 * Resolves the property as a URI.
	 * @return result.
	 */
	default Result<URI> ofURI() {
		return ofString().convert(properties(), URI::new);
	}

	/**
	 * Resolves the property as a {@link LogProviderRef} for component provision.
	 * @return result.
	 * @see LogOutput#of(LogProviderRef)
	 */
	default Result<LogProviderRef> ofProviderRef() {
		return switch (ofURI()) {
			case Result.Success<URI> s -> mapValue(s, LogProviderRef.of(s.value(), s.key()));
			case Result.Missing<URI> m -> m.convert();
			case Result.Error<URI> e -> e.convert();
		};
	}

	/**
	 * Resolves the property as a {@link LogProviderRef} and then maps it to a
	 * {@link LogProvider}.
	 * @param <U> provider type.
	 * @param mapper function to map to provider.
	 * @return result.
	 * @see LogOutput#of(LogProviderRef)
	 */
	default <U> Result<LogProvider<U>> ofProvider(
			PropertyFunction<LogProviderRef, LogProvider<U>, ? super Exception> mapper) {
		return ofProviderRef().convert(properties(), mapper);
	}

	/**
	 * Rewraps a value that was already derived from success's value, preserving whether
	 * success was a {@link Result.Success.PropertySuccess} (and so which underlying
	 * property it originally came from) or a {@link Result.Success.ValueSuccess} - useful
	 * for a conversion step that cannot itself throw, so
	 * {@link Result#convert(LogProperties, PropertyFunction)} would be overkill, but that
	 * still needs to keep the result's origin intact for a later conversion step's error
	 * message.
	 * @param <T> success's value type.
	 * @param <U> new value type.
	 * @param success success to take the origin from.
	 * @param value already-converted value to wrap.
	 * @return new success of the same shape as success, wrapping value.
	 */
	static <T, U> Result.Success<U> mapValue(Result.Success<T> success, U value) {
		return switch (success) {
			case Result.Success.PropertySuccess<T> ps -> new Result.Success.PropertySuccess<>(ps.property(), value);
			case Result.Success.ValueSuccess<T> vs -> new Result.Success.ValueSuccess<>(vs.key(), value);
		};
	}

	private static <U> Result.Error<U> richError(LogProperties properties, Result.Success<?> previousResult,
			Exception e) {
		String fqk = previousResult.key();
		FoundProperty fp = switch (previousResult) {
			case Result.Success.ValueSuccess<?> vs -> null;
			case Result.Success.PropertySuccess<?> ps -> ps.property();
		};
		if (fp == null) {
			String resolvedKey = properties.description(fqk);
			String message = "Error for property. key: " + resolvedKey + ", " + e.getMessage();
			return new Result.Error<>(resolvedKey, message, e);
		}
		var badProps = fp.properties();
		String resolvedKey = "'" + fqk + "' from " + badProps.description(fqk);
		String message;
		if (e instanceof PropertyConvertException || e instanceof ValidationException) {
			message = "Error converting property. key: " + resolvedKey + ", value: '" + fp.valueDescription()
					+ "' cause:\n" + e.getMessage();
		}
		else {
			message = "Error for property. key: " + resolvedKey + ", " + e.getClass().getName() + " " + e.getMessage();
		}
		message += "\nTried: '" + fqk + "' from " + properties.description(fqk);
		return new Result.Error<>(resolvedKey, message, e);
	}

	/**
	 * Checks that value is not null, throwing {@link PropertyMissingException} naming key
	 * if it is. Useful for a property that is required but was set programmatically (e.g.
	 * a builder setter) rather than resolved through {@link #ofString()}/etc, so there is
	 * no {@link Result} to check for missing-ness.
	 * @param <T> value type.
	 * @param key property key, used only in the exception message.
	 * @param value value to check.
	 * @return value, never null.
	 * @throws PropertyMissingException if value is null.
	 */
	static <T> T require(String key, @Nullable T value) {
		if (value == null) {
			throw new PropertyMissingException("Value is required not null. property key='" + key + "'");
		}
		return value;
	}

	/**
	 * Converts a value into a String that can be parsed back by {@link LogProperties}'s
	 * built-in property parsing. Supported types: String, Boolean, Integer, URI, Map,
	 * List.
	 * @param value value to convert, must not be null.
	 * @return property-string representation of value.
	 */
	static String propertyString(Object value) {
		return switch (value) {
			case String s -> s;
			case Boolean b -> String.valueOf(b);
			case Integer i -> String.valueOf(i);
			case URI u -> String.valueOf(u);
			case Map<?, ?> m -> mapPropertyString(m);
			case List<?> list -> listPropertyString(list);
			default -> throw new RuntimeException("Unable to convert to property string. value = " + value);
		};
	}

	private static String mapPropertyString(Map<?, ?> value) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : value.entrySet()) {
			if (first) {
				first = false;
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

	private static String listPropertyString(List<?> list) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (var e : list) {
			if (first) {
				first = false;
			}
			else {
				sb.append(",");
			}
			PercentCodec.encode(sb, String.valueOf(e), StandardCharsets.UTF_8);
		}
		return sb.toString();
	}

	/**
	 * Adds a fallback key to try (without <code>{name}</code> interpolation) if none of
	 * the keys tried so far are found. Keys already added are tried first, in order; this
	 * one is tried last.
	 * @param fallbackKey additional key to try.
	 * @return a new LogProperty trying this key's keys followed by fallbackKey.
	 */
	default LogProperty or(String fallbackKey) {
		validateKey(fallbackKey);
		return withKey(fallbackKey);
	}

	/**
	 * Adds a fallback key to try (with <code>{name}</code> interpolated to nameParam) if
	 * none of the keys tried so far are found.
	 * @param fallbackKey additional key to try, containing a <code>{name}</code>
	 * placeholder.
	 * @param nameParam value to interpolate for <code>{name}</code>.
	 * @return a new LogProperty trying this key's keys followed by fallbackKey.
	 */
	default LogProperty or(String fallbackKey, String nameParam) {
		String interpolated = LogProperties.interpolateNamedKey(fallbackKey, nameParam);
		validateKey(interpolated);
		return withKey(interpolated);
	}

	private LogProperty withKey(String fallbackKey) {
		List<String> combined = new ArrayList<>(keys());
		combined.add(fallbackKey);
		return new DefaultLogProperty(List.copyOf(combined), properties());
	}

	/**
	 * Creates a LogProperty for a single key.
	 * @param properties properties this key will be looked up against.
	 * @param key key.
	 * @return LogProperty.
	 */
	static LogProperty of(LogProperties properties, String key) {
		validateKey(key);
		return new DefaultLogProperty(List.of(key), properties);
	}

	/**
	 * Creates a LogProperty for a single key with a <code>{name}</code> parameter
	 * interpolated.
	 * @param properties properties this key will be looked up against.
	 * @param key key, containing a <code>{name}</code> placeholder.
	 * @param nameParam value to interpolate for <code>{name}</code>.
	 * @return LogProperty.
	 */
	static LogProperty of(LogProperties properties, String key, String nameParam) {
		String interpolated = LogProperties.interpolateNamedKey(key, nameParam);
		validateKey(interpolated);
		return new DefaultLogProperty(List.of(interpolated), properties);
	}

	/**
	 * Validates a fully resolved (post <code>{name}</code> interpolation) property key -
	 * must start with {@value LogProperties#ROOT_PREFIX}, must not start or end with
	 * {@value LogProperties#SEP}, and must not use a reserved key parameter name.
	 * @param key fully resolved key to validate.
	 * @throws IllegalArgumentException if key is malformed.
	 */
	private static void validateKey(String key) {
		if (!key.startsWith(LogProperties.ROOT_PREFIX)) {
			throw new IllegalArgumentException(
					"Property key should start with: '" + LogProperties.ROOT_PREFIX + "'. key = " + key);
		}
		if (key.endsWith(LogProperties.SEP) || key.startsWith(LogProperties.SEP)) {
			throw new IllegalArgumentException("Property key should not start or end with '" + LogProperties.SEP + "'");
		}
		LogProperties.validateKeyParameters(key, Set.of());
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
		 * Like {@link #map(PropertyFunction)} but on conversion failure builds a richer
		 * error message - which key it came from, where that key was found, and (for
		 * {@link PropertyConvertException}/{@link ValidationException} causes) the
		 * original raw value - instead of {@code map}'s terser one line message. Usable
		 * at any point in a chain, not just directly off a {@link LogProperty}, since a
		 * {@link Success} keeps pointing back to the property it originally came from no
		 * matter how many conversions have run since.
		 * @param <U> output value type.
		 * @param properties the properties the original lookup was made against, used
		 * only for the "Tried:" line of the error message.
		 * @param converter conversion function.
		 * @return converted result.
		 */
		default <U> Result<U> convert(LogProperties properties, PropertyFunction<T, U, ? super Exception> converter) {
			return switch (this) {
				case Success<T> s -> {
					try {
						yield mapValue(s, converter._apply(s.value()));
					}
					catch (Exception e) {
						yield richError(properties, s, e);
					}
				}
				case Missing<T> m -> m.convert();
				case Error<T> e -> e.convert();
			};
		}

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

final class DefaultLogProperty implements LogProperty {

	private final List<String> keys;

	private final LogProperties properties;

	DefaultLogProperty(List<String> keys, LogProperties properties) {
		super();
		if (keys.isEmpty()) {
			throw new IllegalArgumentException("at least one key is required");
		}
		this.keys = keys;
		this.properties = properties;
	}

	@Override
	public String key() {
		return keys.get(0);
	}

	@Override
	public List<String> keys() {
		return keys;
	}

	@Override
	public LogProperties properties() {
		return this.properties;
	}

	@Override
	public Result<String> ofString() {
		return resolve(k -> {
			var prop = properties.visit(k, (p, kk) -> {
				var v = p.valueOrNull(kk);
				return v == null ? null : new FoundProperty.StringProperty(p, kk, v);
			});
			return prop == null ? null : new Result.Success.PropertySuccess<>(prop, prop.value());
		});
	}

	@Override
	public Result<List<String>> ofList() {
		return resolve(k -> {
			var prop = properties.visit(k, (p, kk) -> {
				var v = p.listOrNull(kk);
				return v == null ? null : new FoundProperty.ListProperty(p, kk, v);
			});
			return prop == null ? null : new Result.Success.PropertySuccess<>(prop, prop.value());
		});
	}

	@Override
	public Result<Map<String, String>> ofKeyValues() {
		return resolve(k -> {
			var prop = properties.visit(k, (p, kk) -> {
				var v = p.mapOrNull(kk);
				return v == null ? null : new FoundProperty.MapProperty(p, kk, v);
			});
			return prop == null ? null : new Result.Success.PropertySuccess<>(prop, prop.value());
		});
	}

	private <T> Result<T> resolve(java.util.function.Function<String, Result.@Nullable Success<T>> lookup) {
		for (String k : keys) {
			var success = lookup.apply(k);
			if (success != null) {
				return success;
			}
		}
		return missingResult(properties, keys);
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

	String fullyQualifiedKey(String key) {
		return key;
	}

}

/**
 * Found property retrieved from {@link LogProperties}. This is a bridge and meta data
 * needed for the {@link LogProperty} fluent like monads. It includes the original value
 * before conversions.
 *
 * @apiNote This sealed class is purposely not generic parameterized but you are allowed
 * to pattern match as the subclasses represent the builtin types of properties that are
 * supported. Deliberately a top-level (not nested) type so it stays package-private -
 * interface members are always implicitly public in Java even without the keyword, so
 * nesting it inside LogProperty would not have hidden it.
 */
sealed interface FoundProperty {

	/**
	 * The originating <em>exact</em> properties that the value was found on.
	 * @return properties.
	 */
	LogProperties properties();

	/**
	 * The key that was used to find this property.
	 * @return key also known as property name.
	 */
	String key();

	/**
	 * A string representation of the value that this property has usually for error
	 * descriptions.
	 * @return description of value.
	 */
	String valueDescription();

	/**
	 * A found <strong>string</strong> property result which includes the
	 * <strong>exact</strong> properties where a value was found.
	 *
	 * @param properties the <strong>exact</strong> properties where the value was found.
	 * @param key property key.
	 * @param value property string value.
	 */
	record StringProperty(LogProperties properties, String key, String value) implements FoundProperty {
		@Override
		public String valueDescription() {
			return maybeRedact(value);
		}

		private static final Set<String> REDACTED_KEYS = Set.of("password", "apikey", "secret", "token");

		private static final String REDACTED_VALUE = "<REDACTED>";

		private static final String maybeRedact(String input) {
			String lower = input.toLowerCase(Locale.ROOT);
			if (REDACTED_KEYS.contains(lower)) {
				return REDACTED_VALUE;
			}
			for (var k : REDACTED_KEYS) {
				if (input.contains(k)) {
					return REDACTED_VALUE;
				}
			}
			return input;
		}
	}

	/**
	 * A found <strong>list</strong> property result which includes the
	 * <strong>exact</strong> properties where a value was found.
	 *
	 * @param properties the <strong>exact</strong> properties where the value was found.
	 * @param key property key.
	 * @param value property string value.
	 */
	record ListProperty(LogProperties properties, String key, List<String> value) implements FoundProperty {
		@Override
		public String valueDescription() {
			return StringProperty.maybeRedact("" + value);
		}
	}

	/**
	 * A found <strong>map</strong> property result which includes the
	 * <strong>exact</strong> properties where a value was found.
	 *
	 * @param properties the <strong>exact</strong> properties where the value was found.
	 * @param key property key.
	 * @param value property string value.
	 */
	record MapProperty(LogProperties properties, String key, Map<String, String> value) implements FoundProperty {
		@Override
		public String valueDescription() {
			return StringProperty.maybeRedact("" + value);
		}
	}

}
