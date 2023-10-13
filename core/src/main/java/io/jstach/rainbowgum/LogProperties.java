package io.jstach.rainbowgum;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.Property.StringValue;

@FunctionalInterface
public interface LogProperties {

	public @Nullable String property(String key);

	default int order() {
		return 0;
	}

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

	public static LogProperties of(List<? extends LogProperties> logProperties) {
		return of(logProperties, StandardProperties.EMPTY);
	}

	enum StandardProperties implements LogProperties {

		EMPTY {
			@Override
			public @Nullable String property(String key) {
				return null;
			}

			@Override
			public int order() {
				return -1;
			}
		},
		SYSTEM_PROPERTIES {
			@Override
			public @Nullable String property(String key) {
				return System.getProperty(key);
			}

			@Override
			public int order() {
				return 400;
			}
		}

	}

}

record CompositeLogProperties(LogProperties[] properties) implements LogProperties {

	@Override
	public @Nullable String property(String key) {
		for (var props : properties) {
			var value = props.property(key);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

}

record Properties(LogProperties properties) {
	StringValue property(String key) {
		return Property.of(properties, key);
	}

	public static Properties of(LogConfig config) {
		return new Properties(config.properties());
	}

	public static Properties of(LogProperties properties) {
		return new Properties(properties);
	}
}

sealed interface Property<T> {

	String key();

	@Nullable
	String original();

	@Nullable
	T orNull();

	static RuntimeException throwPropertyError(String key, Exception e) {
		throw new RuntimeException("Error for property: " + key, e);
	}

	static StringValue of(LogProperties config, String key) {
		String v = config.property(key);
		return of(key, v);
	}

	static StringValue of(String key, @Nullable String value) {
		return new StringValue(key, value, value);
	}

	default T require() {
		var t = orNull();
		if (t == null)
			throw new RuntimeException("Missing value for key: " + key());
		return t;
	}

	default <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		Objects.requireNonNull(mapper);
		var v = orNull();
		if (v == null) {
			return new EmptyValue<>(key(), original());
		}
		try {
			var nv = mapper.apply(v);
			return new ObjectValue<>(key(), nv, original());
		}
		catch (Exception e) {
			throw throwPropertyError(key(), e);
		}
	}

	default StringValue mapString(PropertyFunction<? super T, String, ? super Exception> mapper) {
		Objects.requireNonNull(mapper);
		var v = orNull();
		if (v == null) {
			return new StringValue(key(), null, original());
		}
		try {
			var nv = mapper.apply(v);
			return new StringValue(key(), nv, original());
		}
		catch (Exception e) {
			throw throwPropertyError(key(), e);
		}
	}

	default T requireElse(T fallback) {
		var t = orNull();
		if (t == null) {
			return fallback;
		}
		return t;
	}

	default Optional<T> optional() {
		return Optional.ofNullable(orNull());
	}

	record EmptyValue<T>(String key, @Nullable String original) implements Property<T> {
		public @Nullable T orNull() {
			return null;
		}
	}

	record ObjectValue<T>(String key, @Nullable T orNull, @Nullable String original) implements Property<T> {
	}

	record StringValue(String key, @Nullable String orNull, @Nullable String original) implements Property<String> {
		public boolean parseBoolean(boolean fallback) {
			var v = orNull;
			if (v == null) {
				return fallback;
			}
			return Boolean.parseBoolean(v);
		}

	}

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

		public R _apply(T t) throws E;

	}

	@SuppressWarnings("unchecked")
	public static <E extends Throwable> void sneakyThrow(final Throwable x) throws E {
		throw (E) x;
	}

}
