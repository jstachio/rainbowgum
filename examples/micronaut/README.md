# Micronaut + Rainbow Gum example

Generated from [Micronaut Launch](https://launch.micronaut.io) (Maven, Java, JUnit 5,
`micronaut-http-server-netty` + `micronaut-serde-jackson`), then swapped from the
default `logback-classic` to Rainbow Gum, per `doc/other-web-frameworks.md`'s Micronaut
section. `HelloController` (`GET /hello`) exercises `LoggerFactory.getLogger(...)`,
MDC, and an exception log, so there's real application-level logging to observe, not
just framework startup noise.

This is **not** part of the normal reactor build (see `pom.xml`'s standalone `<parent>`
on `micronaut-parent`, not `rainbowgum-maven-parent`). Build/run it directly:

```
../../mvnw -q -f pom.xml clean package -DskipTests
java -jar target/rainbowgum-micronaut-example-0.1.jar
curl http://localhost:8080/hello
```

Needs Rainbow Gum installed in the local `.m2` first (`../../mvnw install` from the
repo root) since this pins an exact `1.0.0-SNAPSHOT` version rather than resolving from
Central.

## What changed from the generated baseline

`pom.xml`: replaced the `ch.qos.logback:logback-classic` runtime dependency with
`io.jstach.rainbowgum:rainbowgum-simple` (bundles core, pattern, slf4j, systemlogger,
jdk, jul, plus `rainbowgum-simple-props` for zero-config properties loading),
`rainbowgum-pattern` explicitly (it already rides along transitively via
`rainbowgum-simple`, but is what makes the pattern encoder below actually resolve),
and `io.jstach.rainbowgum.micronaut:rainbowgum-micronaut5` (a real
`ManagedLoggingSystem` bean, see Findings below). Deleted
`src/main/resources/logback.xml` (confirmed first that leaving it in place is
genuinely harmless: no warning, no double logging, matches the doc's claim). Added
`src/main/resources/logging.properties`: Rainbow Gum's own property format
(`logging.level`, `logging.appender.*`, `logging.encoder.*`), not
`java.util.logging`'s, setting the console appender's encoder to `pattern` with a
TTLL-like format that tags every line with `[RAINBOW_GUM]` and includes `%X` (MDC), so
it is visually obvious in the output which system produced a given line and that MDC
round-trips correctly. `application.properties` sets
`logger.levels.rainbowgum.micronaut.example.HelloController=DEBUG`, demonstrating that
`rainbowgum-micronaut5` actually wires this up.

## Findings

- **The swap is exactly as low-effort as the doc says, and stayed that way even after
  moving to `rainbowgum-simple` + a real properties file.** Unlike the Helidon example
  (see `examples/helidon`'s own README), Micronaut fully embraces SLF4J as its own
  facade, so there is no `System.LoggerFinder`/`RainbowGumEagerLoad` bootstrap
  ambiguity to work around here at all: Micronaut's own internals call
  `LoggerFactory.getLogger(...)` immediately on startup, which triggers SLF4J's real
  `initialize()` callback (the thing that actually calls `RainbowGum.of()`) with no
  help needed. Build, run, hit the endpoint: Rainbow Gum's pattern-encoded console
  output, `[RAINBOW_GUM]`-tagged, correct exception stack trace rendering. No SLF4J
  "multiple providers" warning, no missing bridge, no code change needed anywhere
  besides the dependency swap and the properties file.
- **MDC verified working end-to-end** via the baked-in `%X` in the pattern:
  `requestId=abc-123` set via `MDC.put(...)` in the controller shows up correctly on
  both the INFO and ERROR lines for that request, with no extra configuration needed
  beyond what's already in `logging.properties`.
- **The `/loggers` management endpoint gap, previously found, is now closed by
  `rainbowgum-micronaut5`.** Originally: added `micronaut-management` +
  `endpoints.loggers.enabled=true` and hit `GET /loggers`: 500, with
  `NoSuchBeanException: No bean of type [ManagedLoggingSystem] exists`, because both
  bundled candidates (`LogbackLoggingSystem`, `Log4jLoggingSystem`) disable themselves
  when their backing classes aren't on the classpath, which, once Logback is gone, is
  exactly the case, and there was no generic SLF4J-based `ManagedLoggingSystem` to fall
  back to. `rainbowgum-micronaut5` provides exactly that bean now: `GET /loggers`
  returns 200, listing every logger this example has touched with its configured and
  effective level.
- **`application.properties`-driven level configuration now works too, and it is the
  same bean that closes both gaps.** `rainbowgum-micronaut5` needs no
  `ServiceLoader` registration or early lifecycle hook of its own: Micronaut's own
  bundled `PropertiesLoggingLevelsConfigurer` (already in `micronaut-context`) reads
  every `logger.levels.*` key from `application.properties` and calls `setLogLevel(...)`
  on whatever `ManagedLoggingSystem`/`LoggingSystem` bean it finds, automatically.
  Confirmed with a real, previously filtered log line: `HelloController`'s `debug(...)`
  call now appears in the output, and `/loggers` reports it at `DEBUG`, both driven by
  one `logger.levels.rainbowgum.micronaut.example.HelloController=DEBUG` line.
  A curiosity noticed along the way, not a bug: Micronaut's startup log shows the same
  level applied twice, once for `rainbowgum.micronaut.example.HelloController` and once
  for `rainbowgum.micronaut.example.hello-controller` (kebab case). Micronaut resolves
  `logger.levels.*` two different ways internally (a raw property map lookup, which
  preserves the key exactly, and a `@ConfigurationProperties`-style nested binding,
  which kebab-cases it), and both happen to fire for this one property.
- **No gaps found in actual log output correctness.** This is a genuine "it just
  works" case, unlike Helidon/Quarkus (see the rest of `doc/other-web-frameworks.md`).
