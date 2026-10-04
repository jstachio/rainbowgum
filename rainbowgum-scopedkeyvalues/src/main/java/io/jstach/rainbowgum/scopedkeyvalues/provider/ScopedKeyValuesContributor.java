package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

/**
 * Contributes {@link ScopedKeyValuesProviderImpl#currentMergedKeyValues()} to events.
 * Goes directly to {@link ScopedKeyValuesProviderImpl}'s own storage rather than through
 * the generic {@code Map}-shaped {@code ScopedKeyValuesProvider} contract, so a
 * per-log-call merge never pays for a {@code Map} conversion.
 */
enum ScopedKeyValuesContributor implements KeyValuesContributor {

	INSTANCE;

	@Override
	public KeyValues keyValues() {
		return ScopedKeyValuesProviderImpl.currentMergedKeyValues();
	}

	@Override
	public void clear() {
		/*
		 * Scoped values are unbound when their scope exits, so there is nothing left on a
		 * thread to clear.
		 */
	}

}
