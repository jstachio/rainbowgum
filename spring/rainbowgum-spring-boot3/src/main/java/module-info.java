/**
 * Rainbow Gum Spring Boot 3 integration.
 * <p>
 * Both the module name and the {@code io.jstach.rainbowgum.spring.boot3} package are
 * suffixed with the Spring Boot major version because this module's code is duplicated
 * (not shared) with {@code io.jstach.rainbowgum.spring.boot4}: none of it is exported, all
 * of it is wired up internally through {@code provides}/{@code uses} or Spring Boot's own
 * {@code META-INF/spring.factories}, so no consumer ever imports it directly and there is
 * no "drop-in" benefit to giving the two modules' internals the same package name - unlike
 * two modules ever sharing a module <em>name</em>, which is a hard JPMS conflict the moment
 * both are combined onto one module graph, as this repository's own aggregate javadoc build
 * does. The SPI package ({@code io.jstach.rainbowgum.spring.boot.spi}) lives in its own
 * genuinely shared module, {@code io.jstach.rainbowgum.spring.boot.spi}, since it does not
 * depend on Spring Boot at all (just the stable {@code Environment} type from spring-core)
 * and is meant to be implemented by consumers, so it is neither duplicated nor suffixed.
 * <p>
 * <b>Spring Boot logging property support</b> - see
 * <a href="https://docs.spring.io/spring-boot/3.5/reference/features/logging.html">Spring
 * Boot 3.5's own logging documentation</a> for what each property means. This is a
 * snapshot of Boot 3 support specifically; the (separately maintained, not necessarily
 * identical) Boot 4 module is {@code io.jstach.rainbowgum.spring.boot4}. Every property
 * key below is a {@code {@value}} reference into
 * {@link io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties} (or, for
 * the two properties RainbowGum core itself understands independent of Spring Boot,
 * {@link io.jstach.rainbowgum.LogProperties}) rather than a literal string, so this
 * table can't silently drift from what the code actually reads.
 * <table class="table">
 * <caption><strong>Supported</strong></caption>
 * <tr>
 * <th>Property</th>
 * <th>Notes</th>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#LOGGING_LEVEL_ROOT}, {@code
 * logging.level.<logger>}, {@value io.jstach.rainbowgum.LogProperties#GROUP_PROPERTY}</td>
 * <td>Native - the group property works because RainbowGum core's own {@code
 * GroupLevelResolver} already uses this exact property key, not anything
 * Spring-specific.</td>
 * </tr>
 * <tr>
 * <td>{@code debug}, {@code trace}</td>
 * <td>Transitively - Spring's own {@code LoggingApplicationListener} converts these to
 * {@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#LOGGING_LEVEL_ROOT} before any
 * {@code LoggingSystem} is initialized.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#PATTERN_CONSOLE}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#PATTERN_FILE}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#PATTERN_LEVEL}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#PATTERN_DATEFORMAT}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#EXCEPTION_CONVERSION_WORD}</td>
 * <td>Transitively - Spring Boot bridges these to system properties
 * ({@code CONSOLE_LOG_PATTERN}, etc.) before calling {@code initialize()}; this module's
 * {@code Patterns} class reads those system properties, the same mechanism a
 * hand-written {@code logback.xml} would rely on.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#INCLUDE_APPLICATION_NAME}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#INCLUDE_APPLICATION_GROUP}</td>
 * <td>Native - toggle whether the default pattern includes those segments.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.LogProperties#FILE_PROPERTY}</td>
 * <td>Native, but in {@code rainbowgum-core} itself (not this module) - works even
 * without Spring Boot on the classpath at all, kept there for that reason.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#FILE_PATH}</td>
 * <td>Native - synthesizes {@code <path>/spring.log} when {@code logging.file.name}
 * itself is unset, matching Spring Boot's own {@code LogFile.get(...)} precedence.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#OUTPUT_ANSI_ENABLED} ({@code
 * NEVER}/{@code ALWAYS}/{@code DETECT})</td>
 * <td>Native - bridged to RainbowGum's own existing
 * {@code logging.global.ansi.disable} property rather than a second ansi mechanism;
 * {@code DETECT} (or unset) leaves RainbowGum's own auto-detection in charge.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_FORMAT_CONSOLE}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_FORMAT_FILE} ({@code
 * ecs}/{@code gelf}/{@code logstash} only - not a custom {@code StructuredLogFormatter}
 * class name)</td>
 * <td>Native - bridges to {@code rainbowgum-json}'s {@code EcsEncoder}/
 * {@code GelfEncoder}/{@code LogstashEncoder}. The two properties are independent,
 * matching Spring Boot's own behavior. Structured logging is a Spring Boot 3.4+ feature -
 * this module reads the property itself regardless of the exact 3.x patch version, so it
 * works even on Boot versions where Spring's own auto-configuration predates it.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_ECS_SERVICE_NAME}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_ECS_SERVICE_VERSION}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_ECS_SERVICE_ENVIRONMENT}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_ECS_SERVICE_NODE_NAME}</td>
 * <td>Native - {@code .name}/{@code .version} default to
 * {@code spring.application.name}/{@code .version} the same way Spring Boot's own ECS
 * formatter does.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_GELF_HOST}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#STRUCTURED_GELF_SERVICE_VERSION}</td>
 * <td>Native - {@code .host} defaults to {@code spring.application.name}. GELF has no
 * dedicated version field, so {@code .service.version} becomes an
 * underscore-prefixed {@code _service_version} additional field, matching Spring Boot's
 * own GELF formatter's naming.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#CHARSET_CONSOLE}, {@value
 * io.jstach.rainbowgum.spring.boot3.SpringBootSupportedProperties#CHARSET_FILE}</td>
 * <td>Native - bridged to the pattern encoder's own {@code charset} builder property
 * (a core {@code rainbowgum-pattern} capability, not Spring-specific); unset falls back
 * to UTF-8 the same as when Spring Boot is not on the classpath at all.</td>
 * </tr>
 * </table>
 * <table class="table">
 * <caption><strong>Not supported</strong></caption>
 * <tr>
 * <th>Property</th>
 * <th>Why</th>
 * </tr>
 * <tr>
 * <td>{@code logging.threshold.console}, {@code logging.threshold.file}</td>
 * <td>Would need a new core capability (per-appender level filtering within a
 * multi-appender route - level filtering today is per-route, not per-appender).</td>
 * </tr>
 * <tr>
 * <td>{@code logging.structured.json.include}, {@code .exclude}, {@code .rename.*},
 * {@code .add.*}, {@code .customizer}, {@code .stacktrace.*}</td>
 * <td>Spring Boot's own {@code JsonWriter}/{@code StackTracePrinter} customization
 * layer, specific to its {@code StructuredLogFormatter} machinery - no RainbowGum
 * equivalent to bridge to.</td>
 * </tr>
 * <tr>
 * <td>A fully-qualified {@code StructuredLogFormatter} class name as the
 * {@code logging.structured.format.*} value (Spring Boot's fully-custom-format escape
 * hatch)</td>
 * <td>No RainbowGum equivalent; silently falls back to whatever pattern encoder is
 * already installed for that output type rather than failing.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.config}</td>
 * <td>No RainbowGum equivalent - configuration is property-driven, not a separate
 * config file format like {@code logback.xml}.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.register-shutdown-hook}</td>
 * <td>Unclear mapping to RainbowGum's own shutdown lifecycle - not attempted.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.logback.rollingpolicy.*}, {@code logging.log4j2.rollingpolicy.*}</td>
 * <td>N/A - this module has no Logback or Log4j2 dependency, and RainbowGum does not
 * support file rolling at all (see the roadmap's file-rolling discussion).</td>
 * </tr>
 * <tr>
 * <td>{@code logging.console.enabled}</td>
 * <td>N/A - not a Spring Boot 3 property (Boot 4 only; see
 * {@code io.jstach.rainbowgum.spring.boot4.SpringBootSupportedProperties#CONSOLE_ENABLED}).</td>
 * </tr>
 * </table>
 */
module io.jstach.rainbowgum.spring.boot3 {
	requires transitive io.jstach.rainbowgum;
	requires transitive io.jstach.rainbowgum.spring.boot.spi;
	requires io.jstach.rainbowgum.pattern;
	requires io.jstach.rainbowgum.json;

	/*
	 * Note that we never require transitive of spring stuff
	 * as it is an automatic module.
	 */
	requires spring.boot;
	requires spring.core;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;

	/*
	 * Spring does not need this because it is an automatic module
	 * but in theory some day they will as the boot package
	 * is not exported or open.
	 */
	provides org.springframework.boot.logging.LoggingSystemFactory
		with io.jstach.rainbowgum.spring.boot3.RainbowGumLoggingSystemFactory;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
		with io.jstach.rainbowgum.spring.boot3.PreBootRainbowGumProvider;

	uses io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;
}
