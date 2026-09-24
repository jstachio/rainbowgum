package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEventFactory;

/**
 * Bridges {@link ScopedKeyValuesProviderImpl#currentMergedKeyValues()} into the one
 * method {@code rainbowgum-slf4j} actually consults on a {@link LogEventFactory}
 * registered under its special service-registry name - see
 * {@code RainbowGumSLF4JServiceProvider#SCOPED_KEY_VALUES_SERVICE_NAME}. Every other
 * {@link LogEventFactory} method ({@code loggerName()}, {@code timestamp()}, ...) is
 * never called for a factory registered that way, so they are not implemented. Goes
 * directly to {@link ScopedKeyValuesProviderImpl}'s own storage rather than through the
 * generic {@code Map}-shaped {@code ScopedKeyValuesProvider} contract, so a per-log-call
 * merge never pays for a {@code Map} conversion.
 */
enum ScopedKeyValuesLogEventFactory implements LogEventFactory {

	INSTANCE;

	@Override
	public String loggerName() {
		throw new UnsupportedOperationException(
				"ScopedKeyValuesLogEventFactory is only ever consulted for defaultKeyValues() - "
						+ "loggerName() should never be called on it.");
	}

	@Override
	public KeyValues defaultKeyValues() {
		return ScopedKeyValuesProviderImpl.currentMergedKeyValues();
	}

}
