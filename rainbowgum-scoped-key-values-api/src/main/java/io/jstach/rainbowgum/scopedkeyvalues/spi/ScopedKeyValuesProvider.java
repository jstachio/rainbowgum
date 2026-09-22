package io.jstach.rainbowgum.scopedkeyvalues.spi;

import java.util.Map;

import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues.CallableOp;

/**
 * Service Provider Interface for
 * {@link io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues}, discovered with
 * {@link java.util.ServiceLoader}. At most one provider is expected to be on the
 * classpath/module path at a time - the first one found is used.
 * <p>
 * A provider owns the actual storage mechanism (a real implementation backs it with
 * {@code java.lang.ScopedValue}); this module never references that type directly so it
 * can stay dependency-free and compile on older JDKs. When no provider is found,
 * {@code ScopedKeyValues} falls back to one that runs the body without recording
 * anything, the same "safe with nothing bound" behavior SLF4J's own facade falls back to
 * when no {@code org.slf4j.spi.SLF4JServiceProvider} is found.
 */
public interface ScopedKeyValuesProvider {

	/**
	 * Pushes {@code layer} and runs {@code body} for its duration.
	 * @param layer key values to push, never {@code null}, keys and values never
	 * {@code null}.
	 * @param body code to run with {@code layer} pushed.
	 */
	void push(Map<String, String> layer, Runnable body);

	/**
	 * Pushes {@code layer} and calls {@code body} for its duration.
	 * @param <T> return type.
	 * @param <X> exception type {@code body} may throw.
	 * @param layer key values to push, never {@code null}, keys and values never
	 * {@code null}.
	 * @param body code to call with {@code layer} pushed.
	 * @return whatever {@code body} returns.
	 * @throws X whatever {@code body} throws.
	 */
	<T, X extends Throwable> T push(Map<String, String> layer, CallableOp<T, X> body) throws X;

	/**
	 * Every currently pushed layer, merged into one map - later (more deeply nested)
	 * pushes win on a key collision.
	 * @return merged, read-only-by-convention map, empty if nothing is currently pushed.
	 */
	Map<String, String> currentMerged();

}
