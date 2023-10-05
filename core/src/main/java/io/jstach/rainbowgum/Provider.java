package io.jstach.rainbowgum;

public interface Provider<T> {

	public T provide(LogConfig config);

	public interface ProviderBuilder<T> {

		public Provider<T> build();

	}

}
