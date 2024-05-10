package io.jstach.rainbowgum;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A factory that may need config to provide.
 *
 * @param <T> component
 */
@FunctionalInterface
public interface LogProvider<T> {

	/*
	 * TODO maybe make this sealed with sub provider types?
	 */

	/**
	 * Creates the component from config. The component is not always guaranteed to be new
	 * object.
	 * @param name config name of the parent component.
	 * @param config config.
	 * @return component.
	 */
	T provide(String name, LogConfig config);

	/**
	 * Convenience for flattening nullable providers.
	 * @param <U> component
	 * @param provider nullable provider
	 * @param name name of parent component and can be ignored if not needed.
	 * @param config config used to provide if not null.
	 * @return maybe null component.
	 */
	@SuppressWarnings("exports")
	public static <U> @Nullable U provideOrNull(@Nullable LogProvider<U> provider, String name, LogConfig config) {
		if (provider == null) {
			return null;
		}
		return provider.provide(name, config);
	}

	/**
	 * Creates a provider of instance that is already configured.
	 * @param <U> component
	 * @param instance component instance.
	 * @return this.
	 */
	public static <U> LogProvider<U> of(U instance) {
		return (n, c) -> instance;
	}

	/**
	 * Wraps with an error description.
	 * @param description description function passed the name and returns description.
	 * @return wrapped provider.
	 */
	default LogProvider<T> describe(String description) {
		return wrap(this, (k, t) -> "Failure providing " + description + ". cause:\n" + t.getMessage());
	}

	/**
	 * Wraps with an error description.
	 * @param description description function passed the name and returns description.
	 * @return wrapped provider.
	 */
	default LogProvider<T> describe(Function<String, String> description) {
		return wrap(this, (k, t) -> "Failure providing " + description.apply(k) + ". cause:\n" + t.getMessage());
	}

	private static <U> LogProvider<U> wrap(LogProvider<U> provider, BiFunction<String, Throwable, String> description) {
		return (n, c) -> {
			try {
				return provider.provide(n, c);
			}
			catch (Exception e) {
				@SuppressWarnings("null")
				String desc = description.apply(n, e);
				throw new ProvisionException(desc, e);
			}
		};
	}

	/**
	 * Flattens a list of providers to a single provider of a list.
	 * @param <U> provider type.
	 * @param providers list of providers.
	 * @return provider of list.
	 */
	public static <U> LogProvider<List<U>> flatten(List<LogProvider<U>> providers) {
		return (n, c) -> {
			List<U> list = new ArrayList<>();
			for (var p : providers) {
				list.add(p.provide(n, c));
			}
			return list;
		};
	}

	// /**
	// * Flattens a property of a provider.
	// * @param <U>
	// * @param property
	// * @return
	// */
	// public static <U> LogProvider<U> flatten(LogProperty.Property<LogProvider<U>>
	// property) {
	// return (n, c) -> property.get(c.properties()).value().provide(n, c);
	// }

	/**
	 * Thrown if a provider fails and a description needs to be added.
	 */
	public static class ProvisionException extends RuntimeException {

		private static final long serialVersionUID = -1666167238381090332L;

		ProvisionException(String message, Throwable cause) {
			super(message, cause);
			// TODO Auto-generated constructor stub
		}

	}

}
