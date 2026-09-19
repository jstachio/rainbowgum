package io.jstach.rainbowgum.scopedkeyvalues;

import java.util.Map;
import java.util.concurrent.Callable;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;

/*
 * ScopedKeyValues' fallback when ServiceLoader finds no ScopedKeyValuesProvider: the
 * body still runs/calls normally, nothing is recorded or read back - mirrors SLF4J's own
 * "safe with nothing bound" behavior.
 */
enum NoopScopedKeyValuesProvider implements ScopedKeyValuesProvider {

	INSTANCE;

	@Override
	public void push(Map<String, @Nullable String> layer, Runnable body) {
		body.run();
	}

	@Override
	public <T> T push(Map<String, @Nullable String> layer, Callable<T> body) throws Exception {
		return body.call();
	}

	@Override
	public Map<String, @Nullable String> currentMerged() {
		return Map.of();
	}

}
