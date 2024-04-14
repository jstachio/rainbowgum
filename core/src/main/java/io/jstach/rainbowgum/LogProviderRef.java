package io.jstach.rainbowgum;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperty.Result;

/**
 * Log Provider Source.
 */
public sealed interface LogProviderRef {

	/**
	 * URI of the log provider.
	 * @return uri not <code>null</code>.
	 */
	URI uri();

	/**
	 * Property key from where the URI came from or <code>null</code>.
	 * @return property key.
	 */
	@Nullable
	String keyOrNull();

	/**
	 * Creates a log provider ref from URI.
	 * @param uri uri.
	 * @return ref.
	 */
	public static LogProviderRef of(URI uri) {
		return new DefaultLogProviderRef(uri, null);
	}

	/**
	 * Create a provider ref from a property result.
	 * @param property successful URI result.
	 * @return provider ref.
	 */
	public static LogProviderRef of(Result.Success<? extends URI> property) {
		return new DefaultLogProviderRef(property.value(), property.key());
	}

	/**
	 * Creates a log provider ref from URI.
	 * @param uri uri.
	 * @param key property key where URI came from.
	 * @return ref.
	 */
	public static LogProviderRef of(URI uri, @Nullable String key) {
		return new DefaultLogProviderRef(uri, key);
	}

}

record DefaultLogProviderRef(URI uri, @Nullable String keyOrNull) implements LogProviderRef {
}