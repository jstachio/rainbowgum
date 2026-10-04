# Rainbow Gum 0.13.0

This release is effectively **1.0.0-RC2**. The headline is **more framework support
and diagnostic reporting**: native integrations for Helidon SE 4 and Micronaut 5,
a native Log4j2 API implementation, and a new `LogReporter` that shows how the
logging system is actually wired. Spring Boot 4 can now expose that report through
Actuator, alongside the existing metrics integration. There is also a configuration
fix for system-property overrides that could silently disappear when a properties
provider was present.

## New module: `rainbowgum-helidon4`

Native Helidon SE 4.x support through Helidon's own `LoggingProvider` extension
point. Add the dependency and Helidon selects and initializes Rainbow Gum during
its logging bootstrap, before configuration, the service registry, or the webserver
start. The module brings in `rainbowgum-jul` for Helidon's JUL logging.

Pair it with `rainbowgum-simple-props` and `rainbowgum-pattern` for a classpath
`logging.properties` file using Rainbow Gum's property format and a pattern encoder.
A runnable application is included in `examples/helidon`.

## New module: `rainbowgum-micronaut5`

Native Micronaut 5.x integration through a `ManagedLoggingSystem` bean. SLF4J already
routes Micronaut's log events to Rainbow Gum; this module adds the framework's own
configuration and management support:

- `logger.levels.*` settings in `application.properties` or `application.yml` now
  control Rainbow Gum's logging levels.
- Micronaut's `/loggers` management endpoint can inspect and change logging levels.
- Runtime level changes are enabled during bootstrap so loggers created early can
  still respond to later changes.

Add the dependency and the bean is discovered automatically. A runnable application
using `rainbowgum-simple` and `rainbowgum-pattern` is included in `examples/micronaut`.

## New module: `rainbowgum-log4j2`

A native implementation of the Log4j2 API, discovered automatically through
`ServiceLoader`. Applications and libraries using Log4j2 can log directly to Rainbow
Gum without routing through SLF4J.

Loggers with fixed levels cache their resolved level for cheap disabled-level checks;
loggers configured for runtime level changes resolve their level dynamically. Log4j2
markers are accepted but ignored: they do not affect filtering or routing.

## New: `LogReporter`

A plain-text diagnostic report of a bound Rainbow Gum instance, showing the concrete
publishers, appenders, encoders, and outputs selected for each route, along with
active global configuration. Optional sections bring metrics, recent alerts,
registered logger names and their resolved levels, and the logging facades in active
use into the same report.

```java
var reporter = LogReporter.builder().build();
System.out.println(reporter.report(RainbowGum.of()));
```

The default report includes `VERSION` and `COMPONENTS`. To include every section:

```java
var reporter = LogReporter.builder()
    .sections(java.util.EnumSet.allOf(LogReporter.Section.class))
    .build();
```

Custom components can implement `LogReporter.Reportable` to contribute their own
description. The report is intended for people and agents diagnosing configuration
or incidents; its text format has no compatibility guarantee, including between
patch releases.

### Spring Boot 4 Actuator info

The existing `rainbowgum-spring-boot4-actuator` module now includes an
`InfoContributor` for `/actuator/info`. Enable it explicitly:

```properties
management.info.rainbowgum.enabled=true
```

It contributes one text entry per report section (`version`, `components`, `metrics`,
`alerts`, `loggers`, and `facades`), all nested under a single `rainbowgum` key.
The contributor is disabled by default because the report can expose internal
configuration details such as component names and URIs. It operates independently
of the existing Micrometer metrics bridge. This integration remains Boot 4 only.

## Fixed: system-property overrides disappearing

When a `PropertiesProvider` contributed a configuration layer, system properties
could disappear from the composite entirely. That meant a setting such as
`-Dlogging.appender.console.encoder=...` could be silently ignored in applications
using a provider, including Avaje Config or the new Micronaut integration.

System properties are now included as an actual layer instead of only being used
as a fallback when no other properties were supplied.

## Under the hood

- Logger registration now tracks names and their originating logging APIs, powering
  the report's `LOGGERS` and `FACADES` sections and a new `LOGGER_NAMES_METRIC`.
- Key `LogProperties` implementations now contribute descriptions to diagnostic
  reports.
- Added CI workflows for the Helidon and Micronaut examples.
- Build JDK upgraded to 27; compile targets and GraalVM CI versions are unchanged by
  that upgrade.

## Docs

Expanded the framework integration and reporting documentation, clarified JUL
bootstrap requirements, and fixed the mobile table-of-contents menu. Added technical
writeups covering Log4j2 flush attribution, Logback JSON encoder behavior, and a
logstash-logback-encoder memory leak.

## Full changelog

[v0.12.0...v0.13.0](https://github.com/jstachio/rainbowgum/compare/v0.12.0...v0.13.0)
