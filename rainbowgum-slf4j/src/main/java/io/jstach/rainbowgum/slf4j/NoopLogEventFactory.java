package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.LogEventFactory;

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

}
