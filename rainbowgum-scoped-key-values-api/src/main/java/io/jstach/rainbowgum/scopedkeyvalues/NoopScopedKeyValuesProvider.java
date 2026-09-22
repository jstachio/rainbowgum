package io.jstach.rainbowgum.scopedkeyvalues;

import java.util.Map;

import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues.CallableOp;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;

/*
 * ScopedKeyValues' fallback when ServiceLoader finds no ScopedKeyValuesProviderFactory:
 * the body still runs/calls normally, nothing is recorded or read back - mirrors SLF4J's
 * own "safe with nothing bound" behavior.
 */
enum NoopScopedKeyValuesProvider implements ScopedKeyValuesProvider {

	INSTANCE;

	@Override
	public void push(Map<String, String> layer, Runnable body) {
		body.run();
	}

	@Override
	public <T, X extends Throwable> T push(Map<String, String> layer, CallableOp<T, X> body) throws X {
		return body.call();
	}

	@Override
	public Map<String, String> currentMerged() {
		return Map.of();
	}

}
