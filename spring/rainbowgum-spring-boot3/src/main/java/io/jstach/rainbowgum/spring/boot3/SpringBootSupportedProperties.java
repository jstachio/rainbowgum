package io.jstach.rainbowgum.spring.boot3;

/**
 * Single source of truth for the literal Spring Boot logging property keys this module
 * reads (directly, or transitively via Spring Boot's own system-property bridging) -
 * referenced from both the code that reads them and {@code module-info.java}'s own
 * javadoc via {@code {@value}}, so the two can't drift apart.
 * <p>
 * {@link io.jstach.rainbowgum.LogProperties#FILE_PROPERTY} ({@code logging.file.name})
 * and {@link io.jstach.rainbowgum.LogProperties#GROUP_PROPERTY}
 * ({@code logging.group.{name}}) are deliberately not duplicated here - they already have
 * a single canonical home in {@code rainbowgum-core} itself, since both work with or
 * without Spring Boot on the classpath.
 * <p>
 * Unlike {@code io.jstach.rainbowgum.spring.boot4.SpringBootSupportedProperties}, there
 * is no {@code CONSOLE_ENABLED} constant here - {@code logging.console.enabled} is a
 * Spring Boot 4-only property, not documented for Spring Boot 3.
 */
public final class SpringBootSupportedProperties {

	private SpringBootSupportedProperties() {
	}

	/**
	 * Root logger level - Spring Boot binds this rather than {@code logging.level} (see
	 * {@link #LOGGING_LEVEL}).
	 */
	public static final String LOGGING_LEVEL_ROOT = "logging.level.root";

	/**
	 * The property RainbowGum core itself reads for the root level; remapped from
	 * {@link #LOGGING_LEVEL_ROOT} since that's what Spring Boot actually binds.
	 */
	public static final String LOGGING_LEVEL = "logging.level";

	/**
	 * Whether the default pattern includes the {@code APPLICATION_NAME} segment.
	 */
	public static final String INCLUDE_APPLICATION_NAME = "logging.include-application-name";

	/**
	 * Whether the default pattern includes the {@code APPLICATION_GROUP} segment.
	 */
	public static final String INCLUDE_APPLICATION_GROUP = "logging.include-application-group";

	/**
	 * Directory-only file property - consulted only as a fallback when {@code
	 * logging.file.name} itself is unset, synthesizing {@code <path>/spring.log}.
	 */
	public static final String FILE_PATH = "logging.file.path";

	/**
	 * {@code NEVER}/{@code ALWAYS}/{@code DETECT} - bridged to RainbowGum's own {@code
	 * logging.global.ansi.disable} property.
	 */
	public static final String OUTPUT_ANSI_ENABLED = "spring.output.ansi.enabled";

	/**
	 * Charset for console output - bridged to the console pattern encoder's own {@code
	 * charset} builder property.
	 */
	public static final String CHARSET_CONSOLE = "logging.charset.console";

	/**
	 * Charset for file output - bridged to the file pattern encoder's own {@code
	 * charset} builder property.
	 */
	public static final String CHARSET_FILE = "logging.charset.file";

	/**
	 * Supported transitively - Spring Boot bridges this to the {@code
	 * CONSOLE_LOG_PATTERN} system property before any {@code LoggingSystem} is
	 * initialized.
	 */
	public static final String PATTERN_CONSOLE = "logging.pattern.console";

	/**
	 * Supported transitively - Spring Boot bridges this to the {@code FILE_LOG_PATTERN}
	 * system property before any {@code LoggingSystem} is initialized.
	 */
	public static final String PATTERN_FILE = "logging.pattern.file";

	/**
	 * Supported transitively - Spring Boot bridges this to the {@code LOG_LEVEL_PATTERN}
	 * system property before any {@code LoggingSystem} is initialized.
	 */
	public static final String PATTERN_LEVEL = "logging.pattern.level";

	/**
	 * Supported transitively - Spring Boot bridges this to the {@code
	 * LOG_DATEFORMAT_PATTERN} system property before any {@code LoggingSystem} is
	 * initialized.
	 */
	public static final String PATTERN_DATEFORMAT = "logging.pattern.dateformat";

	/**
	 * Supported transitively - Spring Boot bridges this to the {@code
	 * LOG_EXCEPTION_CONVERSION_WORD} system property before any {@code LoggingSystem} is
	 * initialized.
	 */
	public static final String EXCEPTION_CONVERSION_WORD = "logging.exception-conversion-word";

	/**
	 * Structured format for console output: {@code ecs}, {@code gelf}, or {@code
	 * logstash}. Independent of {@link #STRUCTURED_FORMAT_FILE}.
	 */
	public static final String STRUCTURED_FORMAT_CONSOLE = "logging.structured.format.console";

	/**
	 * Structured format for file output: {@code ecs}, {@code gelf}, or {@code
	 * logstash}. Independent of {@link #STRUCTURED_FORMAT_CONSOLE}.
	 */
	public static final String STRUCTURED_FORMAT_FILE = "logging.structured.format.file";

	/**
	 * ECS {@code service.name} field - defaults to {@code spring.application.name}.
	 */
	public static final String STRUCTURED_ECS_SERVICE_NAME = "logging.structured.ecs.service.name";

	/**
	 * ECS {@code service.version} field - defaults to {@code
	 * spring.application.version}.
	 */
	public static final String STRUCTURED_ECS_SERVICE_VERSION = "logging.structured.ecs.service.version";

	/**
	 * ECS {@code service.environment} field.
	 */
	public static final String STRUCTURED_ECS_SERVICE_ENVIRONMENT = "logging.structured.ecs.service.environment";

	/**
	 * ECS {@code service.node-name} field.
	 */
	public static final String STRUCTURED_ECS_SERVICE_NODE_NAME = "logging.structured.ecs.service.node-name";

	/**
	 * GELF {@code host} field - defaults to {@code spring.application.name}.
	 */
	public static final String STRUCTURED_GELF_HOST = "logging.structured.gelf.host";

	/**
	 * GELF service version - becomes the underscore-prefixed {@code _service_version}
	 * additional field, matching Spring Boot's own GELF formatter's naming.
	 */
	public static final String STRUCTURED_GELF_SERVICE_VERSION = "logging.structured.gelf.service.version";

}
