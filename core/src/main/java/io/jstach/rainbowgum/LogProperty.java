package io.jstach.rainbowgum;

import java.net.URI;
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
	public Result<Map<String, String>> ofMap();

	/**
	 * Resolves the property as an int.
	 * @return result.
	 */
	default Result<Integer> ofInt() {
		return ofString().map(Integer::parseInt);
	}

	/**
	 * Resolves the property as a boolean.
	 * @return result.
	 */
	default Result<Boolean> ofBoolean() {
		return ofString().map(Boolean::parseBoolean);
	}

	/**
	 * Resolves the property as a URI.
	 * @return result.
	 */
	default Result<URI> ofURI() {
		return ofString().map(URI::new);
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
		return ofProviderRef().map(mapper);
	}

	/**
	 * Rewraps a value that was already derived from success's value, keeping the
	 * {@link Result.Success.PropertySuccess}'s origin
	 * (topProperties/properties/key/rawValue/kind) intact - useful for a conversion step
	 * that cannot itself throw, so {@link Result#map(PropertyFunction)} would be
	 * overkill, but that still needs to keep the result's origin intact for a later
	 * conversion step's error message.
	 * @param <T> success's value type.
	 * @param <U> new value type.
	 * @param success success to take the origin from.
	 * @param value already-converted value to wrap.
	 * @return new success of the same shape as success, wrapping value.
	 */
	static <T, U> Result.Success<U> mapValue(Result.Success<T> success, U value) {
		return switch (success) {
			case Result.Success.PropertySuccess<T> ps -> new Result.Success.PropertySuccess<>(ps.topProperties(),
					ps.properties(), ps.key(), ps.rawValue(), ps.kind(), value);
		};
	}

	private static <U> Result.Error<U> richError(Result.Success<?> previousResult, Exception e) {
		String fqk = previousResult.key();
		Result.Success.PropertySuccess<?> ps = switch (previousResult) {
			case Result.Success.PropertySuccess<?> p -> p;
		};
		if (ps.kind() == Result.Success.PropertySuccess.Kind.VALUE) {
			String resolvedKey = ps.topProperties().description(fqk);
			String message = "Error for property. key: " + resolvedKey + ", " + e.getMessage();
			return new Result.Error<>(resolvedKey, message, e);
		}
		var badProps = ps.properties();
		String resolvedKey = "'" + fqk + "' from " + badProps.description(fqk);
		String message;
		if (e instanceof PropertyConvertException || e instanceof ValidationException) {
			message = "Error converting property. key: " + resolvedKey + ", value: '" + ps.valueDescription()
					+ "' cause:\n" + e.getMessage();
		}
		else {
			message = "Error for property. key: " + resolvedKey + ", " + errorName(e) + " " + e.getMessage();
		}
		message += "\nTried: '" + fqk + "' from " + ps.topProperties().description(fqk);
		return new Result.Error<>(resolvedKey, message, e);
	}

	/**
	 * Prefers {@link PropertyProblem#errorName()} (a short, stable name) over the
	 * exception's actual class name - a foreign exception (not one of ours) just gets its
	 * full class name as before.
	 */
	private static String errorName(Exception e) {
		if (e instanceof PropertyProblem pp) {
			return pp.errorName();
		}
		return e.getClass().getName();
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
	sealed interface PropertyProblem permits PropertyConvertException, PropertyMissingException, ValidationException,
			LogProviderRef.NotFoundException {

		/**
		 * A short, stable name for this problem used in error messages instead of the
		 * exception's full (and for a nested class, {@code $}-separated) class name.
		 * Deliberately hardcoded per implementation rather than derived via
		 * {@code getClass().getSimpleName()} so it stays stable across renames/moves of
		 * the actual exception class.
		 * @return error name.
		 */
		String errorName();

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

		@Override
		public String errorName() {
			return "PropertyConvertException";
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

		@Override
		public String errorName() {
			return "PropertyMissingException";
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

		@Override
		public String errorName() {
			return "ValidationException";
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
		 * Registers this result with {@code validator} via {@link Validator#add(Result)}
		 * (a still-{@link Missing} result is treated as a real failure) and returns this
		 * result unchanged, so a chain that builds a required property can register
		 * itself with a shared {@link Validator} and keep going in one expression -
		 * {@code properties.forKey(key).ofString().or(fallback).validate(v)} - instead of
		 * a separate {@code var x = ...; v.add(x);} statement pair. The validator is
		 * shared and single-use by design: add every property's result this way (or via
		 * {@link #validateIfError(Validator)} for an optional one), then call
		 * {@link Validator#validate()} exactly once at the end to report every failure
		 * together instead of one exception per property.
		 * @param validator validator to add this result to.
		 * @return this result, unchanged.
		 */
		default Result<T> validate(Validator validator) {
			validator.add(this);
			return this;
		}

		/**
		 * Like {@link #validate(Validator)} but via {@link Validator#addIfError(Result)}
		 * instead of {@link Validator#add(Result)} - a {@link Missing} result is expected
		 * and ignored, only a {@link Result.Error} is treated as a failure. Intended for
		 * an optional property that already has a fallback applied (for example via
		 * {@link #or(Object)}): the property being absent is fine, but if it was present
		 * and failed to convert that should not be swallowed.
		 * @param validator validator to add this result to.
		 * @return this result, unchanged.
		 */
		default Result<T> validateIfError(Validator validator) {
			validator.addIfError(this);
			return this;
		}

		/**
		 * Convenience for a genuine one-off, single-property validation that does not
		 * need to participate in a larger batch: builds a single-use {@link Validator},
		 * registers this result with it via {@link Validator#add(Result)}, and validates
		 * immediately - instead of registering with a caller-managed {@link Validator}
		 * via {@link #validate(Validator)} and deferring to a later
		 * {@link Validator#validate()} call.
		 * <p>
		 * Uses {@link Validator#add(Result)} semantics: a still-{@link Missing} result is
		 * treated as a failure, same as an absent required property with no fallback. If
		 * this property is optional, apply a fallback first -
		 * {@code properties.forKey(key).ofString().or(fallback).validateNow(component)} -
		 * since a result with a fallback applied is never {@link Missing}. For a property
		 * that should tolerate being missing but not being present-and-malformed, use
		 * {@link #validateIfError(Validator)} with an externally-owned {@link Validator}
		 * instead - there is deliberately no addIfError-equivalent of this method, to
		 * avoid two same-named single-property convenience methods with silently
		 * different failure semantics.
		 * @param component the class on whose behalf this property is being resolved -
		 * only used to name the source of the failure in the exception message.
		 * @return value.
		 * @throws ValidationException if this result is not a {@link Success}.
		 */
		default T validateNow(Class<?> component) {
			var v = Validator.of(component);
			v.add(this);
			v.validate();
			return value();
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
		 * Maps this result's value through {@code mapper}. On success, keeps the origin
		 * ({@link Success.PropertySuccess#topProperties() topProperties}/properties/key/
		 * rawValue/kind, see {@link Success.PropertySuccess}) so a later conversion
		 * step's error message can still point back to it. On failure, builds a rich
		 * error message - which key it came from, where that key was found, and (for
		 * {@link PropertyConvertException}/{@link ValidationException} causes) the
		 * original raw value - with a "Tried:" line searching {@code topProperties}: the
		 * {@link LogProperties} the very first {@link LogProperties#forKey(String)} in
		 * this chain was looked up against, which for a chained/composite
		 * {@code LogProperties.of(a, b)} can be broader than the exact source the value
		 * was actually found at. Usable at any point in a chain, not just directly off a
		 * {@link LogProperty}, since a {@link Success} keeps pointing back to the
		 * property it originally came from no matter how many conversions have run since.
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
			 * A property that is present, either because it was actually found in
			 * properties, or (when {@code kind} is {@link Kind#VALUE}) because it was
			 * missing there and a fallback value was supplied instead.
			 *
			 * @param <T> property type.
			 * @param topProperties the {@link LogProperties} the very first
			 * {@link LogProperties#forKey(String)} in this chain was looked up against -
			 * for a chained/composite {@code LogProperties.of(a, b)} this is the whole
			 * composite, not just whichever member the value ended up found in. Used for
			 * a failure message's "Tried:" line, which can therefore search more broadly
			 * than {@code properties}.
			 * @param properties the properties searched; for {@link Kind#STRING}/
			 * {@link Kind#LIST}/{@link Kind#MAP} the <strong>exact</strong> properties
			 * where the value was found - for a chained/composite search this can be
			 * narrower than {@code topProperties}, naming just the one member the value
			 * was actually found in. Used for a failure message's "key: ... from X" line.
			 * @param key property key.
			 * @param rawValue value as originally found (or the fallback, for
			 * {@link Kind#VALUE}), before any conversion - a {@link String}, {@link List
			 * List&lt;String&gt;}, or {@link Map Map&lt;String,String&gt;} depending on
			 * kind.
			 * @param kind which of {@link LogProperty}'s {@code ofString()}/
			 * {@code ofList()}/{@code ofMap()} this property was found through, and so
			 * rawValue's runtime type - or {@link Kind#VALUE} if it was not found at all
			 * and rawValue is a fallback.
			 * @param value actual (possibly converted) value.
			 * @apiNote rawValue/kind stick around after a conversion (e.g.
			 * {@link LogProperty#ofInt()} converting the string this was found as into an
			 * {@code Integer}) so error messages can still describe the original value -
			 * value's type and rawValue's kind are not always in sync.
			 */
			public record PropertySuccess<T>(LogProperties topProperties, LogProperties properties, String key,
					Object rawValue, Kind kind, T value) implements Success<T> {

				/**
				 * Which of {@link LogProperty}'s typed accessors a
				 * {@link PropertySuccess} was found through, and so what
				 * {@link PropertySuccess#rawValue()}'s runtime type actually is - or that
				 * it was not found at all and is a plain fallback value.
				 */
				public enum Kind {

					/**
					 * rawValue is a {@link String}, found via
					 * {@link LogProperty#ofString()}.
					 */
					STRING,
					/**
					 * rawValue is a {@link List List&lt;String&gt;}, found via
					 * {@link LogProperty#ofList()}.
					 */
					LIST,
					/**
					 * rawValue is a {@link Map Map&lt;String,String&gt;}, found via
					 * {@link LogProperty#ofMap()}.
					 */
					MAP,
					/**
					 * Not found in properties at all - rawValue is a plain fallback value
					 * supplied via {@link Result#or}.
					 */
					VALUE

				}

				/**
				 * Successfully found property value.
				 * @param topProperties the properties the original lookup started from.
				 * @param properties the properties searched.
				 * @param key property key.
				 * @param rawValue value as originally found (or the fallback), before any
				 * conversion.
				 * @param kind rawValue's kind.
				 * @param value actual value should not be <code>null</code>.
				 */
				public PropertySuccess {
					if (value == null) {
						throw new NullPointerException("value");
					}
				}

				@Override
				public <U> Result<U> map(PropertyFunction<T, U, ? super Exception> mapper) {
					try {
						U u = mapper._apply(value);
						return new PropertySuccess<>(topProperties, properties, key, rawValue, kind, u);
					}
					catch (Exception e) {
						return richError(this, e);
					}
				}

				@Override
				public String describe() {
					if (kind == Kind.VALUE) {
						return "Fallback[" + key + "]=" + value;
					}
					return "Property[" + key + "]=" + valueDescription();
				}

				String valueDescription() {
					String s = kind == Kind.STRING ? (String) rawValue : String.valueOf(rawValue);
					return maybeRedact(s);
				}

				private static final Set<String> REDACTED_KEYS = Set.of("password", "apikey", "secret", "token");

				private static final String REDACTED_VALUE = "<REDACTED>";

				private static String maybeRedact(String input) {
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

		}

		/**
		 * A property that is missing (<code>null</code>).
		 *
		 * @param <T> property type.
		 * @param properties the properties that were searched, kept around so
		 * {@link #or(Object)} can hand it to the {@link Success.PropertySuccess} it
		 * builds for a supplied fallback.
		 * @param keys keys.
		 * @param message description of where the property is missing.
		 */
		public record Missing<T>(LogProperties properties, List<String> keys, String message) implements Result<T> {
			/**
			 * A property that is missing (<code>null</code>).
			 * @param properties the properties that were searched.
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
					return new Success.PropertySuccess<>(properties, properties, keys.get(0), fallback,
							Success.PropertySuccess.Kind.VALUE, fallback);
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
		return resolve(k -> properties.visit(k, (p, kk) -> {
			var v = p.valueOrNull(kk);
			return v == null ? null : new Result.Success.PropertySuccess<>(properties, p, kk, v,
					Result.Success.PropertySuccess.Kind.STRING, v);
		}));
	}

	@Override
	public Result<List<String>> ofList() {
		return resolve(k -> properties.visit(k, (p, kk) -> {
			var v = p.listOrNull(kk);
			return v == null ? null : new Result.Success.PropertySuccess<>(properties, p, kk, v,
					Result.Success.PropertySuccess.Kind.LIST, v);
		}));
	}

	@Override
	public Result<Map<String, String>> ofMap() {
		return resolve(k -> properties.visit(k, (p, kk) -> {
			var v = p.mapOrNull(kk);
			return v == null ? null : new Result.Success.PropertySuccess<>(properties, p, kk, v,
					Result.Success.PropertySuccess.Kind.MAP, v);
		}));
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
		return new Result.Missing<>(props, keys, message);
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
