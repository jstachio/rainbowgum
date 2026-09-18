import java.util.concurrent.StructuredTaskScope;

/**
 * Proves the reference implementation actually works, run directly (no Maven, no test
 * framework - this is a prototype, not something meant to ship yet).
 */
public class Demo {

	public static void main(String[] args) throws Exception {
		basicUsage();
		firstWinsOnNestedSameName();
		retrievalDoesNotNeedToKnowNamesAheadOfTime();
		structuredChildInheritsBinding();
		notBoundOutsideAnyScope();
	}

	static void basicUsage() {
		System.out.println("== basicUsage ==");
		ScopedKeyValues.builder()
			.add("requestId", "abc123")
			.add("tenant", "acme")
			.run("request", () -> {
				System.out.println("current(request) = " + ScopedKeyValues.current("request"));
			});
	}

	static void firstWinsOnNestedSameName() {
		System.out.println("== firstWinsOnNestedSameName ==");
		ScopedKeyValues.builder().add("requestId", "outer").run("request", () -> {
			System.out.println("outer sees: " + ScopedKeyValues.current("request"));
			// Nested call under the SAME name with DIFFERENT values - first wins, this
			// should have no effect; the outer binding stays in effect throughout.
			ScopedKeyValues.builder().add("requestId", "inner-should-be-ignored").run("request", () -> {
				System.out.println("inner sees (should still be 'outer'): " + ScopedKeyValues.current("request"));
			});
			System.out.println("outer still sees: " + ScopedKeyValues.current("request"));
		});
	}

	static void retrievalDoesNotNeedToKnowNamesAheadOfTime() {
		System.out.println("== retrievalDoesNotNeedToKnowNamesAheadOfTime ==");
		// Simulates an unrelated library registering its OWN named scope that a
		// LogEventFactory-style caller never heard of - currentAll() finds it anyway.
		ScopedKeyValues.builder().add("requestId", "abc123").run("request", () -> {
			ScopedKeyValues.builder().add("txId", "tx-789").run("some.library.FQCN.transaction", () -> {
				System.out.println("currentAll() merged = " + ScopedKeyValues.currentAll());
			});
		});
	}

	static void structuredChildInheritsBinding() throws Exception {
		System.out.println("== structuredChildInheritsBinding ==");
		ScopedKeyValues.builder().add("requestId", "parent-value").run("request", () -> {
			try (var scope = StructuredTaskScope.<String>open()) {
				var subtask = scope.fork(() -> {
					// A different (virtual) thread - same ScopedValue binding, inherited
					// structurally, no manual copying needed.
					return "child thread sees: " + ScopedKeyValues.current("request") + " on "
							+ Thread.currentThread();
				});
				scope.join();
				System.out.println(subtask.get());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	static void notBoundOutsideAnyScope() {
		System.out.println("== notBoundOutsideAnyScope ==");
		System.out.println("current(request) outside any run = " + ScopedKeyValues.current("request"));
		System.out.println("currentAll() outside any run = " + ScopedKeyValues.currentAll());
	}

}
