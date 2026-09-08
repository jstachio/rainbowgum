package io.jstach.rainbowgum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.FoundProperty;
import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.PropertyFunction;
import io.jstach.rainbowgum.LogProperty.PropertyMissingException;
import io.jstach.rainbowgum.LogProperty.Result;
import io.jstach.rainbowgum.LogProperty.ValidationException;

public interface LogKeyed {

	public String key();

	public List<String> keys();

	public LogProperties properties();

	public Result<String> ofString();

	public Result<List<String>> ofList();

	public Result<Map<String, String>> ofKeyValues();

	default Result<Integer> ofInt() {
		return convert(properties(), ofString(), Integer::parseInt);
	}

	default Result<Boolean> ofBoolean() {
		return convert(properties(), ofString(), Boolean::parseBoolean);
	}

	default Result<URI> ofURI() {
		return convert(properties(), ofString(), URI::new);
	}

	default Result<LogProviderRef> ofProviderRef() {
		return switch (ofURI()) {
			case Result.Success<URI> s -> mapValue(s, LogProviderRef.of(s.value(), s.key()));
			case Result.Missing<URI> m -> m.convert();
			case Result.Error<URI> e -> e.convert();
		};
	}

	default <U> Result<LogProvider<U>> ofProvider(
			PropertyFunction<LogProviderRef, LogProvider<U>, ? super Exception> mapper) {
		return convert(properties(), ofProviderRef(), mapper);
	}

	/**
	 * Like {@link Result#map(PropertyFunction)} but on conversion failure builds a richer
	 * error message - which key it came from, where that key was found, and (for
	 * {@link PropertyConvertException}/{@link ValidationException} causes) the original
	 * raw value - instead of {@code Result.map}'s terser one line message. Usable at any
	 * point in a chain, not just directly off a {@link LogKeyed}, since a
	 * {@link Result.Success} keeps pointing back to the {@link FoundProperty} it
	 * originally came from no matter how many conversions have run since.
	 * @param <T> input value type.
	 * @param <U> output value type.
	 * @param properties the properties the original lookup was made against, used only
	 * for the "Tried:" line of the error message.
	 * @param result result to convert.
	 * @param converter conversion function.
	 * @return converted result.
	 */
	static <T, U> Result<U> convert(LogProperties properties, Result<T> result,
			PropertyFunction<T, U, ? super Exception> converter) {
		return switch (result) {
			case Result.Success<T> s -> {
				try {
					yield mapValue(s, converter._apply(s.value()));
				}
				catch (Exception e) {
					yield richError(properties, s, e);
				}
			}
			case Result.Missing<T> m -> m.convert();
			case Result.Error<T> e -> e.convert();
		};
	}

	/**
	 * Rewraps a value that was already derived from success's value, preserving whether
	 * success was a {@link Result.Success.PropertySuccess} (and so which
	 * {@link FoundProperty} it originally came from) or a
	 * {@link Result.Success.ValueSuccess} - useful for a conversion step that cannot
	 * itself throw, so {@link #convert} would be overkill, but that still needs to keep
	 * the result's origin intact for a later conversion step's error message.
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
	 * @return a new LogKeyed trying this key's keys followed by fallbackKey.
	 */
	default LogKeyed or(String fallbackKey) {
		validateKey(fallbackKey);
		return withKey(fallbackKey);
	}

	/**
	 * Adds a fallback key to try (with <code>{name}</code> interpolated to nameParam) if
	 * none of the keys tried so far are found.
	 * @param fallbackKey additional key to try, containing a <code>{name}</code>
	 * placeholder.
	 * @param nameParam value to interpolate for <code>{name}</code>.
	 * @return a new LogKeyed trying this key's keys followed by fallbackKey.
	 */
	default LogKeyed or(String fallbackKey, String nameParam) {
		String interpolated = LogProperties.interpolateNamedKey(fallbackKey, nameParam);
		validateKey(interpolated);
		return withKey(interpolated);
	}

	private LogKeyed withKey(String fallbackKey) {
		List<String> combined = new ArrayList<>(keys());
		combined.add(fallbackKey);
		return new DefaultLogKeyed(List.copyOf(combined), properties());
	}

	static LogKeyed of(LogProperties properties, String key) {
		validateKey(key);
		return new DefaultLogKeyed(List.of(key), properties);
	}

	static LogKeyed of(LogProperties properties, String key, String nameParam) {
		String interpolated = LogProperties.interpolateNamedKey(key, nameParam);
		validateKey(interpolated);
		return new DefaultLogKeyed(List.of(interpolated), properties);
	}

	/**
	 * Validates a fully resolved (post <code>{name}</code> interpolation) property key -
	 * must start with {@value LogProperties#ROOT_PREFIX}, must not start or end with
	 * {@value LogProperties#SEP}, and must not use a reserved key parameter name. Mirrors
	 * what {@code PropertyGetter.validateKey} used to enforce.
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

}

class DefaultLogKeyed implements LogKeyed {

	private final List<String> keys;

	private final LogProperties properties;

	DefaultLogKeyed(List<String> keys, LogProperties properties) {
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
			var v = this.properties.stringPropertyOrNull(k);
			return v == null ? null : new Result.Success.PropertySuccess<>(v, v.value());
		});
	}

	@Override
	public Result<List<String>> ofList() {
		return resolve(k -> {
			FoundProperty.@Nullable ListProperty prop = properties.listPropertyOrNull(k);
			return prop == null ? null : new Result.Success.PropertySuccess<>(prop, prop.value());
		});
	}

	@Override
	public Result<Map<String, String>> ofKeyValues() {
		return resolve(k -> {
			FoundProperty.@Nullable MapProperty prop = properties.mapPropertyOrNull(k);
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
