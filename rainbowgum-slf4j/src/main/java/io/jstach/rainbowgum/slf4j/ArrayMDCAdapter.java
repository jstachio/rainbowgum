package io.jstach.rainbowgum.slf4j;

import static java.util.Objects.requireNonNull;

import java.util.Deque;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class ArrayMDCAdapter implements MDCAdapter {

	final ThreadLocal<MutableKeyValues> copyOnThreadLocal = new ThreadLocal<>();

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

	private MutableKeyValues duplicateAndInsertNewMap(@Nullable MutableKeyValues oldMap) {

		MutableKeyValues newMap;

		if (oldMap != null) {
			/*
			 * No synchronization needed here: copyOnThreadLocal is a plain (not
			 * inheritable) ThreadLocal, so oldMap is never shared with another thread,
			 * and nothing that reads a KeyValues elsewhere in the codebase synchronizes
			 * on it either.
			 */
			newMap = oldMap.copy();
		}
		else {
			newMap = MutableKeyValues.of();
		}

		copyOnThreadLocal.set(newMap);
		return newMap;
	}

	@Override
	public void put(@NonNull String key, @Nullable String val) throws NullPointerException {
		requireNonNull(key, "key cannot be null");

		MutableKeyValues oldMap = copyOnThreadLocal.get();
		Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

		if (wasLastOpReadOrNull(lastOp) || oldMap == null) {
			MutableKeyValues newMap = duplicateAndInsertNewMap(oldMap);
			newMap.accept(key, val);
		}
		else {
			oldMap.accept(key, val);
		}
	}

	@Override
	public void remove(@Nullable String key) {
		if (key == null) {
			return;
		}
		MutableKeyValues oldMap = copyOnThreadLocal.get();
		if (oldMap == null)
			return;

		Integer lastOp = getAndSetLastOperation(WRITE_OPERATION);

		if (wasLastOpReadOrNull(lastOp)) {
			MutableKeyValues newMap = duplicateAndInsertNewMap(oldMap);
			newMap.remove(key);
		}
		else {
			oldMap.remove(key);
		}
	}

	@Override
	public void clear() {
		lastOperation.set(WRITE_OPERATION);
		copyOnThreadLocal.remove();
	}

	@Override
	public @Nullable String get(String key) {
		if (Objects.isNull(key)) {
			return null;
		}
		final MutableKeyValues map = copyOnThreadLocal.get();
		if (map != null) {
			return map.getValueOrNull(key);
		}
		else {
			return null;
		}
	}

	/**
	 * Get the current thread's MDC, or an empty {@link KeyValues} if nothing has been put
	 * yet. This method is intended to be used internally and the returned value
	 * <strong>should not be mutated by the caller</strong>. Calling this marks the
	 * returned instance (if not empty) as exposed so that the next
	 * {@link #put(String, String)} or {@link #remove(String)} on this thread will
	 * defensively copy before mutating instead of mutating in place.
	 * @return key values, never <code>null</code>.
	 */
	public KeyValues keyValues() {
		lastOperation.set(MAP_COPY_OPERATION);
		var m = copyOnThreadLocal.get();
		return m == null ? KeyValues.of() : m;
	}

	/**
	 * Copies the current thread's MDC into a new, independent mutable buffer, or creates
	 * an empty one if nothing has been put yet. The returned instance is never shared
	 * with what MDC currently holds so it is safe for the caller to mutate it freely
	 * (e.g. to seed a builder that will add more key values on top of a snapshot of MDC).
	 * @return a new mutable key values, never <code>null</code>.
	 */
	public MutableKeyValues copyMutableKeyValues() {
		MutableKeyValues oldMap = copyOnThreadLocal.get();
		if (oldMap == null) {
			return MutableKeyValues.of();
		}
		return oldMap.copy();
	}

	// /**
	// * Returns the keys in the MDC as a {@link Set}. The returned value can be null.
	// * @return keys.
	// */
	// public @Nullable Set<String> getKeys() {
	// MutableKeyValues map = keyValues();
	//
	// if (map != null) {
	// return map.copyToMap().keySet();
	// }
	// else {
	// return null;
	// }
	// }

	/**
	 * Return a copy of the current thread's context map. Returned value may be null.
	 * @return map copy.
	 */

	@Override
	public @Nullable Map<String, @Nullable String> getCopyOfContextMap() {
		MutableKeyValues hashMap = copyOnThreadLocal.get();
		if (hashMap == null) {
			return null;
		}
		else {
			return hashMap.copyToMap();
		}
	}

	@Override
	public void setContextMap(Map<String, @Nullable String> contextMap) {
		lastOperation.set(WRITE_OPERATION);

		MutableKeyValues newMap = MutableKeyValues.of(contextMap.size());
		newMap.putAll(contextMap);

		// the newMap replaces the old one for serialisation's sake
		copyOnThreadLocal.set(newMap);
	}

	@Override
	public void pushByKey(String key, String value) {
	}

	@Override
	public @Nullable String popByKey(String key) {
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