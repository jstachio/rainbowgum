package io.jstach.rainbowgum;

import java.util.Objects;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.Property.StringValue;

@FunctionalInterface
public interface LogProperties {

	public @Nullable String property(String key);

}

record Properties(LogProperties properties) {
	StringValue property(String key) {
		return Property.of(properties, key);
	}

	public static Properties of(LogConfig config) {
		return new Properties(config.properties());
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

	default <U> Property<U> map(Function<? super T, ? extends U> mapper) {
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

	default StringValue mapString(Function<? super T, String> mapper) {
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

	record EmptyValue<T>(String key, @Nullable String original) implements Property<T> {
		public @Nullable T orNull() {
			return null;
		}
	}

	record ObjectValue<T>(String key, @Nullable T orNull, @Nullable String original) implements Property<T> {
	}

	record StringValue(String key, @Nullable String orNull, @Nullable String original) implements Property<String> {

	}

}
