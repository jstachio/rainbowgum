# Quarkus + Rainbow Gum example

Generated from [code.quarkus.io](https://code.quarkus.io) (`quarkus-arc` + `quarkus-rest`,
Quarkus 3.39.5), per `doc/other-web-frameworks.md`'s Quarkus section - "the hardest of
the three." `GreetingResource` (`GET /hello`) logs through `org.jboss.logging.Logger`
(Quarkus's own facade) with an INFO, a DEBUG (should be filtered), an exception, and
`org.jboss.logging.MDC`, to have real application-level logging to observe.

Not part of the normal reactor - build/run directly:

```
../../mvnw -q -f pom.xml clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
curl http://localhost:8080/hello
```

Needs Rainbow Gum installed in the local `.m2` first (`../../mvnw install` from the
repo root).

## What was actually tried

This is **Path A** from `doc/other-web-frameworks.md` - additive, not touching
Quarkus's bundled `slf4j-jboss-logmanager` or attempting a full backend swap (Path B,
explicitly out of scope, still unattempted). Two things added to the generated
baseline:

- `rainbowgum-jul` + `rainbowgum-core` (+ `rainbowgum-pattern`, for the pattern-encoder
  verification below) as dependencies - nothing Quarkus-specific, no build step, no
  extension.
- One CDI bean, `RainbowGumStartup`, observing `io.quarkus.runtime.StartupEvent` to
  call `RainbowGum.of()` - see below for why this is necessary.

## Findings

- **The doc's specific claimed mechanism doesn't check out.** It described Path A as
  configured via `quarkus.log.handler.rainbowgum.enabled=true` /
  `quarkus.log.handlers=rainbowgum` plus a handler bean. Checked Quarkus's own
  `logging.adoc` source directly: `quarkus.log.handlers` only attaches *named handlers
  of the built-in console/file/syslog types* (parameterized instances of Quarkus's own
  handler classes) - there's no documented config-driven way to register an arbitrary
  custom `Handler` class that way. What actually worked was much simpler: just having
  `rainbowgum-jul` on the classpath and bootstrapping Rainbow Gum - no
  `quarkus.log.*` configuration at all was needed or used.
- **Same gap as `examples/helidon`: `rainbowgum-jul` does nothing until something
  bootstraps Rainbow Gum.** Without the `RainbowGumStartup` CDI bean's
  `RainbowGum.of()` call, adding the dependency alone changes nothing. This is now
  confirmed across two independent frameworks, not a Helidon-specific quirk - see that
  example's README for the original finding.
- **The result was not what "additive" suggested it would be.** Expected Rainbow Gum's
  output to show up *alongside* Quarkus's own colored/bracketed console format
  (`[io.quarkus] (main) ...`). Instead, once `rainbowgum-jul` is bootstrapped, *all*
  output - Quarkus's own framework logging (`io.quarkus` category), the application's
  `org.jboss.logging.Logger` calls, correct level filtering (DEBUG suppressed),
  correct exception rendering - comes through in Rainbow Gum's own format only;
  Quarkus's usual startup banner and bracketed format never appear. Did not dig into
  *why* (likely something about how `rainbowgum-jul` sets the root JUL logger's own
  level interacting with however Quarkus's own console handler is attached under JBoss
  Log Manager) - this is an observed, reproducible result, not a fully explained one.
  Whatever the mechanism, the practical outcome is clean: one single, correctly
  formatted, correctly filtered output stream, with no SLF4J "multiple providers"
  conflict (`slf4j-jboss-logmanager` was never touched).
- **A real, confirmed gap, but a pre-existing one, not specific to Quarkus**: MDC does
  not survive the trip. `org.jboss.logging.MDC.put("requestId", "abc-123")` in
  `GreetingResource`, checked with a custom pattern encoder
  (`-Dlogging.appender.console.encoder=pattern
  -Dlogging.encoder.console.pattern="...%X%n"`), renders as an empty `%X` - confirmed
  the key value never arrives. `JULBridge.publish` (shared by `rainbowgum-jul` and
  `rainbowgum-jul-logmanager`, `rainbowgum-jul`'s own source) has a
  `// TODO fix key values aka MDC` comment - this example is independent, concrete
  confirmation that TODO is a real, currently-live gap, not just a note to self.
- **Not attempted**: Path B (excluding `slf4j-jboss-logmanager`, putting
  `rainbowgum-slf4j` in its place). Given how cleanly Path A came together here, there
  wasn't a strong motivation to also chase the higher-risk path in this pass.
