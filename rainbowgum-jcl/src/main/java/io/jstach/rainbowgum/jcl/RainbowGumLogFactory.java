package io.jstach.rainbowgum.jcl;

import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.svc.ServiceProvider;

/**
 * Discovered by {@link LogFactory#getFactory()} via {@link java.util.ServiceLoader}
 * (Commons Logging's own {@code module-info.java} declares
 * {@code uses org.apache.commons.logging.LogFactory}) or, on the classpath, via the
 * generated {@code META-INF/services/org.apache.commons.logging.LogFactory} entry.
 * Creates {@link RainbowGumLog} instances - see that class and
 * {@link ChangeableRainbowGumLog}/{@link LevelLog} for how logging is actually
 * dispatched.
 */
@ServiceProvider(LogFactory.class)
public final class RainbowGumLogFactory extends LogFactory {

	private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public RainbowGumLogFactory() {
	}

	@Override
	public Log getInstance(Class<?> clazz) {
		return getInstance(clazz.getName());
	}

	@Override
	public Log getInstance(String name) {
		return new RainbowGumLog(name);
	}

	@Override
	public void release() {
	}

	@Override
	public @Nullable Object getAttribute(String name) {
		return attributes.get(name);
	}

	@Override
	public String[] getAttributeNames() {
		return attributes.keySet().toArray(new String[0]);
	}

	@Override
	public void removeAttribute(String name) {
		attributes.remove(name);
	}

	@Override
	public void setAttribute(String name, @Nullable Object value) {
		if (value == null) {
			attributes.remove(name);
		}
		else {
			attributes.put(name, value);
		}
	}

}
