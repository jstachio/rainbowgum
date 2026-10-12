package io.jstach.rainbowgum;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogProperty.PropertyProblem;
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
	@Nullable String keyOrNull();

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
	public static final class NotFoundException extends NoSuchElementException implements PropertyProblem {

		private static final long serialVersionUID = 5484668688480940159L;

		NotFoundException(String s) {
			super(s);
		}

		@Override
		public String errorName() {
			return "NotFoundException";
		}

		/**
		 * Builds a {@link NotFoundException} for an unregistered scheme, with a message
		 * consistent across every kind of provider registry (output/publisher/encoder/
		 * etc). If the scheme happens to be one known to be provided by an optional
		 * module not currently on the classpath (see {@link ProviderModule}), the message
		 * is extended to say which dependency to add. Otherwise it suggests a similarly
		 * spelled scheme and lists the registered ones.
		 * @param component the kind of thing not found.
		 * @param scheme the URI scheme that had no provider registered for it.
		 * @param uri the full URI that was being resolved.
		 * @param registered the schemes registered for the component.
		 * @return exception, not thrown.
		 */
		static NotFoundException of(ProviderModule.ComponentType component, String scheme, URI uri,
				Collection<? extends String> registered) {
			String message = "No " + component.label() + " found. Scheme not registered. scheme: '" + scheme
					+ "', URI: '" + LogRedactor.redactUri(uri) + "'";
			var module = ProviderModule.find(component, scheme);
			if (module.isPresent()) {
				return new NotFoundException(message + moduleHint(module.get()));
			}
			var candidates = new TreeSet<String>(registered);
			for (var m : ProviderModule.values()) {
				if (m.componentType() == component) {
					candidates.add(m.scheme());
				}
			}
			String suggested = similarlySpelledScheme(scheme, candidates);
			if (suggested != null) {
				message += ". Did you mean '" + suggested + "'?";
			}
			// core. prefixed aliases only repeat the plain schemes.
			var listed = new TreeSet<String>(registered);
			listed.removeIf(r -> r.startsWith("core."));
			if (!listed.isEmpty()) {
				message += (suggested == null ? ". " : " ") + "Registered " + component.label() + " schemes: "
						+ String.join(", ", listed.stream().map(r -> "'" + r + "'").toList());
			}
			if (suggested != null && !registered.contains(suggested)) {
				var suggestedModule = ProviderModule.find(component, suggested);
				if (suggestedModule.isPresent()) {
					message += moduleHint(suggestedModule.get());
				}
			}
			return new NotFoundException(message);
		}

		private static String moduleHint(ProviderModule m) {
			return ". Scheme '" + m.scheme() + "' is provided by module '" + m.moduleName() + "' (Maven: '"
					+ m.mavenGav() + "'). Add that dependency. See " + m.docUrl();
		}

		/*
		 * The same spelling distance as unused key suggestions, but scaled to the
		 * scheme's length so a short unknown scheme is not matched to an unrelated short
		 * one.
		 */
		private static @Nullable String similarlySpelledScheme(String scheme, Set<String> candidates) {
			String normalized = UnusedKeyCheck.normalize(scheme);
			String best = null;
			int bestDistance = UnusedKeyCheck.MAX_DISTANCE + 1;
			for (String candidate : candidates) {
				if (UnusedKeyCheck.normalize(candidate).equals(normalized)) {
					return candidate;
				}
				int limit = Math.min(UnusedKeyCheck.MAX_DISTANCE, Math.max(1, candidate.length() / 3));
				int distance = UnusedKeyCheck.distance(scheme, candidate, bestDistance);
				if (distance <= limit && distance < bestDistance) {
					best = candidate;
					bestDistance = distance;
				}
			}
			return best;
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
					throw new IllegalArgumentException("URI is not proper: " + LogRedactor.redactUri(uri));
				}
				uri = new URI(path + ":///");
				if (uri.getScheme() == null) {
					/*
					 * A leading "/" or "." keeps the URI parser from ever attempting
					 * scheme detection, so appending ":///" above silently produces
					 * another scheme-less URI instead of throwing. Fail clearly here
					 * rather than letting an unresolvable URI reach a provider registry.
					 */
					throw new IllegalArgumentException("URI is not proper: " + LogRedactor.redactUri(uri));
				}
			}
		}
		catch (URISyntaxException e) {
			throw new IllegalArgumentException("URI is not proper: " + LogRedactor.redactUri(uri));
		}
		return uri;
	}

	static LogProviderRef normalize(LogProviderRef ref) {
		var uri = ref.uri();
		uri = DefaultLogProviderRef.normalize(uri);
		return new DefaultLogProviderRef(uri, ref.keyOrNull());
	}
}
