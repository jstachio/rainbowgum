package io.jstach.rainbowgum.simple.props;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
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
import io.jstach.rainbowgum.annotation.EnumAlias;

/**
 * Resolves {@link LogProperties} from the following layers, highest priority first:
 * <ol>
 * <li>The optional watched level file, enabled by {@value #LEVEL_FILE_PROPERTY}. Only
 * {@code logging.level} and {@code logging.level.*} keys are supplied by this
 * source.</li>
 * <li>System properties
 * ({@link LogProperties.StandardProperties#SYSTEM_PROPERTIES}).</li>
 * <li>Environment variables, using {@value Builder#DEFAULT_ENV_PREFIX} in place of the
 * {@code "logging."} lead segment of the key, remaining <code>.</code>s replaced with
 * <code>_</code>, and casing left exactly as-is. The prefix is set by
 * {@value #ENV_PREFIX_PROPERTY} in the base resource or by
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
	 * How resource keys without the logging prefix are treated. The base resource
	 * overrides {@link Builder#strict(StrictType)}. Accepts {@code short} (default),
	 * {@code off} or {@code false}, {@code alert}, and {@code fail} or {@code true}. It
	 * is always written with its full key, even when {@code short} allows other keys
	 * without the logging prefix.
	 * <p>
	 * Property: <code>{@index logging.simpleprops.strict}</code>
	 */
	public static final String STRICT_PROPERTY = LogProperties.ROOT_PREFIX + "simpleprops.strict";

	/**
	 * Controls how resource property keys without the logging prefix are treated.
	 */
	@CaseChanging
	public enum StrictType {

		/** Disables prefix validation. */
		@EnumAlias("false")
		OFF,
		/** Records an error alert for each key without the logging prefix. */
		ALERT,
		/**
		 * Records error alerts and fails when supplying properties if any key lacks the
		 * logging prefix.
		 */
		@EnumAlias("true")
		FAIL,
		/**
		 * Keys without the logging prefix get it, in the base resource and profile
		 * resources: <code>level.com.myco=debug</code> is
		 * <code>logging.level.com.myco=debug</code>. When both forms of a key are given,
		 * the later line wins, as with any repeated key. A base resource with keys only
		 * java.util.logging uses, such as <code>handlers</code> or <code>.level</code>,
		 * fails like {@link #FAIL}, since its configuration file is also named
		 * <code>logging.properties</code>.
		 */
		SHORT;

		static StrictType parse(String value) {
			return switch (value.toLowerCase(java.util.Locale.ROOT)) {
				case "true" -> FAIL;
				case "false" -> OFF;
				default -> LogProperty.enumValue(StrictType.class, value, "true", "false");
			};
		}

	}

	/**
	 * The environment variable prefix used in place of the key's {@code "logging."} lead
	 * segment, read only from the base resource, before any environment variable. For
	 * example {@code logging.simpleprops.envPrefix=MYAPP_LOG_} makes
	 * {@code logging.level.com.myapp} the environment variable
	 * {@code MYAPP_LOG_level_com_myapp}. It overrides {@link Builder#envPrefix(String)}.
	 * The prefix must be ASCII letters, digits, and underscores, not start with a digit,
	 * and end with <code>_</code>. It must not start with <code>HTTP_</code> in any case,
	 * since CGI and similar gateways set those variables from request headers.
	 * <p>
	 * Property: <code>{@index logging.simpleprops.envPrefix}</code>
	 */
	public static final String ENV_PREFIX_PROPERTY = LogProperties.ROOT_PREFIX + "simpleprops.envPrefix";

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
	 * <p>
	 * Property: <code>{@index logging.profiles}</code>
	 */
	public static final String PROFILES_PROPERTY = LogProperties.ROOT_PREFIX + "profiles";

	/**
	 * Whether to watch a level file for log levels, see
	 * {@value #LEVEL_FILE_PATH_PROPERTY}. Accepts {@code off} or {@code false} (default)
	 * and {@code watch} or {@code true}. Resolved from system properties, environment
	 * variables, or the base resource, and only when the base resource exists.
	 * <p>
	 * Property: <code>{@index logging.simpleprops.levelFile}</code>
	 */
	public static final String LEVEL_FILE_PROPERTY = LogProperties.ROOT_PREFIX + "simpleprops.levelFile";

	/**
	 * Where the watched level file is, default <code>level.properties</code>. A relative
	 * path is resolved against the working directory. Resolved from the same sources as
	 * {@value #LEVEL_FILE_PROPERTY}, never from profile resources or the level file
	 * itself. Where POSIX permissions are available, the file is refused while it or its
	 * directory can be written by group or others, or is owned by someone other than the
	 * user running the application or root. Symbolic links also have their resolved
	 * target's directory checked; a missing symbolic link target is refused.
	 * <p>
	 * Property: <code>{@index logging.simpleprops.levelFilePath}</code>
	 */
	public static final String LEVEL_FILE_PATH_PROPERTY = LogProperties.ROOT_PREFIX + "simpleprops.levelFilePath";

	/**
	 * Whether to watch a level file, see {@value #LEVEL_FILE_PROPERTY}.
	 */
	@CaseChanging
	public enum LevelFileType {

		/** No level file. */
		@EnumAlias("false")
		OFF,
		/**
		 * Polls the level file, see {@value #LEVEL_FILE_PATH_PROPERTY}. Its
		 * <code>logging.level</code> keys take precedence over every other source, and
		 * level changes are allowed unless {@code logging.change.mode} or
		 * {@code logging.change.logger} is set otherwise. Keys may leave off the logging
		 * prefix: <code>level.com.myco=debug</code> is
		 * <code>logging.level.com.myco=debug</code>. Other keys in the file are ignored.
		 */
		@EnumAlias("true")
		WATCH;

		static LevelFileType parse(String value) {
			return switch (value.toLowerCase(java.util.Locale.ROOT)) {
				case "true" -> WATCH;
				case "false" -> OFF;
				default -> LogProperty.enumValue(LevelFileType.class, value, "true", "false");
			};
		}

	}

	private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]+");

	private static final Pattern ENV_PREFIX = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

	private static String parseEnvPrefix(String value) {
		if (!ENV_PREFIX.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid environment variable prefix '" + value
					+ "': use only ASCII letters, digits, and underscores, not starting with a digit");
		}
		if (!value.endsWith("_")) {
			throw new IllegalArgumentException("Invalid environment variable prefix '" + value
					+ "': end it with an underscore, as in '" + value + "_'");
		}
		if (value.toUpperCase(java.util.Locale.ROOT).startsWith("HTTP_")) {
			throw new IllegalArgumentException("Invalid environment variable prefix '" + value
					+ "': CGI and similar gateways set HTTP_ variables from request headers (see httpoxy)");
		}
		return value;
	}

	private final List<LogProperties> properties;

	private final List<String> infoMessages;

	private final List<String> errorMessages;

	private final StrictType strict;

	private final @Nullable LevelFileProperties levelFile;

	private SimpleProperties(List<LogProperties> properties, List<String> infoMessages, List<String> errorMessages,
			StrictType strict, @Nullable LevelFileProperties levelFile) {
		this.properties = properties;
		this.infoMessages = infoMessages;
		this.errorMessages = errorMessages;
		this.strict = strict;
		this.levelFile = levelFile;
	}

	/*
	 * The watched level file, when enabled.
	 */
	@Nullable LevelFileProperties levelFile() {
		return levelFile;
	}

	/**
	 * The layers described in this class's javadoc, highest priority first.
	 * @return properties, highest priority first.
	 * @throws IllegalArgumentException if strict validation is {@link StrictType#FAIL}
	 * and the base resource has unprefixed keys, or is {@link StrictType#SHORT} and the
	 * base resource has java.util.logging keys.
	 */
	public List<LogProperties> properties() {
		if ((strict == StrictType.FAIL || strict == StrictType.SHORT) && !errorMessages.isEmpty()) {
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

		private StrictType strict = StrictType.SHORT;

		/**
		 * Sets how resource keys without the logging prefix are treated. The base
		 * resource's {@value SimpleProperties#STRICT_PROPERTY} overrides this setting.
		 * {@link StrictType#ALERT} and {@link StrictType#FAIL} record error alerts, and
		 * {@link StrictType#FAIL} also stops initialization when properties are supplied.
		 * @param strict mode, default {@link StrictType#SHORT}.
		 * @return this.
		 */
		public Builder strict(StrictType strict) {
			this.strict = Objects.requireNonNull(strict);
			return this;
		}

		private Function<String, @Nullable String> envLookup = System::getenv;

		private Path levelFilePath = Path.of(LevelFileProperties.FILE_NAME);

		private Duration levelFilePollInterval = LevelFileProperties.POLL_INTERVAL;

		private Builder() {
		}

		/**
		 * Sets the environment variable prefix used in place of the key's
		 * {@code "logging."} lead segment (for example {@code logging.level.com.myapp}
		 * becomes {@code RAINBOWGUM_level_com_myapp} with the default prefix). Casing is
		 * not changed: only the prefix and the <code>.</code> to <code>_</code>
		 * replacement are applied, so a key with mixed-case segments produces a
		 * mixed-case environment variable name. The base resource's
		 * {@value SimpleProperties#ENV_PREFIX_PROPERTY} overrides this setting, and the
		 * prefix has the same rules.
		 * @param envPrefix prefix, default {@value #DEFAULT_ENV_PREFIX}.
		 * @return this.
		 * @throws IllegalArgumentException if the prefix breaks the rules of
		 * {@value SimpleProperties#ENV_PREFIX_PROPERTY}.
		 */
		public Builder envPrefix(String envPrefix) {
			this.envPrefix = parseEnvPrefix(Objects.requireNonNull(envPrefix));
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

		/*
		 * Test-only hook so the default level file need not be in the working directory
		 * and tests need not wait seconds for a poll.
		 */
		Builder levelFilePath(Path levelFilePath, Duration pollInterval) {
			this.levelFilePath = Objects.requireNonNull(levelFilePath);
			this.levelFilePollInterval = Objects.requireNonNull(pollInterval);
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
						strict, null);
			}
			var strictType = classpathProperties.forKey(STRICT_PROPERTY)
				.ofString()
				.map(StrictType::parse)
				.or(strict)
				.validateNow(SimpleProperties.class);
			List<String> errorMessages = List.of();
			if (classpathProperties instanceof SimpleLogProperties simple) {
				errorMessages = switch (strictType) {
					case OFF -> List.of();
					case ALERT, FAIL -> simple.prefixErrors();
					case SHORT -> simple.julErrors();
				};
			}
			boolean shortKeys = strictType == StrictType.SHORT;
			if (shortKeys) {
				classpathProperties = withShortKeys(classpathProperties);
			}
			var envPrefix = classpathProperties.forKey(ENV_PREFIX_PROPERTY)
				.ofString()
				.map(SimpleProperties::parseEnvPrefix)
				.or(this.envPrefix)
				.validateNow(SimpleProperties.class);
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
					if (shortKeys) {
						profileProperties = withShortKeys(profileProperties);
					}
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
			var operatorProperties = LogProperties
				.of(List.of(systemProperties, environmentVariables, classpathProperties));
			var levelFileType = operatorProperties.forKey(LEVEL_FILE_PROPERTY)
				.ofString()
				.map(LevelFileType::parse)
				.or(LevelFileType.OFF)
				.validateNow(SimpleProperties.class);
			LevelFileProperties levelFile = null;
			if (levelFileType == LevelFileType.WATCH) {
				var path = operatorProperties.forKey(LEVEL_FILE_PATH_PROPERTY)
					.ofString()
					.map(Path::of)
					.or(levelFilePath)
					.validateNow(SimpleProperties.class);
				levelFile = new LevelFileProperties(path, levelFilePollInterval);
				properties.add(0, levelFile);
				// Lowest precedence so explicit change settings still win.
				properties.add(LogProperties.builder()
					.fromProperties(LogProperties.CHANGE_MODE_PROPERTY + "=on\n" + LogProperties.CHANGE_LOGGER_PREFIX
							+ "=level")
					.description("LEVEL_FILE_DEFAULT")
					.order(0)
					.build());
				infoMessages.add("Watching level file: " + levelFile.path());
			}
			return new SimpleProperties(List.copyOf(properties), List.copyOf(infoMessages), errorMessages, strictType,
					levelFile);
		}

		private static LogProperties withShortKeys(LogProperties properties) {
			return properties instanceof SimpleLogProperties simple ? simple.withShortKeys() : properties;
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
