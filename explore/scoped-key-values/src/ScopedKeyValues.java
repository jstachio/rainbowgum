import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The static facade a user actually calls - analogous to org.slf4j.MDC/Marker. Backed by
 * whatever ScopedKeyValuesFactory is bound (hardcoded to the reference implementation
 * here; a real version would discover this the same way SLF4J discovers its provider).
 */
public final class ScopedKeyValues {

	private static final ScopedKeyValuesFactory FACTORY = DefaultScopedKeyValuesFactory.INSTANCE;

	private ScopedKeyValues() {
	}

	public static ScopedKeyValuesFactory.Scope scope(String name) {
		return FACTORY.getScope(name);
	}

	public static Optional<KeyValues> current(String name) {
		return scope(name).current();
	}

	/**
	 * Everything currently bound, across every named scope ever registered - the
	 * retrieval a LogEventFactory/appender-facing decorator would actually call, since
	 * it has no way to know ahead of time which names some dependency registered.
	 */
	public static KeyValues currentAll() {
		return KeyValues.merge(FACTORY.currentAll());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private final List<String> keys = new ArrayList<>();

		private final List<String> values = new ArrayList<>();

		public Builder add(String key, String value) {
			keys.add(key);
			values.add(value);
			return this;
		}

		public void run(String scopeName, Runnable body) {
			ScopedKeyValues.scope(scopeName).run(build(), body);
		}

		public <T> T call(String scopeName, Callable<T> body) throws Exception {
			return ScopedKeyValues.scope(scopeName).call(build(), body);
		}

		private KeyValues build() {
			return KeyValues.of(keys.toArray(new String[0]), values.toArray(new String[0]));
		}

	}

}
