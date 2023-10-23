package io.jstach.rainbowgum;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.Nullable;

public sealed interface ServiceRegistry permits DefaultServiceRegistry {

	public static ServiceRegistry of() {
		return new DefaultServiceRegistry();
	}

	public <T> void put(Class<T> type, T service, String name);

	default <T> void put(Class<T> type, T service) {
		put(type, service, "");
	}

	@SuppressWarnings("exports")
	public <T> @Nullable T findOrNull(Class<T> type, String name);

	@SuppressWarnings("exports")
	default <T> @Nullable T findOrNull(Class<T> type) {
		return findOrNull(type, "");
	}

}

record ServiceKey(Class<?> type, String name) {
	ServiceKey {
		type = Objects.requireNonNull(type);
		name = Objects.requireNonNull(name);
	}
}

final class DefaultServiceRegistry implements ServiceRegistry {

	private final Map<ServiceKey, Object> services = new ConcurrentHashMap<>();

	@Override
	public <T> void put(Class<T> type, T service, String name) {
		Objects.requireNonNull(service);
		services.getOrDefault(new ServiceKey(type, name), service);

	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> @Nullable T findOrNull(Class<T> type, String name) {
		Objects.requireNonNull(type);
		Objects.requireNonNull(name);
		return (T) services.get(new ServiceKey(type, name));
	}

}
