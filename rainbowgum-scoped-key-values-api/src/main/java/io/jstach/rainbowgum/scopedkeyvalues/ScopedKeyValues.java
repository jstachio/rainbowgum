package io.jstach.rainbowgum.scopedkeyvalues;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProvider;
import io.jstach.rainbowgum.scopedkeyvalues.spi.ScopedKeyValuesProviderFactory;

/**
 * Request/task-scoped key values, pushed once (typically once, wrapping an entire request
 * or task at its boundary) and never mutated afterward.
 * <p>
 * This is deliberately not a replacement for ad-hoc, imperative, single-call-site key
 * values (e.g. SLF4J's own {@code LoggingEventBuilder#addKeyValue(String, Object)}) -
 * that is still the right tool for "add this to just this one log line". This class is
 * for the opposite shape: a small number of values that should be visible on every log
 * line for an entire request/task, set up once, at a well-known boundary.
 * <p>
 * The actual push/read mechanism is supplied by whichever {@link ScopedKeyValuesProvider}
 * is found via {@link ServiceLoader} the first time this class is used - the same
 * bootstrap shape SLF4J itself uses for {@code org.slf4j.spi.SLF4JServiceProvider}. If
 * none is found, pushing still runs the body normally, it just does not record or
 * propagate anything - always safe to depend on this module alone, even with nothing
 * bound.
 */
public final class ScopedKeyValues {

	/*
	 * A plain static final field is the only option on this project's current JDK
	 * baseline, computed eagerly at class-init time via the usual "safe publication
	 * through a final field" guarantee. If/when this project's baseline reaches a JDK
	 * with JEP 502 (Stable Values), this is a candidate to become a StableValue<...>
	 * instead - same one-time, thread-safe, at-most-once computation, but computed lazily
	 * on first access rather than tied to class initialization, without needing a
	 * holder-class trick to get that laziness.
	 */
	private static final ScopedKeyValuesProvider PROVIDER = findProvider();

	private ScopedKeyValues() {
	}

	private static ScopedKeyValuesProvider findProvider() {
		for (var factory : ServiceLoader.load(ScopedKeyValuesProviderFactory.class)) {
			return factory.provide();
		}
		return NoopScopedKeyValuesProvider.INSTANCE;
	}

	/**
	 * Every currently pushed layer, merged into one map - later (more-deeply-nested)
	 * pushes win on a key collision.
	 * @return merged, read-only-by-convention map, empty if nothing is currently pushed
	 * or no {@link ScopedKeyValuesProvider} was found.
	 */
	public static Map<String, String> currentMerged() {
		return PROVIDER.currentMerged();
	}

	/**
	 * Creates a builder to push a new layer of key values.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Like {@code java.lang.ScopedValue.CallableOp} but declared here so this API module
	 * never has to reference {@code java.lang.ScopedValue} itself, keeping it compilable
	 * on older JDKs - the real {@code ScopedValue}-backed provider (a separate module
	 * requiring a newer JDK) bridges the two with a plain method reference, since both
	 * interfaces share the same {@code R call() throws X} shape.
	 *
	 * @param <R> return type.
	 * @param <X> exception type the operation may throw.
	 */
	@FunctionalInterface
	public interface CallableOp<R, X extends Throwable> {

		/**
		 * Runs the operation.
		 * @return result.
		 * @throws X whatever the operation itself declares.
		 */
		R call() throws X;

	}

	/**
	 * Builds one immutable layer of key values and pushes it for the duration of a
	 * {@link #run(Runnable)}/{@link #call(CallableOp)} call.
	 */
	public static final class Builder {

		private final Map<String, String> layer = new LinkedHashMap<>();

		private Builder() {
		}

		/**
		 * Adds a key/value pair to the layer being built. Order is preserved.
		 * @param key key, never {@code null}.
		 * @param value value, never {@code null} - pass a real sentinel/empty string
		 * instead if "no value" needs to be represented, rather than relying on null
		 * analysis to catch a missing one.
		 * @return this.
		 * @throws NullPointerException if either argument is {@code null}.
		 */
		public Builder add(String key, String value) {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
			layer.put(key, value);
			return this;
		}

		/**
		 * Pushes this layer and runs {@code body} for its duration.
		 * @param body code to run with this layer pushed.
		 */
		public void run(Runnable body) {
			PROVIDER.push(frozen(), body);
		}

		/**
		 * Pushes this layer and calls {@code body} for its duration.
		 * @param <T> return type.
		 * @param <X> exception type {@code body} may throw.
		 * @param body code to call with this layer pushed.
		 * @return whatever {@code body} returns.
		 * @throws X whatever {@code body} throws.
		 */
		public <T, X extends Throwable> T call(CallableOp<T, X> body) throws X {
			return PROVIDER.push(frozen(), body);
		}

		private Map<String, String> frozen() {
			return Collections.unmodifiableMap(new LinkedHashMap<>(layer));
		}

	}

}
