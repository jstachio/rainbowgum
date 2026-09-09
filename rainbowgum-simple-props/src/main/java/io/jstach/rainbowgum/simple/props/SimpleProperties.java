package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogProperties;

/**
 * Resolves {@link LogProperties} from up to three layers, highest priority first:
 * <ol>
 * <li>System properties
 * ({@link LogProperties.StandardProperties#SYSTEM_PROPERTIES}).</li>
 * <li>Environment variables, using {@value Builder#DEFAULT_ENV_PREFIX} (configurable) in
 * place of the {@code "logging."} lead segment of the key, remaining <code>.</code>s
 * replaced with <code>_</code>, and casing left exactly as-is - see
 * {@link Builder#envPrefix(String)}.</li>
 * <li>A classpath resource (default {@value Builder#DEFAULT_RESOURCE}, configurable)
 * parsed the same way {@link LogProperties.Builder#fromProperties(String)} parses any
 * other properties text - if the resource is not found this layer contributes
 * nothing.</li>
 * </ol>
 * Create one with {@link #builder()}, or just rely on {@link SimplePropertiesProvider}
 * picking up the defaults automatically via {@link java.util.ServiceLoader}.
 */
public final class SimpleProperties {

	private final List<LogProperties> properties;

	private SimpleProperties(List<LogProperties> properties) {
		this.properties = properties;
	}

	/**
	 * The three layers described in this class's javadoc, highest priority first.
	 * @return properties, highest priority first.
	 */
	public List<LogProperties> properties() {
		return properties;
	}

	/**
	 * Creates a builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a {@link SimpleProperties}.
	 */
	public static final class Builder {

		/**
		 * Default environment variable prefix: {@value #DEFAULT_ENV_PREFIX}.
		 */
		public static final String DEFAULT_ENV_PREFIX = "RAINBOWGUM_";

		/**
		 * Default classpath resource: {@value #DEFAULT_RESOURCE}.
		 */
		public static final String DEFAULT_RESOURCE = "classpath:/logging.properties";

		private String envPrefix = DEFAULT_ENV_PREFIX;

		private String resource = DEFAULT_RESOURCE;

		private Function<String, @Nullable String> envLookup = System::getenv;

		private Builder() {
		}

		/**
		 * Sets the environment variable prefix used in place of the key's
		 * {@code "logging."} lead segment (for example {@code logging.level.root} becomes
		 * {@code RAINBOWGUM_level_root} with the default prefix). Casing is not touched -
		 * only the prefix and the remaining <code>.</code>-to-<code>_</code> replacement
		 * are applied - so a key with mixed-case segments produces a mixed-case
		 * environment variable name.
		 * @param envPrefix prefix, default {@value #DEFAULT_ENV_PREFIX}.
		 * @return this.
		 */
		public Builder envPrefix(String envPrefix) {
			this.envPrefix = Objects.requireNonNull(envPrefix);
			return this;
		}

		/**
		 * Sets the classpath resource to load properties from. A leading
		 * {@code classpath:} scheme (with or without a following <code>/</code>) is
		 * stripped before resolving; the remainder is resolved as a plain classpath
		 * resource name. Not found is not an error - that layer simply contributes no
		 * properties.
		 * @param resource classpath resource, default {@value #DEFAULT_RESOURCE}.
		 * @return this.
		 */
		public Builder resource(String resource) {
			this.resource = Objects.requireNonNull(resource);
			return this;
		}

		/*
		 * Test-only hook so env var mapping can be tested without setting real
		 * environment variables (which the JVM cannot do portably at test time).
		 */
		Builder envLookup(Function<String, @Nullable String> envLookup) {
			this.envLookup = Objects.requireNonNull(envLookup);
			return this;
		}

		/**
		 * Builds the {@link SimpleProperties}.
		 * @return simple properties.
		 */
		public SimpleProperties build() {
			var systemProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			var environmentVariables = new EnvVarProperties(envPrefix, envLookup);
			var classpathProperties = loadResource(resource);
			return new SimpleProperties(List.of(systemProperties, environmentVariables, classpathProperties));
		}

		private static LogProperties loadResource(String resource) {
			String path = resource;
			if (path.startsWith("classpath:")) {
				path = path.substring("classpath:".length());
			}
			while (path.startsWith("/")) {
				path = path.substring(1);
			}
			ClassLoader cl = SimpleProperties.class.getClassLoader();
			if (cl == null) {
				cl = Thread.currentThread().getContextClassLoader();
			}
			try (InputStream in = cl == null ? null : cl.getResourceAsStream(path)) {
				if (in == null) {
					return LogProperties.StandardProperties.EMPTY;
				}
				String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				return LogProperties.builder()
					.description("SIMPLE_PROPS_FILE[" + resource + "]")
					.order(100)
					.fromProperties(content)
					.build();
			}
			catch (IOException e) {
				throw new UncheckedIOException("Failed to read classpath resource: " + resource, e);
			}
		}

	}

}
