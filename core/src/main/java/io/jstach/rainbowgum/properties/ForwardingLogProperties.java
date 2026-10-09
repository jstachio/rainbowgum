package io.jstach.rainbowgum.properties;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogReporter;

/**
 * Properties that forward every lookup to a delegate, for writing decorators that observe
 * or change lookups.
 * <p>
 * {@link #valueOrNull(String)}, {@link #listOrNull(String)}, {@link #mapOrNull(String)},
 * {@link #visit(String, BiFunction)}, {@link #description(String)}, {@link #order()}, and
 * {@link #report(Appendable)} forward to {@link #delegate()}. When the delegate's
 * {@code visit} passes the delegate itself to the visitor, this properties is passed
 * instead, so lookups that go through {@code visit}, such as
 * {@link LogProperties#forKey(String)}, reach the methods of this properties. The other
 * methods are the {@link LogProperties} defaults, which are built on these.
 */
public abstract class ForwardingLogProperties implements LogProperties, LogReporter.Reportable {

	/**
	 * For subclasses.
	 */
	protected ForwardingLogProperties() {
	}

	/**
	 * The properties lookups are forwarded to.
	 * @return delegate.
	 */
	protected abstract LogProperties delegate();

	@Override
	public @Nullable String valueOrNull(String key) {
		return delegate().valueOrNull(key);
	}

	@Override
	public @Nullable List<String> listOrNull(String key) {
		return delegate().listOrNull(key);
	}

	@Override
	public @Nullable Map<String, String> mapOrNull(String key) {
		return delegate().mapOrNull(key);
	}

	// Identity on purpose: only the delegate passing itself is replaced.
	@SuppressWarnings("ReferenceEquality")
	@Override
	public <R extends @Nullable Object> @Nullable R visit(String key,
			BiFunction<LogProperties, String, @Nullable R> visitor) {
		var delegate = delegate();
		return delegate.visit(key, (source, sourceKey) -> visitor.apply(source == delegate ? this : source, sourceKey));
	}

	@Override
	public String description(String key) {
		return delegate().description(key);
	}

	@Override
	public int order() {
		return delegate().order();
	}

	@Override
	public void report(Appendable out) throws IOException {
		if (delegate() instanceof LogReporter.Reportable r) {
			r.report(out);
		}
		else {
			out.append(delegate().toString());
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + delegate() + "]";
	}

}
