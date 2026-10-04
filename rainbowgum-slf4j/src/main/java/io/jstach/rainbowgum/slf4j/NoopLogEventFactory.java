package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;

/*
 * The default LogEventHandler#delegate() so call sites never need to null check for "is
 * anything registered" - loggerName() is never actually invoked since this factory is
 * only ever consulted for its (empty) defaultKeyValues().
 */
enum NoopLogEventFactory implements LogEventFactory {

	INSTANCE;

	@Override
	public String loggerName() {
		throw new UnsupportedOperationException("NoopLogEventFactory is only used for its (empty) defaultKeyValues().");
	}

	@Override
	public KeyValues defaultKeyValues() {
		return KeyValues.of();
	}

}

/*
 * Adapts the other registered key values contributors to LogEventHandler#delegate().
 */
record ContributorLogEventFactory(KeyValuesContributor contributor) implements LogEventFactory {

	@Override
	public String loggerName() {
		throw new UnsupportedOperationException("ContributorLogEventFactory is only used for its defaultKeyValues().");
	}

	@Override
	public KeyValues defaultKeyValues() {
		return contributor.keyValues();
	}

}
