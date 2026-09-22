package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProviderFactory;
import io.jstach.svc.ServiceProvider;

/**
 * The {@code java.util.ServiceLoader}-discovered factory for
 * {@link ScopedKeyValuesProviderImpl}.
 */
@ServiceProvider(ScopedKeyValuesProviderFactory.class)
public final class ScopedKeyValuesProviderFactoryImpl implements ScopedKeyValuesProviderFactory {

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public ScopedKeyValuesProviderFactoryImpl() {
	}

	@Override
	public ScopedKeyValuesProvider provide() {
		return new ScopedKeyValuesProviderImpl();
	}

}
