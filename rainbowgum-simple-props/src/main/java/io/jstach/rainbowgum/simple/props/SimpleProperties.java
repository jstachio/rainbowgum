package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.annotation.CaseChanging;

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
 * These are loaded only when the base resource exists. An empty selection uses the
 * optional {@code default} profile; all other profile resources must exist.</li>
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
	 * Base-resource key validation. The base resource overrides
	 * {@link Builder#strict(StrictType)}. Accepts {@code off} or {@code false},
	 * {@code alert}, and {@code fail} or {@code true} (default).
	 */
	public static final String STRICT_PROPERTY = LogProperties.ROOT_PREFIX + "simpleprops.strict";

	/**
	 * Controls prefix validation of the base resource's property keys.
	 */
	@CaseChanging
	public enum StrictType {

		/** Disables prefix validation. */
		OFF,
		/** Records an error alert for each key without the logging prefix. */
		ALERT,
		/**
		 * Records error alerts and fails when supplying properties if any key lacks the
		 * logging prefix.
		 */
		FAIL;

		static StrictType parse(String value) {
			return switch (value.toLowerCase(java.util.Locale.ROOT)) {
				case "true" -> FAIL;
				case "false" -> OFF;
				default -> LogProperty.enumValue(StrictType.class, value, "true", "false");
			};
		}

	}

	/**
	 * Comma-separated profiles, in priority order. Resolved from system properties,
	 * environment variables, or {@link Builder#profiles(List)}, in that priority order,
	 * after the base classpath resource is found. With the default environment prefix,
	 * use {@code RAINBOWGUM_profiles}. For example,
	 * {@code logging.profiles=local-dev,dev} loads {@code logging-local-dev.properties}
	 * before {@code logging-dev.properties}, with {@code logging.properties} as fallback.
	 * A missing base resource disables all property sources from this module, regardless
	 * of profile selection. With no profiles selected, the optional {@code default}
	 * profile is used. It is not included automatically when other profiles are selected.
	 * A missing selected profile resource fails initialization unless its name is
	 * {@code default}. Files cannot activate additional profiles.
	 */
	public static final String PROFILES_PROPERTY = LogProperties.ROOT_PREFIX + "profiles";

	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

	private final List<LogProperties> properties;

	private final List<String> infoMessages;

	private final List<String> errorMessages;

	private final StrictType strict;

	private SimpleProperties(List<LogProperties> properties, List<String> infoMessages, List<String> errorMessages,
			StrictType strict) {
		this.properties = properties;
		this.infoMessages = infoMessages;
		this.errorMessages = errorMessages;
		this.strict = strict;
	}

	/**
	 * The layers described in this class's javadoc, highest priority first.
	 * @return properties, highest priority first.
	 * @throws IllegalArgumentException if strict validation is {@link StrictType#FAIL}
	 * and the base resource has unprefixed keys.
	 */
	public List<LogProperties> properties() {
		if (strict == StrictType.FAIL && !errorMessages.isEmpty()) {
			throw new IllegalArgumentException(
					"Invalid simple-props base resource:\n" + String.join("\n", errorMessages));
		}
		return properties;
	}

	void reportAlerts(LogAlerts alerts) {
		for (var message : infoMessages) {
			alerts.info(SimpleProperties.class, message);
		}
		var eventFactory = LogEventFactory.of(SimpleProperties.class.getName());
		for (var message : errorMessages) {
			alerts.alert(eventFactory.eventNoArg(Level.ERROR, message, null));
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

		private StrictType strict = StrictType.FAIL;

		/**
		 * Sets base-resource key validation. The base resource's
		 * {@value SimpleProperties#STRICT_PROPERTY} overrides this setting. Validation
		 * records error alerts. {@link StrictType#FAIL} also stops initialization when
		 * properties are supplied.
		 * @param strict validation mode, default {@link StrictType#FAIL}.
		 * @return this.
		 */
		public Builder strict(StrictType strict) {
			this.strict = Objects.requireNonNull(strict);
			return this;
		}

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
		 * resource exists, except for the optional {@code default} profile.
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
		 * letters, digits, underscores, and hyphens. An empty selection uses the optional
		 * {@code default} profile.
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
				return new SimpleProperties(List.of(), List.of("No properties resource found: " + resource), List.of(),
						strict);
			}
			var strictType = classpathProperties.forKey(STRICT_PROPERTY)
				.ofString()
				.map(StrictType::parse)
				.or(strict)
				.validateNow(SimpleProperties.class);
			var errorMessages = strictType != StrictType.OFF
					&& classpathProperties instanceof SimpleLogProperties simple ? simple.prefixErrors()
							: List.<String>of();
			var systemProperties = LogProperties.StandardProperties.SYSTEM_PROPERTIES;
			var environmentVariables = new EnvVarProperties(envPrefix, envLookup);
			var preProperties = LogProperties.of(List.of(systemProperties, environmentVariables));
			var profileResources = preProperties.forKey(PROFILES_PROPERTY).ofList().or(profiles).map(names -> {
				if (names.isEmpty()) {
					names = List.of("default");
				}
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
						if (name.equals("default")) {
							continue;
						}
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
			return new SimpleProperties(List.copyOf(properties), List.copyOf(infoMessages), errorMessages, strictType);
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
