package io.jstach.rainbowgum.scopedkeyvalues.provider;

import java.util.Map;
import java.util.concurrent.Callable;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter.KeyValuesCarrier;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;
import io.jstach.svc.ServiceProvider;

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
 * Storage is {@code static}, not tied to a particular instance of this class, since
 * {@link java.util.ServiceLoader} does not guarantee only one instance is ever created -
 * {@link #currentMergedKeyValues()} is the fast, no-{@link Map}-conversion accessor
 * {@code rainbowgum-slf4j} integration uses directly on every log call;
 * {@link #currentMerged()} (the generic {@link ScopedKeyValuesProvider} contract) is only
 * ever used by callers going through the plain {@code ScopedKeyValues} facade, not by
 * this module's own logging integration.
 */
@ServiceProvider(ScopedKeyValuesProvider.class)
public final class ScopedKeyValuesProviderImpl implements ScopedKeyValuesProvider {

	private static final ScopedValue<KeyValues> STACK = ScopedValue.newInstance();

	/**
	 * For service loader.
	 */
	public ScopedKeyValuesProviderImpl() {
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
	public void push(Map<String, @Nullable String> layer, Runnable body) {
		var merged = KeyValues.merge(currentMergedKeyValues(), toKeyValues(layer));
		try {
			ScopedValue.where(STACK, merged).run(body);
		}
		catch (Throwable t) {
			tagWithContext(t, merged);
			throw t;
		}
	}

	@Override
	public <T> T push(Map<String, @Nullable String> layer, Callable<T> body) throws Exception {
		var merged = KeyValues.merge(currentMergedKeyValues(), toKeyValues(layer));
		try {
			return ScopedValue.where(STACK, merged).call(body::call);
		}
		catch (Exception e) {
			tagWithContext(e, merged);
			throw e;
		}
	}

	@Override
	public Map<String, @Nullable String> currentMerged() {
		return currentMergedKeyValues().copyToMap();
	}

	private static KeyValues toKeyValues(Map<String, @Nullable String> layer) {
		var buf = MutableKeyValues.of(layer.size());
		layer.forEach(buf);
		return buf.freeze();
	}

	/*
	 * Prototype/experimental - see ScopedContextMarker and
	 * LogFormatter.ThrowableFormatter.KeyValuesCarrier. Tags the escaping throwable with
	 * the key values that were bound at *this* push boundary - the deepest (most nested)
	 * push an exception escapes through already has everything from enclosing pushes
	 * merged in (see this class's own javadoc on the cons-cell structure), so only the
	 * first (innermost) push it passes through needs to tag it; an outer push seeing it
	 * already tagged leaves it alone rather than replacing a more specific snapshot with
	 * a less specific one.
	 */
	private static void tagWithContext(Throwable t, KeyValues merged) {
		if (merged.isEmpty()) {
			return;
		}
		for (var suppressed : t.getSuppressed()) {
			if (suppressed instanceof KeyValuesCarrier) {
				return;
			}
		}
		t.addSuppressed(new ScopedContextMarker(merged));
	}

}
