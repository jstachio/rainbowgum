package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogProperties;

/**
 * Resolves {@link LogProperties} from the following layers, highest priority first:
 * <ol>
 * <li>System properties
 * ({@link LogProperties.StandardProperties#SYSTEM_PROPERTIES}).</li>
 * <li>Environment variables, using {@value Builder#DEFAULT_ENV_PREFIX} (configurable) in
 * place of the {@code "logging."} lead segment of the key, remaining <code>.</code>s
 * replaced with <code>_</code>, and casing left exactly as-is; see
 * {@link Builder#envPrefix(String)}.</li>
 * <li>Profile resources selected by {@value #PROFILES_PROPERTY}, first profile wins.
 * These are loaded only when the base resource exists; every selected profile resource
 * must exist.</li>
 * <li>A classpath resource (default {@value Builder#DEFAULT_RESOURCE}, configurable)
 * parsed the same way {@link LogProperties.Builder#fromProperties(String)} parses any
 * other properties text. If the base resource is absent, this module supplies no property
 * sources at all.</li>
 * </ol>
 * Create one with {@link #builder()}, or just rely on {@link SimplePropertiesProvider}
 * picking up the defaults automatically via {@link java.util.ServiceLoader}. The provider
 * reports selected profiles and classpath resource loading through {@link LogAlerts}.
 * <p>
 * <b>GraalVM native image</b>: this module bundles a {@code resource-config.json} (at
 * {@code META-INF/native-image/io.jstach.rainbowgum/rainbowgum-simple-props/}) that
 * includes {@code logging.properties} and {@code logging-<profile>.properties}
 * automatically. Profile names contain only ASCII letters, digits, underscores, and
 * hyphens. Custom {@link Builder#resource(String)} names and their profile variants need
 * corresponding native-image resource metadata.
 */
public final class SimpleProperties {

	/**
	 * Comma-separated profiles, in priority order. Resolved from system properties,
	 * environment variables, or {@link Builder#profiles(List)}, in that priority order,
	 * after the base classpath resource is found. With the default environment prefix,
	 * use {@code RAINBOWGUM_profiles}. For example,
	 * {@code logging.profiles=local-dev,dev} loads {@code logging-local-dev.properties}
	 * before {@code logging-dev.properties}, with {@code logging.properties} as fallback.
	 * A missing base resource disables all property sources from this module, regardless
	 * of profile selection. A missing selected profile resource fails initialization.
	 * Files cannot activate additional profiles.
	 */
	public static final String PROFILES_PROPERTY = LogProperties.ROOT_PREFIX + "profiles";

	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

	private final List<LogProperties> properties;

	private final List<String> infoMessages;

	private SimpleProperties(List<LogProperties> properties, List<String> infoMessages) {
		this.properties = properties;
		this.infoMessages = infoMessages;
	}

	/**
	 * The layers described in this class's javadoc, highest priority first.
	 * @return properties, highest priority first.
	 */
	public List<LogProperties> properties() {
		return properties;
	}

	void reportAlerts(LogAlerts alerts) {
		for (var message : infoMessages) {
			alerts.info(SimpleProperties.class, message);
		}
	}

	private record ProfileResources(List<String> names, List<LogProperties> properties, List<String> resources) {
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

		private List<String> profiles = List.of();

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
		 * resource name. Profile names are inserted before a trailing {@code .properties}
		 * extension, or appended with a hyphen if there is no such extension. A missing
		 * base resource makes this module supply no property sources, regardless of
		 * profile selection. Missing selected profile resources fail when the base
		 * resource exists.
		 * @apiNote unlike the default resource name, a custom one here is not covered by
		 * this module's bundled GraalVM {@code resource-config.json} - see this class's
		 * javadoc.
		 * @param resource classpath resource, default {@value #DEFAULT_RESOURCE}.
		 * @return this.
		 */
		public Builder resource(String resource) {
			this.resource = Objects.requireNonNull(resource);
			return this;
		}

		/**
		 * Sets fallback profiles in priority order. A system property or environment
		 * variable for {@value SimpleProperties#PROFILES_PROPERTY} overrides this list,
		 * including when it selects no profiles. Profile names must contain only ASCII
		 * letters, digits, underscores, and hyphens.
		 * @param profiles fallback profile names, first profile wins.
		 * @return this.
		 */
		public Builder profiles(List<String> profiles) {
			this.profiles = List.copyOf(profiles);
			return this;
		}

		/**
		 * Sets fallback profiles in priority order.
		 * @param profiles fallback profile names, first profile wins.
		 * @return this.
		 * @see #profiles(List)
		 */
		public Builder profiles(String... profiles) {
			return profiles(List.of(profiles));
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
			var classpathProperties = loadResource(resource);
			if (classpathProperties == LogProperties.StandardProperties.EMPTY) {
				return new SimpleProperties(List.of(), List.of("No properties resource found: " + resource));
			}
			var systemProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			var environmentVariables = new EnvVarProperties(envPrefix, envLookup);
			var preProperties = LogProperties.of(List.of(systemProperties, environmentVariables));
			var profileResources = preProperties.forKey(PROFILES_PROPERTY).ofList().or(profiles).map(names -> {
				var layers = new ArrayList<LogProperties>();
				var resources = new ArrayList<String>();
				for (var name : names) {
					if (!PROFILE_NAME.matcher(name).matches()) {
						throw new IllegalArgumentException("Invalid profile name '" + name
								+ "': use only ASCII letters, digits, underscores, and hyphens");
					}
					String profileResource = resource.endsWith(".properties")
							? resource.substring(0, resource.length() - ".properties".length()) + "-" + name
									+ ".properties"
							: resource + "-" + name;
					var profileProperties = loadResource(profileResource);
					if (profileProperties == LogProperties.StandardProperties.EMPTY) {
						throw new IllegalArgumentException("Missing classpath resource '" + profileResource
								+ "' for selected profile '" + name + "'");
					}
					layers.add(profileProperties);
					resources.add(profileResource);
				}
				return new ProfileResources(List.copyOf(names), List.copyOf(layers), List.copyOf(resources));
			}).validateNow(SimpleProperties.class);
			var properties = new ArrayList<LogProperties>();
			properties.add(systemProperties);
			properties.add(environmentVariables);
			properties.addAll(profileResources.properties());
			properties.add(classpathProperties);
			var infoMessages = new ArrayList<String>();
			infoMessages.add("Found profiles: " + profileResources.names());
			infoMessages.add("Loaded properties resource: " + resource);
			for (var profileResource : profileResources.resources()) {
				infoMessages.add("Loaded properties resource: " + profileResource);
			}
			return new SimpleProperties(List.copyOf(properties), List.copyOf(infoMessages));
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
				return SimpleLogProperties.read(new StringReader(content), resource);
			}
			catch (IOException e) {
				throw new UncheckedIOException("Failed to read classpath resource: " + resource, e);
			}
		}

	}

}
