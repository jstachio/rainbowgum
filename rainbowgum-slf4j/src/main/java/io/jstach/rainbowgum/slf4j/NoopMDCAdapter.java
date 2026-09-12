package io.jstach.rainbowgum.slf4j;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * A {@link RainbowGumMDCAdapter} that never touches either of {@link ArrayMDCAdapter}'s
 * {@link ThreadLocal} fields - every method is a no-op or returns empty/null. Used
 * instead of {@link ArrayMDCAdapter} when {@code logging.mdc=DISABLED} or
 * {@code logging.global.threadlocalDisabled} is set - see
 * {@link RainbowGumSLF4JServiceProvider#initialize(io.jstach.rainbowgum.RainbowGum)} -
 * for deployments that want a hard guarantee of no {@link ThreadLocal} anywhere in the
 * logging path and are fine losing MDC entirely to get it.
 * <p>
 * {@link ArrayMDCAdapter#pushByKey(String, String)},
 * {@link ArrayMDCAdapter#popByKey(String)},
 * {@link ArrayMDCAdapter#getCopyOfDequeByKey(String)} and
 * {@link ArrayMDCAdapter#clearDequeByKey(String)} are already no-ops on the parent class,
 * so they are not overridden here.
 *
 * @apiNote whether a disabled MDC should alert/warn on use (someone called
 * {@code MDC.put(...)} expecting it to work) instead of silently doing nothing is still
 * an open question - not implemented either way yet, silently doing nothing is the
 * simplest starting point.
 */
final class NoopMDCAdapter extends RainbowGumMDCAdapter {

	@Override
	public void put(@NonNull String key, @Nullable String val) {
	}

	@Override
	public void remove(@Nullable String key) {
	}

	@Override
	public void clear() {
	}

	@Override
	public @Nullable String get(String key) {
		return null;
	}

	@Override
	public KeyValues keyValues() {
		return KeyValues.of();
	}

	@Override
	public MutableKeyValues copyMutableKeyValues() {
		return MutableKeyValues.of();
	}

	@Override
	public @Nullable Map<String, @Nullable String> getCopyOfContextMap() {
		return null;
	}

	@Override
	public void setContextMap(Map<String, @Nullable String> contextMap) {
	}

}
