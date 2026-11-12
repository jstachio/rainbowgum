# Migrating from Logback to Rainbow Gum _VERSION_

Instructions for an agent moving a project from Logback to Rainbow Gum, which requires
JDK 21 or later. Links are
relative to this file. Section links such as [Spring Boot](overview.md#spring_boot) go to
the Markdown user guide, which has the details; read a section there before changing
anything it covers.

Rainbow Gum has no XML configuration. Logback's `logback.xml` becomes properties: Spring
Boot's `application.properties`, a `logging.properties` file, or system properties. Code
that only uses the SLF4J API does not change.

## 1. Survey the project

Before editing, find and list:

- Dependencies: `ch.qos.logback:logback-classic`, `ch.qos.logback:logback-core`,
  `net.logstash.logback:logstash-logback-encoder`, `co.elastic.logging:logback-ecs-encoder`,
  `org.codehaus.janino:janino` (Logback conditionals), and
  `org.springframework.boot:spring-boot-starter-logging` (brought in by many Spring Boot
  starters). Check transitive dependencies with `./mvnw dependency:tree` or
  `./gradlew dependencies`, using the project's wrapper.
- Bridges: `org.slf4j:jul-to-slf4j` (and calls to `SLF4JBridgeHandler.install()`),
  `org.slf4j:jcl-over-slf4j`, `org.apache.logging.log4j:log4j-to-slf4j`,
  `org.slf4j:slf4j-jdk-platform-logging`.
- Configuration: `logback.xml`, `logback-test.xml`, `logback-spring.xml`,
  `logback.groovy`, the `logback.configurationFile` system property, and Spring Boot's
  `logging.config` property.
- Code importing `ch.qos.logback`: custom appenders, encoders, layouts or filters, casts
  of SLF4J loggers to `ch.qos.logback.classic.Logger` (usually to set a level in tests),
  and `ListAppender` used to assert on log output.
- Uses of SLF4J `Marker`. Rainbow Gum has
  [no built-in Marker support](overview.md#limitations).

Report anything in the survey that has no equivalent (see
[What does not carry over](#what-does-not-carry-over)) to the user before migrating.

## 2. Change dependencies

Remove Logback and its add-ons, then add Rainbow Gum for the kind of project:

- **Spring Boot**: add `io.jstach.rainbowgum:rainbowgum-spring-boot4-starter` (or
  `rainbowgum-spring-boot3-starter` for Spring Boot 3) and exclude
  `spring-boot-starter-logging` from every Spring Boot starter that brings it in. See
  [Spring Boot](overview.md#spring_boot).
- **Micronaut** or **Helidon**: see [Micronaut](overview.md#micronaut) and
  [Helidon](overview.md#helidon).
- **Any other application**: add `io.jstach.rainbowgum:rainbowgum` at runtime scope, see
  [Installation](overview.md#installation). Add
  `io.jstach.rainbowgum:rainbowgum-simple-props` to keep configuration in a
  `logging.properties` file, see [rainbowgum-simple-props](overview.md#simple_props).
- **Library or test only** (a `logback-test.xml`, or Logback at test scope): add
  `io.jstach.rainbowgum:rainbowgum-simple` at test scope, see
  [rainbowgum-simple](overview.md#simple_bundle).

Add modules for what the configuration uses, as listed in the
[URI scheme table](overview.md#installation):

- `rainbowgum-pattern` for Logback `<pattern>` layouts.
- `rainbowgum-file` for file and rolling file appenders.
- `rainbowgum-json` for ECS, GELF, Logstash, or Logback JSON encoders. The core `json`
  encoder needs no additional module; see [core JSON](overview.md#core_json).

Bridges:

- Remove `jul-to-slf4j` and `SLF4JBridgeHandler.install()`. The `rainbowgum` dependency
  already routes `java.util.logging` and `System.Logger`, see
  [System.Logger and java.util.logging](overview.md#system_logger).
- Remove `slf4j-jdk-platform-logging`, see
  [the warning about it](overview.md#slf4j_platform_logging).
- Replace `log4j-to-slf4j` with `io.jstach.rainbowgum:rainbowgum-log4j2`, see
  [Log4j2](overview.md#log4j2).
- Remove `jcl-over-slf4j` and make sure `commons-logging` is 1.3.0 or later, see
  [Commons Logging](overview.md#commons_logging).
- Add `io.jstach.rainbowgum:rainbowgum-jboss-logging` if a dependency logs through JBoss
  Logging, see [JBoss Logging](overview.md#jboss_logging).

## 3. Translate the configuration

Write the properties in the place the project already configures itself: Spring Boot's
`application.properties` (or YAML), `logging.properties` with
[rainbowgum-simple-props](overview.md#simple_props), or system properties. With Spring
Boot, Spring's own `logging.*` properties keep working and Rainbow Gum's keys below can
sit next to them. The supported Spring Boot properties are listed in the
[rainbowgum-spring-boot4](io.jstach.rainbowgum.spring.boot4/module-summary.html) module
documentation.

| Logback | Rainbow Gum |
|---|---|
| `<root level="INFO">` | `logging.level=INFO` |
| `<logger name="com.example" level="DEBUG"/>` | `logging.level.com.example=DEBUG` |
| `ConsoleAppender` | the default; `logging.appender.console.output.uri=stdout` to be explicit |
| `<encoder><pattern>...</pattern>` | `logging.appender.console.encoder.uri=pattern` and `logging.appender.console.encoder.pattern.pattern=...`, see [Pattern](overview.md#pattern_encoder_config) |
| several `<appender-ref>` on root | `logging.appenders=console,file` |
| `FileAppender` | `logging.appender.file.output.uri=file:///var/log/app.log`, or `logging.file.name=app.log`, see [File Output](overview.md#file) |
| `RollingFileAppender` with a size policy | `logging.appender.file.output.uri=rolling:///var/log/app.log?maxFileSize=10MB&maxHistory=7`, see [Built-in Rolling](overview.md#rolling_builtin) |
| `RollingFileAppender` with a time policy | no equivalent, see [Rolling Files](overview.md#rolling) |
| `AsyncAppender` | put the wrapped appender on its own route with `logging.route.{name}.publisher.uri=async`; other routes stay synchronous, see [Publishers](overview.md#publisher_batch_swap_configuration) |
| `additivity="false"` with its own appender | a separate route for that appender with `logging.route.{name}.level=OFF` and the logger's level, and the logger set to `OFF` on every other route, such as `logging.route.console.level.com.example.audit=OFF`, see [Additivity](overview.md#Additivity) and [Route Level Priority](overview.md#route_level_priority) |
| `ThresholdFilter` on an appender | the appender on its own route with that route's level, see [Additivity](overview.md#Additivity) |
| other appender filters | route filters, see [Event based Filtering](overview.md#router_filter) |
| `TurboFilter` | an SLF4J logger decorator, see [SLF4J based filtering](overview.md#slf4j_filter) |
| logstash-logback-encoder `LogstashEncoder` | `logging.appender.console.encoder.uri=logstash`, see [Logstash](overview.md#logstash) |
| `co.elastic.logging.logback.EcsEncoder` | `logging.appender.console.encoder.uri=ecs`, see [ECS](overview.md#ecs) |
| Logback's `JsonEncoder` | `logging.appender.console.encoder.uri=logback`, see [Logback JSON](overview.md#logback_json) |
| `<springProfile>` | Spring profile files such as `application-prod.properties` |
| `<if>` conditions or per environment files | profiles, see [Profiles](overview.md#simple_props_profiles) |
| `scan="true"` | a watched level file, see [Level file](overview.md#simple_props_level_file), or [runtime level changes](overview.md#changing_resolving) |
| `debug="true"` or a status listener | run with `-Dlogging.debug=help`, see [Troubleshooting](overview.md#troubleshooting) |

Watch for these differences:

- Output paths in a URI need `./` for the working directory: `./logs/app.log` or
  `file:///./logs/app.log`, and `rolling:///./logs/app.log` for rolling, not `app.log`,
  see [Outputs](overview.md#outputs). `logging.file.name` does not. Missing directories
  such as `logs` are created.
- Every appender name used in `logging.appender.{name}...` must be listed in
  `logging.appenders` (or the route's `appenders`), see
  [Plural keys](overview.md#config_plural).
- Logback's `AsyncAppender` drops TRACE, DEBUG and INFO events when its queue is nearly
  full. Rainbow Gum's async publisher blocks instead by default. To keep Logback's
  behavior set `discardThreshold` to about a fifth of `bufferSize`, see
  [Overload handling](overview.md#publisher_batch_swap_overload).
- Patterns support most Logback keywords. Compare the pattern with
  [`PatternRegistry.KeywordKey`](io.jstach.rainbowgum.pattern/io/jstach/rainbowgum/pattern/format/PatternRegistry.KeywordKey.html)
  and
  [`PatternRegistry.ColorKey`](io.jstach.rainbowgum.pattern/io/jstach/rainbowgum/pattern/format/PatternRegistry.ColorKey.html),
  and tell the user about any keyword that is not listed. A pattern without an exception
  keyword still prints stack traces, as in Logback, see
  [Automatic Exception Rendering](overview.md#pattern_exception).
- Without `rainbowgum-pattern` the console uses the [TTLL format](overview.md#ttll),
  which prints MDC key values. Patterns print MDC only where they use `%X`. Check that
  nothing sensitive is in MDC, see [the warning about key values](overview.md#key_values_sensitive).
- Logger names that are difficult to express in the chosen property source, especially
  environment variables (for example names with `$` or `-`), can go in a
  [level group](overview.md#level_groups_difficult_names).

The [full example configuration](overview.md#example_configuration_properties) shows many
of these together.

## 4. Change code

- Delete imports of `ch.qos.logback`. Logging calls through SLF4J stay as they are.
- Levels set in code by casting to Logback's `Logger` have no direct equivalent. Prefer
  setting `logging.level.*` system properties before logging starts. For levels that
  must change while running, see [Changing Levels at Runtime](overview.md#changing_resolving).
- Tests that capture output with Logback's `ListAppender` can build a Rainbow Gum with
  [`ListLogOutput`](io.jstach.rainbowgum/io/jstach/rainbowgum/output/ListLogOutput.html)
  as the output, see [Using RainbowGum Builders](overview.md#simple_example_builder).
- Custom Logback appenders, encoders or layouts become a
  [`LogOutput`](io.jstach.rainbowgum/io/jstach/rainbowgum/LogOutput.html) or
  [`LogEncoder`](io.jstach.rainbowgum/io/jstach/rainbowgum/LogEncoder.html), see the
  [Extension development guide](overview.md#plugin_guide). Ask the user before porting
  one.

## 5. Verify

1. Check the dependency tree again: no `logback-classic`, `logback-core` or
   `spring-boot-starter-logging`, and exactly one SLF4J provider. SLF4J prints a warning
   at startup when it finds more than one.
2. Delete the Logback configuration files only after the properties replace them.
3. Run the application or tests once with `-Dlogging.debug=help`. It prints what Rainbow
   Gum wired up, reports misspelled or unused keys, and fails startup on any warning or error, see
   [Troubleshooting](overview.md#troubleshooting). Fix every warning it reports.
4. Compare a few log lines before and after the migration with the user, since formats,
   time zones and key values can differ.

## What does not carry over

Tell the user about any of these the project uses:

- Time based rolling and scheduled compression. Use logrotate or the systemd journal, see
  [External Tool Support](overview.md#rolling_external).
- SLF4J Markers in filters or patterns.
- Logback appenders without an equivalent, such as SMTP, database, socket and syslog
  appenders, and Logback Access.
- XML features such as variable substitution, `<include>` and Janino conditions, beyond
  what the project's property source offers.
