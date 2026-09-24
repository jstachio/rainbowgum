package io.jstach.rainbowgum.scopedkeyvalues;

import java.util.Map;
import java.util.Objects;

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
	public ScopedKeyValues.Builder builder() {
		return NoopBuilder.INSTANCE;
	}

	@Override
	public Map<String, String> currentMerged() {
		return Map.of();
	}

	private enum NoopBuilder implements ScopedKeyValues.Builder {

		INSTANCE;

		@Override
		public void accept(String key, String value) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
		}

		@Override
		public void run(Runnable body) {
			body.run();
		}

		@Override
		public <T, X extends Throwable> T call(CallableOp<T, X> body) throws X {
			return body.call();
		}

	}

}
