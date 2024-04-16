package io.jstach.rainbowgum;

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
	 * @param name name of parent component and can be ignored if not needed.
	 * @param provider nullable provider
	 * @param config config used to provide if not null.
	 * @return maybe null component.
	 */
	@SuppressWarnings("exports")
	public static <U> @Nullable U provideOrNull(String name, @Nullable LogProvider<U> provider, LogConfig config) {
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

}
