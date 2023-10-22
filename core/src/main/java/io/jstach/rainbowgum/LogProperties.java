package io.jstach.rainbowgum;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties.Extractor.RootExtractor;

@FunctionalInterface
public interface LogProperties {

	static final String ROOT_PREFIX = "logging.";
	static final String LEVEL_PREFIX = ROOT_PREFIX + "level";
	static final String CHANGE_PREFIX = ROOT_PREFIX + "change";

	public @Nullable String valueOrNull(String key);

	// default <T> T value(Property<T> property) {
	// return property.require(this);
	// }
	//
	// @SuppressWarnings("exports")
	// default <T> @Nullable T valueOrNull(Property<T> property) {
	// return property.valueOrNull(this);
	// }

	default <T> PropertyValue<T> property(Property<T> property) {
		return property.get(this);
	}

	default @Nullable String search(String root, String key) {
		return searchPath(key, k -> valueOrNull(concatName(root, k)));

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
			public @Nullable String valueOrNull(String key) {
				return null;
			}

			@Override
			public int order() {
				return -1;
			}
		},
		SYSTEM_PROPERTIES {
			@Override
			public @Nullable String valueOrNull(String key) {
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
		throw new RuntimeException("Error for property. key: " + key, e);
	}

	static RuntimeException throwMissingError(String key) {
		throw new NoSuchElementException("Property missing. key: " + key);
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

	record PropertyValue<T>(Property<T> property, LogProperties properties) {
		public <U> PropertyValue<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new PropertyValue<>(property.map(mapper), properties);
		}

		public @Nullable T valueOrNull() {
			return property.extractor.valueOrNull(properties, property.key);
		}

		public Optional<T> optional() {
			return Optional.ofNullable(valueOrNull());
		}

		public T value() {
			return property.extractor.require(properties, property.key);
		}
	}

	record Property<T>(Extractor<T> extractor, String key) {

		public PropertyValue<T> get(LogProperties properties) {
			return new PropertyValue<>(this, properties);
		}

		private <U> Property<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new Property<>(extractor.map(mapper), key);
		}

		public static RootExtractor builder() {
			return Extractor.of();
		}
	}

	interface Extractor<T> {

		@Nullable
		T valueOrNull(LogProperties props, String key);

		default T require(LogProperties props, String key) {
			var t = valueOrNull(props, key);
			if (t == null) {
				throw throwMissing(props, key);

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

		NoSuchElementException throwMissing(LogProperties props, String key);

		public static RootExtractor of() {
			return new RootExtractor("", false);
		}

		record RootExtractor(String prefix, boolean search) implements Extractor<String> {

			@Override
			public @Nullable String valueOrNull(LogProperties props, String key) {
				if (search) {
					return props.search(prefix, key);
				}
				return props.valueOrNull(LogProperties.concatName(prefix, key));
			}

			@Override
			public RuntimeException throwError(LogProperties props, String key, Exception e) {
				throw LogProperties.throwPropertyError(props.description(concatName(prefix, key)), e);
			}

			@Override
			public NoSuchElementException throwMissing(LogProperties props, String key) {
				throw LogProperties.throwMissingError(props.description(concatName(prefix, key)));
			}

			public RootExtractor withSearch(String prefix) {
				return new RootExtractor(prefix, true);
			}

			public RootExtractor withPrefix(String prefix) {
				return new RootExtractor(prefix, search);
			}

		}

		default <U> Extractor<U> map(PropertyFunction<? super T, ? extends U, ? super Exception> mapper) {
			return new MapExtractor<T, U>(this, mapper);
		}

		default Extractor<T> orElse(T fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, () -> fallback);
		}

		default Extractor<T> orElseGet(Supplier<? extends T> fallback) {
			Objects.requireNonNull(fallback);
			return new FallbackExtractor<T>(this, fallback);
		}

		record FallbackExtractor<T>(Extractor<T> parent, Supplier<? extends T> fallback) implements Extractor<T> {

			@Override
			public T valueOrNull(LogProperties props, String key) {
				var n = parent.valueOrNull(props, key);
				if (n != null) {
					return n;
				}
				return fallback.get();
			}

			@Override
			public RuntimeException throwError(LogProperties props, String key, Exception e) {
				throw parent.throwError(props, key, e);
			}

			@Override
			public NoSuchElementException throwMissing(LogProperties props, String key) {
				throw parent.throwMissing(props, key);
			}

		}

		record MapExtractor<T, R>(Extractor<T> parent,
				PropertyFunction<? super T, ? extends R, ? super Exception> mapper) implements Extractor<R> {

			@Override
			public @Nullable R valueOrNull(LogProperties props, String key) {
				var n = parent.valueOrNull(props, key);
				if (n != null) {
					try {
						return mapper._apply(n);
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

			@Override
			public NoSuchElementException throwMissing(LogProperties props, String key) {
				throw parent.throwMissing(props, key);
			}

		}

	}

}

record CompositeLogProperties(LogProperties[] properties) implements LogProperties {

	@Override
	public @Nullable String valueOrNull(String key) {
		for (var props : properties) {
			var value = props.valueOrNull(key);
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
