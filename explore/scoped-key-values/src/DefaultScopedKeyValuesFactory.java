import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reference implementation. One real java.lang.ScopedValue<KeyValues> per interned
 * name, held in a name -> Scope map. currentAll() walks a *separate*, insertion-ordered
 * list of every Scope ever created (not the map's own entries, since
 * ConcurrentHashMap's iteration order isn't insertion order and isn't worth relying on)
 * checking isBound() on each - correct for the "just one scope so far" case, and still
 * correct (just O(registered names) instead of O(1)) once there are a handful more.
 */
public final class DefaultScopedKeyValuesFactory implements ScopedKeyValuesFactory {

	public static final DefaultScopedKeyValuesFactory INSTANCE = new DefaultScopedKeyValuesFactory();

	private final ConcurrentHashMap<String, Scope> scopes = new ConcurrentHashMap<>();

	private final List<Scope> registrationOrder = new CopyOnWriteArrayList<>();

	private DefaultScopedKeyValuesFactory() {
	}

	@Override
	public Scope getScope(String name) {
		return scopes.computeIfAbsent(name, n -> {
			var scope = new DefaultScope(n);
			registrationOrder.add(scope);
			return scope;
		});
	}

	@Override
	public List<KeyValues> currentAll() {
		List<KeyValues> result = null;
		for (var scope : registrationOrder) {
			var current = scope.current();
			if (current.isPresent()) {
				if (result == null) {
					result = new ArrayList<>(4);
				}
				result.add(current.get());
			}
		}
		return result == null ? List.of() : result;
	}

	private static final class DefaultScope implements Scope {

		private final String name;

		private final java.lang.ScopedValue<KeyValues> scopedValue = java.lang.ScopedValue.newInstance();

		private DefaultScope(String name) {
			this.name = name;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public void run(KeyValues values, Runnable body) {
			if (scopedValue.isBound()) {
				// first wins - already inside a same-named scope, don't rebind.
				body.run();
				return;
			}
			java.lang.ScopedValue.where(scopedValue, values).run(body);
		}

		@Override
		public <T> T call(KeyValues values, Callable<T> body) throws Exception {
			if (scopedValue.isBound()) {
				return body.call();
			}
			return java.lang.ScopedValue.where(scopedValue, values).call(body::call);
		}

		@Override
		public Optional<KeyValues> current() {
			return scopedValue.isBound() ? Optional.of(scopedValue.get()) : Optional.empty();
		}

	}

}
