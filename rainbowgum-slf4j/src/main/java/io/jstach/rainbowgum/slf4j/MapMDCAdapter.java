package io.jstach.rainbowgum.slf4j;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.spi.MDCAdapter;

public class MapMDCAdapter implements MDCAdapter {

	// The internal map is copied so as

	// We wish to avoid unnecessarily copying of the map. To ensure
	// efficient/timely copying, we have a variable keeping track of the last
	// operation. A copy is necessary on 'put' or 'remove' but only if the last
	// operation was a 'get'. Get operations never necessitate a copy nor
	// successive 'put/remove' operations, only a get followed by a 'put/remove'
	// requires copying the map.
	// See http://jira.qos.ch/browse/LOGBACK-620 for the original discussion.

	// We no longer use CopyOnInheritThreadLocal in order to solve LBCLASSIC-183
	// Initially the contents of the thread local in parent and child threads
	// reference the same map. However, as soon as a thread invokes the put()
	// method, the maps diverge as they should.
	final ThreadLocal<@Nullable Map<@NonNull String, @Nullable String>> copyOnThreadLocal = new ThreadLocal<>();

	private static final int WRITE_OPERATION = 1;

	private static final int MAP_COPY_OPERATION = 2;

	// keeps track of the last operation performed
	final ThreadLocal<Integer> lastOperation = new ThreadLocal<Integer>();

	private Integer getAndSetLastOperation(int op) {
		Integer lastOp = lastOperation.get();
		lastOperation.set(op);
		return lastOp;
	}

	private boolean wasLastOpReadOrNull(@Nullable Integer lastOp) {
		return lastOp == null || lastOp.intValue() == MAP_COPY_OPERATION;
	}

	private Map<@NonNull String, @Nullable String> duplicateAndInsertNewMap(
			@Nullable Map<String, @Nullable String> oldMap) {
		Map<@NonNull String, @Nullable String> newMap = Collections.synchronizedMap(new HashMap<>());
		if (oldMap != null) {
			// we don't want the parent thread modifying oldMap while we are
			// iterating over it
			synchronized (oldMap) {
				newMap.putAll(oldMap);
			}
		}

		copyOnThreadLocal.set(newMap);
		return newMap;
	}

	/**
	 * Put a context value (the <code>val</code> parameter) as identified with the
	 * <code>key</code> parameter into the current thread's context map. Note that
	 * contrary to log4j, the <code>val</code> parameter can be null.
	 * <p/>
	 * <p/>
	 * If the current thread does not have a context map it is created as a side effect of
	 * this call.
	 * @throws NullPointerException in case the "key" parameter is null
	 */
	public void put(@NonNull String key, @Nullable String val) throws NullPointerException {
		requireNonNull(key, "key cannot be null");

		Map<String, @Nullable String> oldMap = copyOnThreadLocal.get();
		Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

		if (wasLastOpReadOrNull(lastOp) || oldMap == null) {
			Map<String, @Nullable String> newMap = duplicateAndInsertNewMap(oldMap);
			newMap.put(key, val);
		}
		else {
			oldMap.put(key, val);
		}
	}

	/**
	 * Remove the the context identified by the <code>key</code> parameter.
	 * <p/>
	 */
	public void remove(@Nullable String key) {
		if (key == null) {
			return;
		}
		Map<String, @Nullable String> oldMap = copyOnThreadLocal.get();
		if (oldMap == null)
			return;

		Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

		if (wasLastOpReadOrNull(lastOp)) {
			Map<String, @Nullable String> newMap = duplicateAndInsertNewMap(oldMap);
			newMap.remove(key);
		}
		else {
			oldMap.remove(key);
		}
	}

	/**
	 * Clear all entries in the MDC.
	 */
	public void clear() {
		lastOperation.set(WRITE_OPERATION);
		copyOnThreadLocal.remove();
	}

	/**
	 * Get the context identified by the <code>key</code> parameter.
	 * <p/>
	 */
	public @Nullable String get(String key) {
		if (Objects.isNull(key)) {
			return null;
		}
		final Map<String, @Nullable String> map = copyOnThreadLocal.get();
		if (map != null) {
			return map.get(key);
		}
		else {
			return null;
		}
	}

	/**
	 * Get the current thread's MDC as a map. This method is intended to be used
	 * internally.
	 */
	public @Nullable Map<String, @Nullable String> getPropertyMap() {
		lastOperation.set(MAP_COPY_OPERATION);
		return copyOnThreadLocal.get();
	}

	/**
	 * Returns the keys in the MDC as a {@link Set}. The returned value can be null.
	 */
	@Nullable
	public Set<String> getKeys() {
		Map<String, @Nullable String> map = getPropertyMap();

		if (map != null) {
			return map.keySet();
		}
		else {
			return null;
		}
	}

	/**
	 * Return a copy of the current thread's context map. Returned value may be null.
	 */
	@Nullable
	public Map<String, @Nullable String> getCopyOfContextMap() {
		Map<String, @Nullable String> hashMap = copyOnThreadLocal.get();
		if (hashMap == null) {
			return null;
		}
		else {
			return new HashMap<>(hashMap);
		}
	}

	public void setContextMap(Map<String, @Nullable String> contextMap) {
		lastOperation.set(WRITE_OPERATION);

		Map<String, @Nullable String> newMap = Collections.synchronizedMap(new HashMap<>());
		newMap.putAll(contextMap);

		// the newMap replaces the old one for serialisation's sake
		copyOnThreadLocal.set(newMap);
	}

	@Override
	public void pushByKey(String key, String value) {
	}

	@Override
	public @Nullable String popByKey(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public @Nullable Deque<String> getCopyOfDequeByKey(String key) {
		return null;
	}

	@Override
	public void clearDequeByKey(String key) {

	}

}
