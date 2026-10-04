package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

/*
 * The default LogEventHandler#contributor() so call sites never need to null check for
 * "is anything registered".
 */
enum NoopKeyValuesContributor implements KeyValuesContributor {

	INSTANCE;

	@Override
	public KeyValues keyValues() {
		return KeyValues.of();
	}

	@Override
	public KeyValues keyValues(KeyValues own) {
		return own;
	}

}
