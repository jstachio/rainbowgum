# Helidon SE + Rainbow Gum example

Hand-built minimal Helidon SE 4.5.5 app (the Maven archetype for Helidon 4.x/27.x no
longer works with the standard `maven-archetype-plugin` goal - it's moved to its own
build-tool plugin - so this was written directly instead of generated). `Main`'s
`/hello` route exercises `java.util.logging.Logger` (Helidon's own facade, per
`doc/other-web-frameworks.md`'s Helidon section) with an INFO, a FINE (should be
filtered), and a SEVERE-with-exception call.

Not part of the normal reactor - build/run directly:

```
../../mvnw -q -f pom.xml clean package
java -jar target/rainbowgum-helidon-example.jar
curl http://localhost:8080/hello
```

Needs Rainbow Gum installed in the local `.m2` first (`../../mvnw install` from the
repo root).

## What's in here vs. what the doc describes

This example takes the doc's "good enough for most apps" path: Rainbow Gum's existing
Handler-based `rainbowgum-jul` bridge, not a custom `LogManager` (which the doc
documents as not existing yet and being nontrivial new work - out of scope for this
gap-finding pass).

## Findings

- **`rainbowgum-jul` alone does nothing by itself - but `rainbowgum-jdk` fixes that for
  free.** Just adding `rainbowgum-jul` and calling `LogConfig.configureRuntime()`
  changes nothing - JUL output stays completely default (verified: same plain
  `Sep 24, 2026 ... INFO: ...` format with or without `rainbowgum-jul` on the
  classpath). `JULConfigurator` (the `Configurator` that actually installs the bridge
  `Handler` on JUL's root logger) only runs when something bootstraps Rainbow Gum
  itself - and in a Helidon SE app that never touches SLF4J directly, *nothing* does
  that automatically by default. The fix isn't an explicit `RainbowGum.of()` call
  though: swapping the dependency from `rainbowgum-jul` alone to `rainbowgum-jdk`
  (which pulls `rainbowgum-jul` in transitively at runtime scope, plus
  `rainbowgum-systemlogger`) is enough on its own - **no code change needed at
  all**. `rainbowgum-jdk` registers a `java.lang.System.LoggerFinder`
  (`SystemLoggingFactory`) whose default `InitOption.CHECK` behavior eagerly calls
  `RainbowGum.of()` the first time *anything* calls `System.getLogger(...)` - and the
  JDK's own internals do that incidentally, from unrelated static init (`java.time`/
  `java.util.Locale` formatting, per `rainbowgum-systemlogger`'s own javadoc), early
  enough in this app's startup to activate the JUL bridge before Helidon logs its
  first message. Confirmed by removing the explicit `RainbowGum.of()` call entirely
  from `Main.java` and diffing output byte-for-byte against the version that still had
  it - identical. **This isn't mentioned anywhere in `doc/other-web-frameworks.md`**,
  which only discusses `rainbowgum-jul` and would leave a reader with the false
  impression that an explicit bootstrap call is required.
- **Once bootstrapped, it works exactly as the doc describes**: both Helidon's own
  internal JUL logging (`io.helidon.webserver.ServerListener`,
  `io.helidon.common.features.HelidonFeatures`, etc.) and the application's own
  `java.util.logging.Logger` calls are captured, correctly level-filtered (the `FINE`
  call is suppressed, confirmed absent from output), with exceptions rendered
  correctly.
- **A Helidon-specific nuance, not a Rainbow Gum gap**: a plain JUL
  `logging.properties` on the classpath is *not* picked up by
  `LogConfig.configureRuntime()` unless at least one Helidon `LoggingProvider` module
  (e.g. `helidon-logging-jul`) is also present - without one, Helidon prints "There is
  no Helidon logging implementation on classpath, skipping log configuration." and
  does nothing with the file. Irrelevant once Rainbow Gum owns the output (as here),
  but worth knowing if debugging "why isn't my logging.properties doing anything."
- **Not attempted**: the full `java.util.logging.LogManager` replacement path the doc
  describes as the way to get first-class fidelity (no missed early-boot messages,
  automatic per-logger level sync) - that LogManager doesn't exist in Rainbow Gum today
  and building one is real, separate work, not something this pass was meant to do.
