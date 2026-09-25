# Helidon SE + Rainbow Gum example

Hand-built minimal Helidon SE 4.5.5 app. (The Maven archetype for Helidon 4.x/27.x no
longer works with the standard `maven-archetype-plugin` goal; it has moved to its own
build-tool plugin, so this was written directly instead of generated.) `Main`'s
`/hello` route exercises `java.util.logging.Logger` (Helidon's own facade, per
`doc/other-web-frameworks.md`'s Helidon section) with an INFO, a FINE (should be
filtered), and a SEVERE-with-exception call.

Not part of the normal reactor. Build/run directly:

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
documents as not existing yet and being nontrivial new work, out of scope for this
gap-finding pass).

Dependencies are `rainbowgum-helidon4` (implements Helidon's own `LoggingProvider`
extension point, see Findings below for the full journey to this point),
`rainbowgum-simple-props` (zero-config classpath `logging.properties` loading), and
`rainbowgum-pattern` (resolves the `encoder=pattern` line in that file to a real
encoder). `src/main/resources/logging.properties` is Rainbow Gum's own property
format (`logging.level`, `logging.appender.*`, `logging.encoder.*`), not
`java.util.logging`'s: it sets the console appender's encoder to `pattern` with a
TTLL-like format that tags every line with `[RAINBOW_GUM]`, so it is visually obvious in
the output which system actually produced a given line.

## Findings

- **Final state: `rainbowgum-helidon4` (Helidon's own `LoggingProvider` extension
  point).** Superseding every earlier finding below: `rainbowgum-helidon4` implements
  `io.helidon.logging.common.spi.LoggingProvider`, the same extension point
  `helidon-logging-jul`/`helidon-logging-log4j` implement, registered via
  `ServiceLoader`. Helidon's own `LogConfig` calls `initialization()` on whichever
  provider wins in its own static initializer, and every Helidon-generated `Main`
  forces that to run (`LogConfig.initClass()`) before config, the service registry, or
  the webserver ever starts, well before any application log line. No `@Weight`
  needed either: the default weight beats `JulProvider`'s own low weight automatically,
  confirmed even with `helidon-logging-jul` also present. Net effect: zero explicit
  `RainbowGum.of()` call, zero JVM flags, and (unlike the `rainbowgum-jdk`-based
  approach two findings down) no interference from `rainbowgum-slf4j`'s
  `RainbowGumEagerLoad` marker either, since this provider does not defer to it at all,
  it just bootstraps directly. This is the module the rest of this file's findings
  were building toward.
- **`rainbowgum-jul` alone does nothing by itself, but `rainbowgum-jdk` fixes that for
  free.** Just adding `rainbowgum-jul` and calling `LogConfig.configureRuntime()`
  changes nothing. JUL output stays completely default (verified: same plain
  `Sep 24, 2026 ... INFO: ...` format with or without `rainbowgum-jul` on the
  classpath). `JULConfigurator` (the `Configurator` that actually installs the bridge
  `Handler` on JUL's root logger) only runs when something bootstraps Rainbow Gum
  itself, and in a Helidon SE app that never touches SLF4J directly, *nothing* does
  that automatically by default. The fix isn't an explicit `RainbowGum.of()` call
  though: swapping the dependency from `rainbowgum-jul` alone to `rainbowgum-jdk`
  (which pulls `rainbowgum-jul` in transitively at runtime scope, plus
  `rainbowgum-systemlogger`) is enough on its own. **No code change needed at
  all.** `rainbowgum-jdk` registers a `java.lang.System.LoggerFinder`
  (`SystemLoggingFactory`) whose default `InitOption.CHECK` behavior eagerly calls
  `RainbowGum.of()` the first time *anything* calls `System.getLogger(...)`, and the
  JDK's own internals do that incidentally (from unrelated static init such as
  `java.time`/`java.util.Locale` formatting, per `rainbowgum-systemlogger`'s own
  javadoc), early enough in this app's startup to activate the JUL bridge before
  Helidon logs its first message. **This isn't mentioned anywhere in
  `doc/other-web-frameworks.md`**, which only discusses `rainbowgum-jul` and would
  leave a reader with the false impression that an explicit bootstrap call is
  required.
- **A second, previously undiscovered gap in the same area: swapping to
  `rainbowgum-simple` regresses that exact fix.** `rainbowgum-simple` bundles the full
  `rainbowgum` aggregator, which includes `rainbowgum-slf4j` at compile scope, and
  `rainbowgum-slf4j` registers a `RainbowGumEagerLoad` marker (its whole purpose being
  "I promise I will bootstrap Rainbow Gum, so nothing else needs to").
  `SystemLoggingFactory`'s `InitOption.CHECK` logic checks for *any* `RainbowGumEagerLoad`
  implementation on the classpath and, if one exists, defers to it instead of eagerly
  bootstrapping itself. This app never calls a single SLF4J method (Helidon logs
  through JUL, the app logs through JUL), so SLF4J's own `initialize()` callback (the
  thing that would actually call `RainbowGum.of()`) never fires either. Net effect:
  with plain `rainbowgum-simple`, *nothing* ever bootstraps Rainbow Gum, and the app
  silently falls back to completely default JUL output.
- **Excluding `rainbowgum-slf4j` was tried, and works, but was deliberately rejected.**
  It does restore zero-JVM-flag, zero-code-change activation (`RainbowGumEagerLoad.exists()`
  goes back to false, so `SystemLoggingFactory` falls through to its own eager
  bootstrap). Rejected anyway: realistically, any nontrivial app eventually pulls in a
  library that logs through SLF4J, and excluding `rainbowgum-slf4j` from an example
  meant to demonstrate "just get the damn thing to work" would be actively misleading.
- **Also tried and confirmed not to work: setting
  `logging.systemlogger.initialize=true` inside `logging.properties` itself**, hoping
  to activate eager bootstrap without touching `Main.java` at all, while keeping
  `rainbowgum-slf4j` on the classpath. Output stayed completely unbootstrapped either
  way. Root cause: `SystemLoggingFactory`'s constructor resolves this property via
  `LogProperties.findGlobalProperties()`, which checks `RainbowGum.getOrNull()` (still
  null, nothing has bootstrapped yet at this point) and falls back straight to plain
  system properties, never to `rainbowgum-simple-props`'s classpath `logging.properties`.
  That file only becomes readable *after* a `LogConfig` exists with
  `SimplePropertiesProvider` wired into the `ServiceRegistry`, which is exactly the
  bootstrap step this property is meant to gate. A genuine chicken-and-egg:
  `logging.systemlogger.initialize` can only ever be set as a real system property
  (`-D` JVM argument or equivalent), never from `logging.properties`.
- **What landed at the time: one explicit `RainbowGum.of()` call in `Main.java`**,
  right after `LogConfig.configureRuntime()`, with `rainbowgum-slf4j` left in place.
  The simplest option available at the time that was both reliable and honest about
  what a real app doing this would need to do. Superseded by `rainbowgum-helidon4`
  (the finding at the top of this list) once that module existed: no code change
  needed at all anymore.
- **Once bootstrapped, it works exactly as the doc describes.** Both Helidon's own
  internal JUL logging (`io.helidon.webserver.ServerListener`,
  `io.helidon.common.features.HelidonFeatures`, etc.) and the application's own
  `java.util.logging.Logger` calls are captured, correctly level-filtered (the `FINE`
  call is suppressed, confirmed absent from output), with exceptions rendered
  correctly, and every line tagged `[RAINBOW_GUM]` by the custom pattern.
- **A Helidon-specific nuance, not a Rainbow Gum gap.** A plain JUL
  `logging.properties` on the classpath is *not* picked up by
  `LogConfig.configureRuntime()` unless at least one Helidon `LoggingProvider` module
  (e.g. `helidon-logging-jul`) is also present. Without one, Helidon prints "There is
  no Helidon logging implementation on classpath, skipping log configuration." and
  does nothing with the file. Doubly irrelevant here now: Rainbow Gum owns the output,
  and `logging.properties` in this project means Rainbow Gum's own property format
  (via `rainbowgum-simple-props`), not `java.util.logging`'s. That the two systems
  happen to default to the exact same classpath resource name is itself worth knowing
  about; it is what the original version of this file (a `java.util.logging`
  `SimpleFormatter` config) got confused with before this change.
- **Not attempted.** The full `java.util.logging.LogManager` replacement path the doc
  describes as the way to get first-class fidelity (no missed early-boot messages,
  automatic per-logger level sync): that alternative is explored on the sibling
  `examples/helidon-jul-logmanager` branch instead.
