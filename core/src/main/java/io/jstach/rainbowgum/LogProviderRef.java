package io.jstach.rainbowgum;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperty.Result;

/**
 * Log LogProvider Source.
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

	/**
	 * Thrown if a provider could not be found for the ref.
	 */
	public static class NotFoundException extends NoSuchElementException {

		private static final long serialVersionUID = 5484668688480940159L;

		NotFoundException(String s) {
			super(s);
			// TODO Auto-generated constructor stub
		}

	}

}

record DefaultLogProviderRef(URI uri, @Nullable String keyOrNull) implements LogProviderRef {
	static URI normalize(URI uri) {
		String scheme = uri.getScheme();
		String path = uri.getPath();
		try {
			if (scheme == null) {
				if (path == null) {
					throw new IllegalArgumentException("URI is not proper: " + uri);
				}
				if (path.startsWith("./") || path.startsWith("/")) {
					uri = new URI("name://" + path);
				}
				else {
					uri = new URI(path + ":///");
				}
			}
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI is not proper: " + uri);
		}
		return uri;
	}

	static LogProviderRef normalize(LogProviderRef ref) {
		var uri = ref.uri();
		uri = DefaultLogProviderRef.normalize(uri);
		return new DefaultLogProviderRef(uri, ref.keyOrNull());
	}
}