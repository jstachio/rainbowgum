package io.jstach.rainbowgum.scopedkeyvalues.provider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues.CallableOp;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;

/**
 * {@code java.lang.ScopedValue}-backed {@link ScopedKeyValuesProvider}: each
 * {@linkplain #push(Map, Runnable) push} rebinds to
 * {@link KeyValues#merge(KeyValues, KeyValues)} of whatever was previously bound (the
 * lower precedence side) and the newly built {@link KeyValues} layer (the higher
 * precedence side) - a cons cell, not a list, nested one layer deeper with every push -
 * for the duration of that call via a plain {@code ScopedValue.where(...)}. There is no
 * mutable cell anywhere in this class: the JVM's own dynamic-scope unwind is the entire
 * "pop" mechanism, which is also what makes this safe under structured concurrency
 * without any extra bookkeeping - a {@code java.util.concurrent.StructuredTaskScope} fork
 * started inside a push inherits whatever was bound at that moment as a frozen snapshot;
 * nothing a sibling task or the parent does afterward can change what that snapshot
 * contains.
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
	public void push(Map<String, String> layer, Runnable body) {
		ScopedValue.where(STACK, KeyValues.merge(currentMergedKeyValues(), toKeyValues(layer))).run(body);
	}

	@Override
	public <T, X extends Throwable> T push(Map<String, String> layer, CallableOp<T, X> body) throws X {
		return ScopedValue.where(STACK, KeyValues.merge(currentMergedKeyValues(), toKeyValues(layer))).call(body::call);
	}

	@Override
	public Map<String, String> currentMerged() {
		/*
		 * KeyValues#copyToMap() is typed for the general case (a KeyValues can hold null
		 * values), but every KeyValues this provider ever binds comes from
		 * toKeyValues(Map<String, String>) below, whose values were already checked
		 * non-null at ScopedKeyValues.Builder#add(...). requireNonNull here turns that
		 * invariant into an explicit, fail-fast check instead of a silent cast.
		 */
		Map<String, String> result = new LinkedHashMap<>();
		currentMergedKeyValues().forEach((k, v) -> result.put(k, Objects.requireNonNull(v)));
		return result;
	}

	private static KeyValues toKeyValues(Map<String, String> layer) {
		var buf = MutableKeyValues.of(layer.size());
		layer.forEach(buf);
		return buf.freeze();
	}

}
