package io.jstach.rainbowgum;

import java.util.Objects;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

public interface LogProperties {

	public @Nullable String property(String key);

}

sealed interface Property<T> {

	String key();

	@Nullable
	String original();

	@Nullable
	T value();

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
		var t = value();
		if (t == null)
			throw new RuntimeException("Missing value for key: " + key());
		return t;
	}

	default <U> Property<U> map(Function<? super T, ? extends U> mapper) {
		Objects.requireNonNull(mapper);
		var v = value();
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
		var v = value();
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
		var t = value();
		if (t == null) {
			return fallback;
		}
		return t;
	}

	record EmptyValue<T>(String key, @Nullable String original) implements Property<T> {
		public @Nullable T value() {
			return null;
		}
	}

	record ObjectValue<T>(String key, @Nullable T value, @Nullable String original) implements Property<T> {
	}

	record StringValue(String key, @Nullable String value, @Nullable String original) implements Property<String> {

	}

}
