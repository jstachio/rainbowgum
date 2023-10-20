package io.jstach.rainbowgum;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.PropertyFunction;

public sealed interface PropertyValue<T> {

	String key();

	@Nullable
	String original();

	@Nullable
	T orNull();

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

	default <U> PropertyValue<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
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
			throw LogProperties.throwPropertyError(key(), e);
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
			throw LogProperties.throwPropertyError(key(), e);
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

	record EmptyValue<T>(String key, @Nullable String original) implements PropertyValue<T> {
		public @Nullable T orNull() {
			return null;
		}
	}

	record ObjectValue<T>(String key, @Nullable T orNull, @Nullable String original) implements PropertyValue<T> {
	}

	record StringValue(String key, @Nullable String orNull,
			@Nullable String original) implements PropertyValue<String> {
		public boolean parseBoolean(boolean fallback) {
			var v = orNull;
			if (v == null) {
				return fallback;
			}
			return Boolean.parseBoolean(v);
		}

	}

}