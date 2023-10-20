package io.jstach.rainbowgum;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.Extractor.RootExtractor;
import io.jstach.rainbowgum.LogProperties.Property;
import io.jstach.rainbowgum.LogProperties.PropertyFunction;
import io.jstach.rainbowgum.PropertyValue.ObjectValue;

@FunctionalInterface
public interface LogProperties {

	static final String DEFAULT_ROOT_PREFIX = "logging.";
	static final String DEFAULT_LEVEL_PREFIX = "level";

	public @Nullable String property(String key);

	default PropertyValue.StringValue propertyValue(String key) {
		return PropertyValue.of(this, key);
	}

	default <T> PropertyValue<T> value(Property<T> property) {
		return property.value(this);
	}

	default @Nullable String search(String root, String key) {
		return searchPath(key, k -> property(concatName(root, k)));

	}

	default String description(String key) {
		return key;
	}

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

	static RuntimeException throwPropertyError(String key, Exception e) {
		throw new RuntimeException("Error for property: " + key, e);
	}

	static RuntimeException throwMissingError(String key) {
		throw new NoSuchElementException("Property missing. property key: " + key);
	}

	@SuppressWarnings("exports")
	static <T> @Nullable T searchPath(String name, Function<String, @Nullable T> resolveFunc) {
		return searchPath(name, resolveFunc, ".");
	}

	@SuppressWarnings("exports")
	static <T> @Nullable T searchPath(String name, Function<String, @Nullable T> resolveFunc, String sep) {
		String tempName = name;
		T level = null;
		int indexOfLastDot = tempName.length();
		while ((level == null) && (indexOfLastDot > -1)) {
			tempName = tempName.substring(0, indexOfLastDot);
			level = resolveFunc.apply(tempName);
			indexOfLastDot = tempName.lastIndexOf(sep);
		}
		if (level != null) {
			return level;
		}
		return null;
	}

	static String concatName(String prefix, String name) {
		if (name.equals("")) {
			return prefix;
		}
		return prefix + "." + name;
	}

	record Property<T>(Extractor<T> extractor, String key) {
		public PropertyValue<T> value(LogProperties properties) {
			return new ObjectValue<T>(key, extractor.get(properties, key), properties.property(key));
		}

		public T require(LogProperties properties) {
			return extractor.require(properties, key);
		}

		public static RootExtractor builder() {
			return Extractor.of();
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

// record Properties(LogProperties properties) {
// StringValue property(String key) {
// return Property.of(properties, key);
// }
//
// public static Properties of(LogConfig config) {
// return new Properties(config.properties());
// }
//
// public static Properties of(LogProperties properties) {
// return new Properties(properties);
// }
//
// }

interface Extractor<T> {

	@Nullable
	T get(LogProperties props, String key);

	default T require(LogProperties props, String key) {
		var t = get(props, key);
		if (t == null) {
			throw LogProperties.throwMissingError(props.description(key));
		}
		return t;
	}

	default Property<T> property(String key) {
		return new Property<>(this, key);
	}

	default Property<T> build(String key) {
		return new Property<>(this, key);
	}

	RuntimeException throwError(LogProperties props, String key, Exception e);

	public static RootExtractor of() {
		return new RootExtractor("", false);
	}

	record RootExtractor(String prefix, boolean search) implements Extractor<String> {

		@Override
		public @Nullable String get(LogProperties props, String key) {
			if (search) {
				return props.search(prefix, key);
			}
			return props.property(prefix + key);
		}

		@Override
		public RuntimeException throwError(LogProperties props, String key, Exception e) {
			throw LogProperties.throwPropertyError(props.description(prefix + key), e);
		}

		RootExtractor withSearch(String prefix) {
			return new RootExtractor(prefix, true);
		}

		RootExtractor withPrefix(String prefix) {
			return new RootExtractor(prefix, search);
		}

	}

	default <U> Extractor<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
		return new MapExtractor<T, U>(this, mapper);
	}

	default Extractor<T> orElse(T fallback) {
		Objects.requireNonNull(fallback);
		return new FallbackExtractor<T>(this, fallback);
	}

	record FallbackExtractor<T>(Extractor<T> parent, T fallback) implements Extractor<T> {

		@Override
		public T get(LogProperties props, String key) {
			var n = parent.get(props, key);
			if (n != null) {
				return n;
			}
			return fallback;
		}

		@Override
		public RuntimeException throwError(LogProperties props, String key, Exception e) {
			throw parent.throwError(props, key, e);
		}

	}

	record MapExtractor<T, R>(Extractor<T> parent,
			PropertyFunction<? super T, ? extends R, ? super Exception> mapper) implements Extractor<R> {

		@Override
		public @Nullable R get(LogProperties props, String key) {
			var n = parent.get(props, key);
			if (n != null) {
				try {
					return mapper.apply(n);
				}
				catch (Exception e) {
					throw LogProperties.throwPropertyError(props.description(key), e);
				}
			}
			return null;
		}

		@Override
		public RuntimeException throwError(LogProperties props, String key, Exception e) {
			throw parent.throwError(props, key, e);
		}

	}

}
