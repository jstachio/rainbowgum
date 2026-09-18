import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Second-pass design, simpler than the first: one ScopedValue<List<KeyValues>>, no
 * names, no factory/SPI, no COW/CAS mutable cell anywhere. Each push builds a brand new
 * immutable list (the previous list as the head, the new KeyValues as the tail) and
 * rebinds via plain ScopedValue.where(...).run/call(...) - the JDK's own dynamic-scope
 * unwind is the entire "pop" mechanism, nothing to clean up, nothing that can leak.
 * <p>
 * Expected real-world usage: one push wrapping the *entire* request/task, at the
 * boundary - the same shape the ScopedValue JEP's own examples use, not something
 * scattered through every layer of business logic. This deliberately does not try to
 * replace ad-hoc, imperative, single-call-site key-values (the equivalent of MDC.put or
 * SLF4J's own LoggingEventBuilder#addKeyValue) - those stay a separate, complementary
 * mechanism for a different shaped need (one log line, not a whole request).
 */
public final class ScopedKeyValues {

	private static final ScopedValue<List<KeyValues>> STACK = ScopedValue.newInstance();

	private ScopedKeyValues() {
	}

	/**
	 * Every KeyValues pushed so far, outermost first, empty if called outside any
	 * push/run. This is the retrieval a LogEventFactory-style caller would use - no
	 * names, nothing to look up, just "whatever's currently in scope."
	 */
	public static List<KeyValues> current() {
		return STACK.isBound() ? STACK.get() : List.of();
	}

	/** Convenience: current(), flattened into one KeyValues. */
	public static KeyValues currentMerged() {
		return KeyValues.merge(current());
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

		public void run(Runnable body) {
			push(build(), body);
		}

		public <T> T call(Callable<T> body) throws Exception {
			return push(build(), body);
		}

		private KeyValues build() {
			return KeyValues.of(keys.toArray(new String[0]), values.toArray(new String[0]));
		}

	}

	private static void push(KeyValues values, Runnable body) {
		ScopedValue.where(STACK, appended(values)).run(body);
	}

	private static <T> T push(KeyValues values, Callable<T> body) throws Exception {
		return ScopedValue.where(STACK, appended(values)).call(body::call);
	}

	private static List<KeyValues> appended(KeyValues values) {
		var next = new ArrayList<>(current());
		next.add(values);
		return List.copyOf(next);
	}

}
