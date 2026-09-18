import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * The SPI a logging implementation provides - analogous to org.slf4j.MDC's MDCAdapter or
 * org.slf4j.MarkerFactory's IMarkerFactory. Manages named scopes the way MarkerFactory
 * manages marker names: a name is a request for a *shared, interned* handle, not a
 * request to create a brand new independent one every time.
 */
public interface ScopedKeyValuesFactory {

	/**
	 * Returns the (interned) scope for this name - the same name always returns the
	 * same Scope instance, so unrelated code that agrees on a name (e.g. by convention,
	 * or because it's a well-known constant) actually sees each other's bindings.
	 */
	Scope getScope(String name);

	/**
	 * The tricky part: at the point a LogEvent is built, the framework does not know
	 * which named scopes exist - some library dependency may have registered its own
	 * under its own FQCN-based name, entirely unknown to core. This walks every name
	 * ever interned via getScope(...) and returns the KeyValues for whichever ones are
	 * currently bound *on this thread*, in registration order. Never allocates for the
	 * common "nothing bound" case (see DefaultScopedKeyValuesFactory).
	 */
	List<KeyValues> currentAll();

	interface Scope {

		String name();

		/**
		 * If this scope is already bound (we're nested inside a same-named run/call
		 * already), body just runs directly - first wins, the outer binding stays in
		 * effect. Otherwise binds values for the duration of body.
		 */
		void run(KeyValues values, Runnable body);

		<T> T call(KeyValues values, Callable<T> body) throws Exception;

		Optional<KeyValues> current();

	}

}
