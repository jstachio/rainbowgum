package io.jstach.rainbowgum.scopedkeyvalues.spi;

import java.util.Map;

import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;

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
	 * Creates a fresh {@link ScopedKeyValues.Builder} for one layer of key values. Owned
	 * by this provider so an addition can be written directly into its own internal
	 * representation, rather than every push forcing a copy through an intermediate
	 * {@link Map}.
	 * @return builder.
	 */
	ScopedKeyValues.Builder builder();

	/**
	 * Every currently pushed layer, merged into one map - later (more deeply nested)
	 * pushes win on a key collision.
	 * @return merged, read-only-by-convention map, empty if nothing is currently pushed.
	 */
	Map<String, String> currentMerged();

}
