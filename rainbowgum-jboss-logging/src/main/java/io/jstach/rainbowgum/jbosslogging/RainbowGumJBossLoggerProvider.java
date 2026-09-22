package io.jstach.rainbowgum.jbosslogging;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.logging.LoggerProvider;
import org.jspecify.annotations.Nullable;

import io.jstach.svc.ServiceProvider;

/**
 * Native {@link LoggerProvider} for JBoss Logging backed directly by
 * {@link io.jstach.rainbowgum.LogRouter#global()}, not through SLF4J.
 * <p>
 * MDC/NDC storage below is ported from upstream's own
 * {@code org.jboss.logging.AbstractLoggerProvider}/{@code AbstractMdcLoggerProvider}
 * (same behavior, including {@code getNdc()}'s space-joined merged form) since both are
 * package-private and cannot be extended from outside {@code org.jboss.logging}. MDC
 * values are stringified on put (mirroring {@code Slf4jLoggerProvider}'s own
 * {@code MDC.put(key, String.valueOf(value))}) so they can be surfaced directly as
 * {@link io.jstach.rainbowgum.KeyValues} without a second conversion step.
 */
@ServiceProvider(LoggerProvider.class)
public final class RainbowGumJBossLoggerProvider implements LoggerProvider {

	private final ThreadLocal<Map<String, String>> mdcMap = new ThreadLocal<>();

	private final ThreadLocal<ArrayDeque<NdcEntry>> ndcStack = new ThreadLocal<>();

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public RainbowGumJBossLoggerProvider() {
	}

	@Override
	public Logger getLogger(String name) {
		return new RainbowGumJBossLogger(name, this);
	}

	/**
	 * The current thread's MDC, already string-valued, ready to become
	 * {@link io.jstach.rainbowgum.KeyValues}.
	 * @return possibly empty, never <code>null</code>.
	 */
	Map<String, String> mdcSnapshot() {
		var map = mdcMap.get();
		return map == null || map.isEmpty() ? Collections.emptyMap() : map;
	}

	@Override
	public void clearMdc() {
		var map = mdcMap.get();
		if (map != null) {
			map.clear();
		}
	}

	@Override
	public @Nullable Object getMdc(String key) {
		var map = mdcMap.get();
		return map == null ? null : map.get(key);
	}

	@Override
	public Map<String, Object> getMdcMap() {
		var map = mdcMap.get();
		return map == null ? Collections.emptyMap() : new LinkedHashMap<>(map);
	}

	@Override
	public @Nullable Object putMdc(String key, @Nullable Object value) {
		var map = mdcMap.get();
		if (map == null) {
			map = new LinkedHashMap<>();
			mdcMap.set(map);
		}
		if (value == null) {
			return map.remove(key);
		}
		return map.put(key, String.valueOf(value));
	}

	@Override
	public void removeMdc(String key) {
		var map = mdcMap.get();
		if (map != null) {
			map.remove(key);
		}
	}

	@Override
	public void clearNdc() {
		var stack = ndcStack.get();
		if (stack != null) {
			stack.clear();
		}
	}

	@Override
	public @Nullable String getNdc() {
		var stack = ndcStack.get();
		return stack == null || stack.isEmpty() ? null : stack.peek().merged;
	}

	@Override
	public int getNdcDepth() {
		var stack = ndcStack.get();
		return stack == null ? 0 : stack.size();
	}

	@Override
	public String peekNdc() {
		var stack = ndcStack.get();
		return stack == null || stack.isEmpty() ? "" : stack.peek().current;
	}

	@Override
	public String popNdc() {
		var stack = ndcStack.get();
		return stack == null || stack.isEmpty() ? "" : stack.pop().current;
	}

	@Override
	public void pushNdc(String message) {
		var stack = ndcStack.get();
		if (stack == null) {
			stack = new ArrayDeque<>();
			ndcStack.set(stack);
		}
		stack.push(stack.isEmpty() ? new NdcEntry(message) : new NdcEntry(stack.peek(), message));
	}

	@Override
	public void setNdcMaxDepth(int maxDepth) {
		var stack = ndcStack.get();
		if (stack != null) {
			while (stack.size() > maxDepth) {
				stack.pop();
			}
		}
	}

	private static final class NdcEntry {

		private final String merged;

		private final String current;

		NdcEntry(String current) {
			this.merged = current;
			this.current = current;
		}

		NdcEntry(NdcEntry parent, String current) {
			this.merged = parent.merged + ' ' + current;
			this.current = current;
		}

	}

}
