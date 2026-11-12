# Migrating from Log4j2 to Rainbow Gum _VERSION_

Instructions for a coding agent replacing Log4j Core with Rainbow Gum in an application
or its tests. Rainbow Gum requires JDK 21 or later. Keep the Log4j2 API: ordinary logging
calls can stay. Replace the backend, configuration, and any code that depends on Core.

Links are relative to this file in the published documentation. Read the linked
[user guide](overview.md) sections before changing the features they cover. For
applications using Logback behind `log4j-to-slf4j`, also use the
[Logback migration guide](migrate-logback.md).

## 1. Survey the project

Before editing, identify:

- Dependencies: `org.apache.logging.log4j:log4j-api` (keep), `log4j-core`,
  `log4j-layout-template-json`, other Log4j plugins, and
  `org.springframework.boot:spring-boot-starter-log4j2`. Check transitive dependencies
  with `./mvnw dependency:tree` or `./gradlew dependencies`, using the project's wrapper.
- Providers and bridges: `log4j-slf4j2-impl`, `log4j-slf4j-impl`, `log4j-to-slf4j`,
  `log4j-jul`, `log4j-jpl`, `log4j-jcl`, `log4j-1.2-api`, and any other SLF4J providers.
- Configuration: `log4j2.xml`, `log4j2.properties`, JSON/YAML variants, test and Spring
  variants such as `log4j2-test.xml` and `log4j2-spring.xml`, external configuration
  locations, `log4j2.configurationFile` (or `log4j.configurationFile`), and Spring Boot's
  `logging.config`.
- Startup settings in scripts, containers, and tests: a Log4j JUL `LogManager`, provider
  or context-factory overrides, async context selectors, and Log4j configuration
  monitoring settings.
- Imports of `org.apache.logging.log4j.core`: `Configurator`, casts to Core's
  `Logger` or `LoggerContext`, programmatic configuration, custom appenders/layouts/
  filters, and test appenders. Also find custom `Message` implementations and
  `MapMessage`/`StructuredDataMessage` uses that expect structured fields.
- Markers, custom levels, `FATAL`, the `ThreadContext` stack, routing/filter rules,
  JSON templates, rollover policies, and applications relying on separate logger
  contexts per class loader.

List features with no direct equivalent before changing them. Preserve their behavior
where possible and explain any proposed replacement; do not silently discard a filter,
destination, or structured field. See [Compatibility limits](#compatibility-limits).

## 2. Change dependencies

Keep `org.apache.logging.log4j:log4j-api` at compile scope when application code imports
it. Add `io.jstach.rainbowgum:rainbowgum-log4j2` at runtime scope, using version
`_VERSION_`. This implements the Log4j2 API directly; no SLF4J bridge is needed for
Log4j2 calls. See [Log4j2 integration](overview.md#log4j2).

Choose the rest of the runtime setup for the project:

- **Spring Boot**: replace `spring-boot-starter-log4j2` with
  `io.jstach.rainbowgum:rainbowgum-spring-boot4-starter` (or
  `rainbowgum-spring-boot3-starter` for Boot 3). Keep exclusions of
  `spring-boot-starter-logging` on every starter that brings it in. Add
  `rainbowgum-log4j2` as well; the starter does not include it. See
  [Spring Boot](overview.md#spring_boot).
- **Micronaut or Helidon**: follow [Micronaut](overview.md#micronaut) or
  [Helidon](overview.md#helidon), and add `rainbowgum-log4j2` for Log4j2 callers.
- **Other applications**: add `io.jstach.rainbowgum:rainbowgum` at runtime scope to
  support SLF4J, JUL, and `System.Logger` alongside Log4j2. Add
  `rainbowgum-simple-props` if loading a `logging.properties` file. A project using
  only the Log4j2 API can use `rainbowgum-log4j2` and its transitive core dependency
  without the aggregate bundle. See [Installation](overview.md#installation) and
  [properties files](overview.md#simple_props).
- **Library tests**: keep the public logging API as the library's dependency. Put
  `rainbowgum-log4j2` and `rainbowgum-simple` at test scope to supply the backend and
  load `src/test/resources/logging.properties`. See
  [rainbowgum-simple](overview.md#simple_bundle).

Use the same Rainbow Gum version for all modules. Add `rainbowgum-pattern` for pattern
layouts, `rainbowgum-file` for file/rolling outputs, and `rainbowgum-json` for ECS,
GELF, or Logstash output. Core already includes plain `json`; custom Log4j JSON
templates are not interpreted by any of these encoders.

Remove `log4j-core`, its unused plugins/layout modules, and competing Log4j2 providers
such as `log4j-to-slf4j`. Replace the other bridges according to the APIs in use:

- Remove `log4j-slf4j2-impl` or `log4j-slf4j-impl`; use Rainbow Gum's SLF4J provider
  through the aggregate bundle/starter, or add `rainbowgum-slf4j` explicitly. Rainbow
  Gum implements SLF4J 2; check for callers that need upgrading from SLF4J 1.x.
- Remove `log4j-jul` and any
  `-Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager` setting.
  Use Rainbow Gum's [JUL integration](overview.md#system_logger).
- Remove `log4j-jpl` and `slf4j-jdk-platform-logging`; the aggregate bundle supports
  `System.Logger`. See [System.Logger](overview.md#system_logger).
- Remove `log4j-jcl` and `jcl-over-slf4j`; follow the version-specific
  [Commons Logging guidance](overview.md#commons_logging), including the Spring cases.
- If JBoss Logging is used, add `rainbowgum-jboss-logging` and remove any setting
  forcing JBoss Logging to use its Log4j2 provider. See
  [JBoss Logging](overview.md#jboss_logging).
- If `log4j-1.2-api` is present, identify the legacy callers separately. This guide
  covers the Log4j2 API; do not assume Core compatibility classes will keep working.

## 3. Translate configuration

Rainbow Gum does not read Log4j2 XML, JSON, YAML, or properties configuration files.
Use Spring Boot's `application.properties`/YAML, `logging.properties` with
`rainbowgum-simple-props`, system properties, or builders. A `log4j2.properties`
file needs translation too; renaming it is not enough.

| Log4j2 configuration | Rainbow Gum |
|---|---|
| Root level | `logging.level=INFO` |
| Named logger level | `logging.level.com.example=DEBUG` |
| Console appender | `logging.appender.console.output.uri=stdout` (or `stderr`) |
| Several root appender references | `logging.appenders=console,file` |
| File / RandomAccessFile appender | `logging.appender.file.output.uri=./logs/app.log`; add `rainbowgum-file` |
| Size-based rolling | `logging.appender.file.output.uri=rolling:///./logs/app.log?maxFileSize=10MB&maxHistory=7`; review retention and archive naming in [Rolling Files](overview.md#rolling) |
| Time/cron rolling, composite policies | No direct equivalent; consider [external rotation](overview.md#rolling_external) |
| PatternLayout | `logging.appender.console.encoder.uri=pattern` and `logging.appender.console.encoder.pattern.pattern=...`; translate the pattern as described below |
| JSON layouts/templates | Choose a [JSON encoder](overview.md#json_encoders) or core [plain JSON](overview.md#core_json), then verify field names and types |
| AsyncAppender / AsyncLogger | Set `logging.route.{name}.publisher.uri=async` on the relevant route; review [overload handling](overview.md#publisher_batch_swap_overload) |
| `additivity=false` | Use a separate route and exclude the logger from other routes; see the example below |
| Appender ThresholdFilter | Put the appender on its own route with that route's level |
| Other filters | Review [route filters](overview.md#router_filter); Core filter classes cannot be reused |
| `monitorInterval` | No full configuration reload; use a [watched level file](overview.md#simple_props_level_file) for levels |
| Status logging for configuration diagnosis | Run with `-Dlogging.debug=help`; see [Troubleshooting](overview.md#troubleshooting) |

A console and size-rolling file configuration, with `rainbowgum-file` installed:

```properties
logging.level=INFO
logging.level.com.example=DEBUG
logging.appenders=console,file
logging.appender.console.output.uri=stdout
logging.appender.file.output.uri=rolling:///./logs/app.log?maxFileSize=10MB&maxHistory=7
```

To keep `com.example.audit` and its descendants only in a dedicated file, use routes
instead of logger appender references:

```properties
logging.routes=console,audit
logging.route.console.appenders=console
logging.route.console.level=INFO
logging.route.console.level.com.example.audit=OFF
logging.route.audit.appenders=audit
logging.route.audit.level=OFF
logging.route.audit.level.com.example.audit=INFO
logging.appender.console.output.uri=stdout
logging.appender.audit.output.uri=./logs/audit.log
```

The audit route's default `OFF` excludes other loggers. The console route's audit
override prevents duplicate delivery. Apply the exclusion to every other route that
could accept audit events. See [Additivity](overview.md#Additivity) and
[Route Level Priority](overview.md#route_level_priority).

Check these differences during translation:

- **Paths and appender names.** Use `./logs/app.log` or `file:///./logs/app.log` for a
  working-directory file output, and `rolling:///./logs/app.log` for rolling.
  `logging.file.name` accepts an ordinary path. List each appender in
  `logging.appenders` or a route's `appenders`. See [Outputs](overview.md#outputs)
  and [Plural keys](overview.md#config_plural).
- **Patterns.** Rainbow Gum uses Logback pattern syntax. Do not copy a Log4j2 pattern
  without checking each converter and option against
  [`KeywordKey`](io.jstach.rainbowgum.pattern/io/jstach/rainbowgum/pattern/format/PatternRegistry.KeywordKey.html)
  and [`ColorKey`](io.jstach.rainbowgum.pattern/io/jstach/rainbowgum/pattern/format/PatternRegistry.ColorKey.html).
  For example, a basic target pattern is `[%thread] %-5level %logger - %msg%n`.
  Log4j2's brace-based composite patterns such as `%highlight{...}` need translation
  to the Logback-style `%highlight(...)`. Review logger abbreviation, caller fields,
  time zones, and exception output rather than assuming equal-looking patterns match.
  See [Pattern](overview.md#pattern) and [Automatic Exception Rendering](overview.md#pattern_exception).
- **Context.** The `ThreadContext` map is captured as key values, including for other
  logging APIs. The stack/NDC is not captured, so `%x` has no direct equivalent.
  Patterns print key values where `%X` is used; the default TTLL format includes them.
  Retain context cleanup and propagation at task boundaries. See
  [key values](overview.md#key_values_sensitive).
- **Async behavior.** Async publishing is selected per route, rather than by a Log4j2
  context selector. Remove the old selector settings. Rainbow Gum blocks callers when
  its bounded buffer is full by default; compare this with the application's existing
  full-queue policy before selecting drop/discard settings. See
  [Publishers](overview.md#publisher_batch_swap_configuration).
- **Configuration expressions.** Log4j lookups, substitutions, and scripts are not
  evaluated by Rainbow Gum. Resolve required values through the application's
  configuration source. See [Configuration Properties](overview.md#config_properties).

## 4. Adapt backend-specific code

- Keep ordinary `LogManager.getLogger(...)`, `Logger`, parameterized messages, and
  `ThreadContext.put(...)` calls. Remove Core casts and imports only after replacing
  what they do.
- Replace `Configurator.setLevel(...)` and Core logger mutations with initial
  `logging.level.*` properties or explicitly enabled
  [runtime level changes](overview.md#changing_resolving). Changing a system property
  after a logger is created does not by itself change that logger's level.
- Replace test appenders with
  [`ListLogOutput`](io.jstach.rainbowgum/io/jstach/rainbowgum/output/ListLogOutput.html)
  and [Rainbow Gum builders](overview.md#simple_example_builder). Preserve assertions
  about levels, exceptions, routing, and structured data.
- Custom Core appenders/layouts become a
  [`LogOutput`](io.jstach.rainbowgum/io/jstach/rainbowgum/LogOutput.html) or
  [`LogEncoder`](io.jstach.rainbowgum/io/jstach/rainbowgum/LogEncoder.html).
  Use the [extension guide](overview.md#plugin_guide); agree on the behavior to preserve
  before porting a custom component.

## 5. Verify

1. Check the resolved runtime dependencies again. Keep `log4j-api`; ensure
   `rainbowgum-log4j2` is the only intended Log4j2 provider and there is no Core or
   `log4j-to-slf4j`. If SLF4J is present, keep exactly one SLF4J provider.
2. Remove obsolete Core configuration files, selectors, and provider overrides after
   their replacements are ready. Remove stale `logging.config` paths to Log4j2 files.
3. Run the application or tests with `-Dlogging.debug=help`. Fix reported configuration
   warnings and errors. Then start normally too; inspect startup output for provider
   conflicts or fallback logging.
4. Exercise each logging API the application uses. Verify root and named levels,
   exception traces, context fields and cleanup, and that dedicated-route events reach
   the intended destinations without duplicates.
5. Compare representative output before and after migration, including `FATAL`, custom
   messages, JSON fields/types, and timestamps. Validate rolling, async overload, and
   shutdown delivery if the application uses them. Explain remaining behavior changes.

## Compatibility limits

Review these explicitly when the project uses them:

- **Markers** are accepted but ignored. Marker-based routing/filtering cannot be
  preserved by simply carrying over the API calls.
- **Levels** use Rainbow Gum's standard levels. `FATAL` events become `ERROR`; custom
  numeric levels are mapped to standard levels, losing their separate identity.
- **Messages** are rendered using `Message.getFormattedMessage()`. Map and structured
  messages do not automatically become event key values; move required structured
  fields to the context map or a deliberate extension.
- **ThreadContext stack/NDC** is not included in events; only the map is collected.
- **Core extensions and configuration** do not carry over: JSON templates, plugins,
  lookups, scripts, full configuration reload, time-based rolling, and specialized
  appenders need individual replacements.
- **Logger contexts** share Rainbow Gum's global router. The adapter does not provide
  a separate logging configuration per class loader or honor Core configuration URIs.

For reference, Apache documents the source system's
[API/implementation/bridge dependencies](https://logging.apache.org/log4j/2.x/manual/installation.html),
[pattern syntax](https://logging.apache.org/log4j/2.x/manual/pattern-layout.html), and
[context map and stack](https://logging.apache.org/log4j/2.x/manual/thread-context.html).
