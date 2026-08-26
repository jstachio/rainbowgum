/**
 * Rainbow Gum Spring Boot 4 integration.
 * <p>
 * Both the module name and the {@code io.jstach.rainbowgum.spring.boot4} package are
 * suffixed with the Spring Boot major version because this module's code is duplicated
 * (not shared) with {@code io.jstach.rainbowgum.spring.boot3}: none of it is exported, all
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
 * <a href="https://docs.spring.io/spring-boot/reference/features/logging.html">Spring
 * Boot's own logging documentation</a> for what each property means. This is a snapshot
 * of Boot 4 support specifically; the (separately maintained, not necessarily identical)
 * Boot 3 module is {@code io.jstach.rainbowgum.spring.boot3}.
 * <table class="table">
 * <caption><strong>Supported</strong></caption>
 * <tr>
 * <th>Property</th>
 * <th>Notes</th>
 * </tr>
 * <tr>
 * <td>{@code logging.level.*}, {@code logging.group.*}</td>
 * <td>Native - {@code logging.group.*} works because RainbowGum core's own
 * {@code GroupLevelResolver} already uses this exact property key, not anything
 * Spring-specific.</td>
 * </tr>
 * <tr>
 * <td>{@code debug}, {@code trace}</td>
 * <td>Transitively - Spring's own {@code LoggingApplicationListener} converts these to
 * {@code logging.level.root} before any {@code LoggingSystem} is initialized.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.pattern.console}, {@code logging.pattern.file},
 * {@code logging.pattern.level}, {@code logging.pattern.dateformat},
 * {@code logging.exception-conversion-word}</td>
 * <td>Transitively - Spring Boot bridges these to system properties
 * ({@code CONSOLE_LOG_PATTERN}, etc.) before calling {@code initialize()}; this module's
 * {@code Patterns} class reads those system properties, the same mechanism a
 * hand-written {@code logback.xml} would rely on.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.include-application-name},
 * {@code logging.include-application-group}</td>
 * <td>Native - toggle whether the default pattern includes those segments.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.file.name}</td>
 * <td>Native, but in {@code rainbowgum-core} itself (not this module) - works even
 * without Spring Boot on the classpath at all, kept there for that reason.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.file.path}</td>
 * <td>Native - synthesizes {@code <path>/spring.log} when {@code logging.file.name}
 * itself is unset, matching Spring Boot's own {@code LogFile.get(...)} precedence.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.console.enabled}</td>
 * <td>Native - when {@code false}, restricts the route to just the file appender if one
 * resolves; otherwise left alone rather than pointing at nothing.</td>
 * </tr>
 * <tr>
 * <td>{@code spring.output.ansi.enabled} ({@code NEVER}/{@code ALWAYS}/{@code DETECT})</td>
 * <td>Native - bridged to RainbowGum's own existing
 * {@code logging.global.ansi.disable} property rather than a second ansi mechanism;
 * {@code DETECT} (or unset) leaves RainbowGum's own auto-detection in charge.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.structured.format.console}, {@code logging.structured.format.file}
 * ({@code ecs}/{@code gelf}/{@code logstash} only - not a custom
 * {@code StructuredLogFormatter} class name)</td>
 * <td>Native - bridges to {@code rainbowgum-json}'s {@code EcsEncoder}/
 * {@code GelfEncoder}/{@code LogstashEncoder}. The two properties are independent,
 * matching Spring Boot's own behavior.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.structured.ecs.service.name}, {@code .service.version},
 * {@code .service.environment}, {@code .service.node-name}</td>
 * <td>Native - {@code .name}/{@code .version} default to
 * {@code spring.application.name}/{@code .version} the same way Spring Boot's own ECS
 * formatter does.</td>
 * </tr>
 * <tr>
 * <td>{@code logging.structured.gelf.host}, {@code .service.version}</td>
 * <td>Native - {@code .host} defaults to {@code spring.application.name}. GELF has no
 * dedicated version field, so {@code .service.version} becomes an
 * underscore-prefixed {@code _service_version} additional field, matching Spring Boot's
 * own GELF formatter's naming.</td>
 * </tr>
 * </table>
 * <table class="table">
 * <caption><strong>Not supported</strong></caption>
 * <tr>
 * <th>Property</th>
 * <th>Why</th>
 * </tr>
 * <tr>
 * <td>{@code logging.charset.console}, {@code logging.charset.file}</td>
 * <td>Would need a new core capability (configurable output charset - UTF-8 is
 * currently fixed), not just a property bridge at this layer.</td>
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
 * </table>
 */
module io.jstach.rainbowgum.spring.boot4 {
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
		with io.jstach.rainbowgum.spring.boot4.RainbowGumLoggingSystemFactory;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
		with io.jstach.rainbowgum.spring.boot4.PreBootRainbowGumProvider;

	uses io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;
}
