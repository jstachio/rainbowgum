package io.jstach.rainbowgum.scopedkeyvalues.spi;

/**
 * Service Provider Interface {@link java.util.ServiceLoader} actually discovers for
 * {@link io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues} - a factory rather than
 * the {@link ScopedKeyValuesProvider} itself, since a factory method can do things a bare
 * no-arg-constructor {@link java.util.ServiceLoader} instantiation cannot (validate
 * preconditions, choose between alternate implementations, read configuration, etc)
 * without {@code ScopedKeyValues} needing to know anything about how the provider it gets
 * back was actually put together.
 */
public interface ScopedKeyValuesProviderFactory {

	/**
	 * Creates (or otherwise obtains) the provider.
	 * @return provider, never {@code null}.
	 */
	ScopedKeyValuesProvider provide();

}
