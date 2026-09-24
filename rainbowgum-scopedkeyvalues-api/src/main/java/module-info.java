/**
 * <strong>EXPERIMENTAL</strong> A small, self-contained facade for request/task-scoped
 * key values - see {@link io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues}. This
 * module has no dependency beyond (optionally) JSpecify, the same shape as
 * {@code slf4j-api}: it is meant to be a safe compile-time dependency for
 * application/library code that wants to push scoped key values without committing to
 * which logging backend (if any) actually records them.
 * <p>
 * The real work is done by whichever provider a
 * {@link io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProviderFactory} found
 * via {@link java.util.ServiceLoader} at class-init time hands back - see
 * {@code io.jstach.rainbowgum.scopedkeyvalues.provider} for the RainbowGum-backed one.
 * When none is found, {@link io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues} falls
 * back to running the body without recording anything, the same "safe with nothing
 * bound" behavior SLF4J's own facade falls back to when no
 * {@code org.slf4j.spi.SLF4JServiceProvider} is found.
 */
module io.jstach.rainbowgum.scopedkeyvalues {

	exports io.jstach.rainbowgum.scopedkeyvalues;
	exports io.jstach.rainbowgum.scopedkeyvalues.spi;

	uses io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProviderFactory;

	requires static org.jspecify;

}
