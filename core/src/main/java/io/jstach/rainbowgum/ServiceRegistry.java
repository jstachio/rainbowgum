package io.jstach.rainbowgum;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A simple service locator for initialization purposes.
 */
public sealed interface ServiceRegistry permits DefaultServiceRegistry {

	/**
	 * Creates an empty service registry.
	 * @return registry.
	 */
	public static ServiceRegistry of() {
		return new DefaultServiceRegistry();
	}

	/**
	 * Puts a service.
	 * @param <T> service type.
	 * @param type type.
	 * @param service service instance.
	 * @param name name of service.
	 */
	public <T> void put(Class<T> type, T service, String name);

	/**
	 * Puts a service.
	 * @param <T> service type.
	 * @param type type.
	 * @param supplier service instance.
	 * @return service.
	 */
	public <T> T putIfAbsent(Class<T> type, Supplier<T> supplier);

	/**
	 * Puts a service with name "".
	 * @param <T> service type.
	 * @param type type.
	 * @param service service instance.
	 */
	default <T> void put(Class<T> type, T service) {
		put(type, service, "");
	}

	/**
	 * Finds a service or null.
	 * @param <T> service type
	 * @param type service class.
	 * @param name name of service.
	 * @return service or <code>null</code>.
	 */
	@SuppressWarnings("exports")
	public <T> @Nullable T findOrNull(Class<T> type, String name);

	/**
	 * Finds a service or null.
	 * @param <T> service type.
	 * @param type service class.
	 * @return service or <code>null</code>.
	 */
	@SuppressWarnings("exports")
	default <T> @Nullable T findOrNull(Class<T> type) {
		return findOrNull(type, "");
	}

	/**
	 * Finds <strong>all</strong> services of a specific type.
	 * @param <T> service type.
	 * @param type service class.
	 * @return list of services.
	 */
	public <T> List<T> find(Class<T> type);

}

record ServiceKey(Class<?> type, String name) {
	ServiceKey {
		Objects.requireNonNull(type);
		Objects.requireNonNull(name);
	}
}

interface PluginProvider<T, E extends Exception> {

	public T provide(URI uri, String name, LogProperties properties) throws E;

}

abstract class ProviderRegistry<T extends PluginProvider<U, E>, U, E extends Exception> {

	protected final Map<String, T> providers = new ConcurrentHashMap<>();

	public void register(String scheme, T provider) {
		providers.put(scheme, provider);
	}

}

final class DefaultServiceRegistry implements ServiceRegistry {

	private final Map<ServiceKey, Object> services = new ConcurrentHashMap<>();

	@Override
	public <T> void put(Class<T> type, T service, String name) {
		if (service == null) {
			throw new NullPointerException("service");
		}
		services.getOrDefault(new ServiceKey(type, name), service);

	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> @Nullable T findOrNull(Class<T> type, String name) {
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(name, "name");
		return (T) services.get(new ServiceKey(type, name));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> List<T> find(Class<T> type) {
		return services.entrySet().stream().filter(e -> e.getKey().type() == type).map(e -> (T) e.getValue()).toList();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T putIfAbsent(Class<T> type, Supplier<T> supplier) {
		var t = (T) services.computeIfAbsent(new ServiceKey(type, ""), k -> Objects.requireNonNull(supplier.get()));
		return t;
	}

}
