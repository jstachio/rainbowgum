# Micronaut + Rainbow Gum example

Generated from [Micronaut Launch](https://launch.micronaut.io) (Maven, Java, JUnit 5,
`micronaut-http-server-netty` + `micronaut-serde-jackson`), then swapped from the
default `logback-classic` to Rainbow Gum, per `doc/other-web-frameworks.md`'s Micronaut
section. `HelloController` (`GET /hello`) exercises `LoggerFactory.getLogger(...)`,
MDC, and an exception log, so there's real application-level logging to observe, not
just framework startup noise.

This is **not** part of the normal reactor build (see `pom.xml`'s standalone `<parent>`
on `micronaut-parent`, not `rainbowgum-maven-parent`) - build/run it directly:

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
`io.jstach.rainbowgum:rainbowgum`. Deleted `src/main/resources/logback.xml` (confirmed
first that leaving it in place is genuinely harmless - no warning, no double logging,
matches the doc's claim).

## Findings

- **The swap is exactly as low-effort as the doc says.** Build, run, hit the endpoint:
  Rainbow Gum's default TTLL console output, correct level filtering (DEBUG suppressed,
  INFO/ERROR shown), correct exception stack trace rendering. No SLF4J
  "multiple providers" warning, no missing bridge, no code change needed anywhere
  besides the one dependency swap.
- **MDC verified working end-to-end**, not just assumed: with a custom pattern
  (`-Dlogging.appender.console.encoder=pattern
  -Dlogging.encoder.console.pattern="...%X%n"`), `requestId=abc-123` set via
  `MDC.put(...)` in the controller shows up correctly on both the INFO and ERROR lines
  for that request.
- **The `/loggers` management endpoint gap is real, confirmed with the actual error**,
  not just theoretical. Added `micronaut-management` +
  `endpoints.loggers.enabled=true` and hit `GET /loggers`: 500, with
  `NoSuchBeanException: No bean of type [ManagedLoggingSystem] exists` because both
  bundled candidates (`LogbackLoggingSystem`, `Log4jLoggingSystem`) disable themselves
  when their backing classes aren't on the classpath - which, once Logback is gone, is
  exactly the case. There is no generic SLF4J-based `ManagedLoggingSystem` to fall back
  to. A `RainbowGumLoggingSystem` bean (delegating to `LogConfig`'s level-resolution/
  `ChangePublisher` API) would close this, but doesn't exist today. Left the endpoint
  enabled in this example specifically so the 500 is reproducible.
- **No gaps found in actual log output correctness** - this is a genuine "it just
  works" case, unlike Helidon/Quarkus (see the rest of `doc/other-web-frameworks.md`).
