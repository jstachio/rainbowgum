import java.util.concurrent.StructuredTaskScope;

/**
 * Proves the reference implementation actually works, run directly (no Maven, no test
 * framework - this is a prototype, not something meant to ship yet).
 */
public class Demo {

	public static void main(String[] args) throws Exception {
		typicalUsageOnePushForTheWholeRequest();
		nestedPushIsCumulativeNotACollision();
		structuredChildInheritsTheStack();
		siblingsAndParentDoNotSeeEachOthersAdditions();
		notBoundOutsideAnyPush();
	}

	static void typicalUsageOnePushForTheWholeRequest() {
		System.out.println("== typicalUsageOnePushForTheWholeRequest ==");
		// The expected real shape: one push wrapping the entire request, at the
		// boundary - not scattered through business logic.
		ScopedKeyValues.builder().add("requestId", "abc123").add("tenant", "acme").run(() -> {
			System.out.println("current() = " + ScopedKeyValues.current());
			System.out.println("currentMerged() = " + ScopedKeyValues.currentMerged());
		});
	}

	static void nestedPushIsCumulativeNotACollision() {
		System.out.println("== nestedPushIsCumulativeNotACollision ==");
		ScopedKeyValues.builder().add("requestId", "abc123").run(() -> {
			System.out.println("after outer push: " + ScopedKeyValues.current());
			ScopedKeyValues.builder().add("step", "validate").run(() -> {
				System.out.println("after inner push: " + ScopedKeyValues.current());
			});
			System.out.println("back to outer, inner's layer is gone: " + ScopedKeyValues.current());
		});
	}

	static void structuredChildInheritsTheStack() throws Exception {
		System.out.println("== structuredChildInheritsTheStack ==");
		ScopedKeyValues.builder().add("requestId", "parent-value").run(() -> {
			try (var scope = StructuredTaskScope.<String>open()) {
				var subtask = scope.fork(() -> "child sees: " + ScopedKeyValues.current() + " on "
						+ Thread.currentThread());
				scope.join();
				System.out.println(subtask.get());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	static void siblingsAndParentDoNotSeeEachOthersAdditions() throws Exception {
		System.out.println("== siblingsAndParentDoNotSeeEachOthersAdditions ==");
		ScopedKeyValues.builder().add("requestId", "req-1").run(() -> {
			try (var scope = StructuredTaskScope.<Void>open()) {
				System.out.println("parent before fork: " + ScopedKeyValues.current());
				scope.fork(() -> {
					ScopedKeyValues.builder().add("siblingA", "own-layer").run(() -> {
						System.out.println("sibling A sees: " + ScopedKeyValues.current());
					});
					return null;
				});
				scope.fork(() -> {
					System.out.println("sibling B sees (must NOT have A's layer): " + ScopedKeyValues.current());
					return null;
				});
				scope.join();
				System.out.println("parent after both children returned: " + ScopedKeyValues.current());
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
	}

	static void notBoundOutsideAnyPush() {
		System.out.println("== notBoundOutsideAnyPush ==");
		System.out.println("current() outside any push = " + ScopedKeyValues.current());
	}

}
