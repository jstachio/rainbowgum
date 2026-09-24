package io.jstach.rainbowgum.scopedkeyvalues.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues.CallableOp;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;

/**
 * {@code java.lang.ScopedValue}-backed {@link ScopedKeyValuesProvider}: each
 * {@link ScopedKeyValues.Builder#run(Runnable)}/{@link ScopedKeyValues.Builder#call(CallableOp)}
 * rebinds to {@link KeyValues#merge(KeyValues, KeyValues)} of whatever was previously
 * bound (the lower precedence side) and the builder's own {@link MutableKeyValues} layer
 * (the higher precedence side) - a cons cell, not a list, nested one layer deeper with
 * every push - for the duration of that call via a plain {@code ScopedValue.where(...)}.
 * There is no mutable cell anywhere in this class outside of a single in-flight builder's
 * own layer: the JVM's own dynamic-scope unwind is the entire "pop" mechanism, which is
 * also what makes this safe under structured concurrency without any extra bookkeeping -
 * a {@code java.util.concurrent.StructuredTaskScope} fork started inside a push inherits
 * whatever was bound at that moment as a frozen snapshot; nothing a sibling task or the
 * parent does afterward can change what that snapshot contains.
 * <p>
 * Constructed only by {@link ScopedKeyValuesProviderFactoryImpl}, not directly by
 * {@link java.util.ServiceLoader} - package-private since nothing outside this package
 * needs to construct it.
 */
final class ScopedKeyValuesProviderImpl implements ScopedKeyValuesProvider {

	private static final ScopedValue<KeyValues> STACK = ScopedValue.newInstance();

	ScopedKeyValuesProviderImpl() {
	}

	/**
	 * Whatever is currently bound, already fully merged - the accessor
	 * {@code rainbowgum-slf4j} integration actually consults on every log call, never
	 * going through the generic {@link Map}-shaped {@link #currentMerged()}.
	 * @return current key values, {@link KeyValues#of()} (empty) if nothing is currently
	 * pushed.
	 */
	static KeyValues currentMergedKeyValues() {
		return STACK.isBound() ? STACK.get() : KeyValues.of();
	}

	@Override
	public ScopedKeyValues.Builder builder() {
		return new KeyValuesBuilder();
	}

	@Override
	public Map<String, String> currentMerged() {
		/*
		 * KeyValues#copyToMap() is typed for the general case (a KeyValues can hold null
		 * values), but every KeyValues this provider ever binds comes from a
		 * KeyValuesBuilder below, whose additions were already checked non-null in
		 * accept(). requireNonNull here turns that invariant into an explicit, fail-fast
		 * check instead of a silent cast.
		 */
		Map<String, String> result = new LinkedHashMap<>();
		currentMergedKeyValues().forEach((k, v) -> result.put(k, Objects.requireNonNull(v)));
		return result;
	}

	/**
	 * Writes additions straight into a {@link MutableKeyValues}, so pushing a layer never
	 * has to go through an intermediate {@link Map} the way the old {@code Map}-based
	 * {@link ScopedKeyValuesProvider} contract used to force.
	 */
	private static final class KeyValuesBuilder implements ScopedKeyValues.Builder {

		private final MutableKeyValues layer = MutableKeyValues.of();

		@Override
		public void accept(String key, String value) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
			layer.putKeyValue(key, value);
		}

		@Override
		public void run(Runnable body) {
			ScopedValue.where(STACK, KeyValues.merge(currentMergedKeyValues(), layer.freeze())).run(body);
		}

		@Override
		public <T, X extends Throwable> T call(CallableOp<T, X> body) throws X {
			return ScopedValue.where(STACK, KeyValues.merge(currentMergedKeyValues(), layer.freeze())).call(body::call);
		}

	}

}
