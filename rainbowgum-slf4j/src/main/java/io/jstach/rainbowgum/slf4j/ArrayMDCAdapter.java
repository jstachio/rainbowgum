package io.jstach.rainbowgum.slf4j;

import static java.util.Objects.requireNonNull;

import java.util.Deque;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.spi.MDCAdapter;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

class ArrayMDCAdapter implements MDCAdapter {

	final ThreadLocal<MutableKeyValues> mapThreadLocal = new ThreadLocal<>();

	@Override
	public void put(String key, @Nullable String val) throws NullPointerException {
		requireNonNull(key, "key cannot be null");
		MutableKeyValues map = mapThreadLocal.get();
		if (map == null) {
			map = MutableKeyValues.of();
			mapThreadLocal.set(map);
		}
		map.accept(key, val);
	}

	@Override
	public void remove(String key) {
		if (Objects.isNull(key)) {
			return;
		}
		MutableKeyValues map = mapThreadLocal.get();
		if (map != null) {
			map.remove(key);
		}
	}

	@Override
	public void clear() {
		mapThreadLocal.remove();
	}

	@Override
	public @Nullable String get(String key) {
		if (Objects.isNull(key)) {
			return null;
		}
		MutableKeyValues map = mapThreadLocal.get();
		return map != null ? map.getValueOrNull(key) : null;
	}

	/**
	 * Get the current thread's MDC as a map. This method is intended to be used
	 * internally.
	 * @return mutable key values.
	 */
	public @Nullable MutableKeyValues mutableKeyValuesOrNull() {
		return mapThreadLocal.get();
	}

	/**
	 * Return a copy of the current thread's context map. Returned value may be null.
	 * @return map copy.
	 */
	@Override
	public @Nullable Map<String, @Nullable String> getCopyOfContextMap() {
		MutableKeyValues map = mapThreadLocal.get();
		return map == null ? null : map.copyToMap();
	}

	@Override
	public void setContextMap(Map<String, @Nullable String> contextMap) {
		MutableKeyValues newMap = MutableKeyValues.of(contextMap.size());
		newMap.putAll(contextMap);
		mapThreadLocal.set(newMap);
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