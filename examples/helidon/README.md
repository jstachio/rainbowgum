# Helidon SE + Rainbow Gum example (LogManager variant)

Same hand-built minimal Helidon SE 4.5.5 app as the `examples/helidon` branch this one
forked from, but swaps Rainbow Gum's Handler-based `rainbowgum-jul` bridge for
`rainbowgum-jul-logmanager`, a full `java.util.logging.LogManager` replacement - the
"ideal" path per `rainbowgum-jul-logmanager`'s own module javadoc when an application
inherently relies on `java.util.logging` itself, which Helidon does. `Main`'s `/hello`
route exercises `java.util.logging.Logger` with an INFO, a FINE (should be filtered),
and a SEVERE-with-exception call, same as the other branch.

Not part of the normal reactor - build/run directly. Unlike the Handler-bridge variant,
**this one needs a real JVM `-D` argument**, not just a dependency (see Findings below):

```
../../mvnw -q -f pom.xml clean package
java -Djava.util.logging.manager=io.jstach.rainbowgum.jul.logmanager.RainbowGumLogManager \
     -jar target/rainbowgum-helidon-example.jar
curl http://localhost:8080/hello
```

Needs Rainbow Gum installed in the local `.m2` first (`../../mvnw install` from the
repo root).

## What's in here vs. the `examples/helidon` branch

Same app, `rainbowgum-jul-logmanager` + `rainbowgum-jdk` instead of `rainbowgum-jul` +
`rainbowgum-jdk`. This explores the second of two paths for apps that don't use SLF4J:
predominantly-`System.Logger` apps with maybe some stray JUL calls want
`rainbowgum-jdk` (+ `rainbowgum-jul` if any stray JUL calls exist at all - see the other
branch), while apps that inherently rely on `java.util.logging` itself want
`rainbowgum-jul-logmanager` instead, since it replaces the `LogManager` outright rather
than just attaching a `Handler` to whatever the default one already is.

## Findings

- **`rainbowgum-jdk` is still needed, even with the LogManager replacement.** Tried
  `rainbowgum-jul-logmanager` completely alone first (no `rainbowgum-jdk`, JVM flag
  set correctly) - same failure mode as the plain Handler bridge: every `INFO`/`FINE`
  message vanishes silently, and only the app's own `SEVERE` call surfaces, via Rainbow
  Gum's *internal* failsafe alert path (`[ERROR] - RAINBOW_GUM ...`, not a real,
  correctly-formatted app log line). Root cause is identical to the Handler bridge's:
  `RainbowGumJULLogger.log(LogRecord)` routes straight through
  `io.jstach.rainbowgum.jul.JULBridge`, which dispatches via
  `LogRouter.global()` - the *same* global entry point the Handler bridge uses, and
  nothing in `rainbowgum-jul-logmanager` itself ever calls `RainbowGum.of()`. Adding
  `rainbowgum-jdk` back (its `System.LoggerFinder` eagerly bootstraps Rainbow Gum, see
  the other branch's README) fixes it completely, with **no code change** - confirmed
  byte-for-byte identical output to the Handler-bridge branch, including correct `FINE`
  suppression and exception rendering.
- **The `-D` JVM argument is unavoidable, not just today's implementation choice.**
  `java.util.logging.LogManager.getLogManager()` reads the `java.util.logging.manager`
  system property exactly once, via a static bootstrap that runs the first time
  *anything* in the JVM touches `java.util.logging` - `System.setProperty(...)` from
  application code is always too late once that has already happened, and in a real
  app there is no reliable way to guarantee it hasn't (the JDK's own internals, or any
  dependency, may have already touched JUL before `main()` even starts). This
  is a genuine, structural difference from the Handler-bridge path (which needs only a
  dependency, no JVM flag) worth calling out clearly to anyone choosing between the two
  - not a Rainbow Gum limitation, `log4j-jul`'s own `LogManager` (which
  `rainbowgum-jul-logmanager` is modeled on) has the exact same requirement.
- **No observed behavioral difference between the two paths in this particular app.**
  Both the Handler-bridge branch and this one produce identical final output once
  `rainbowgum-jdk` is present. The LogManager's real theoretical advantage - closing
  the race where a JUL message logged very early (before RainbowGum's `Configurator`
  pass has installed the bridge `Handler`) gets silently dropped - never actually
  triggers here, since this app's own logging (and the Helidon internal logging it
  captures) all happens comfortably after both bootstrap paths have completed. A
  message logged from a `static { }` initializer or similar, ahead of *any* Rainbow Gum
  involvement, would be the scenario that actually distinguishes the two; not
  attempted here.
- **A harmless nuance, not a bug**: `rainbowgum-jdk` pulls in the plain `rainbowgum-jul`
  Handler bridge transitively (runtime scope) regardless of which JUL integration you
  actually want. With the custom `LogManager` active, `JULConfigurator`'s
  `Handler`-install call still runs (as part of Rainbow Gum's own bootstrap) and
  targets `Logger.getLogger("")`, which the custom `LogManager` now resolves to a
  `RainbowGumJULLogger` - a `Handler` gets attached to it, but `RainbowGumJULLogger`
  overrides `log(LogRecord)` to bypass Handler dispatch entirely, so the attached
  Handler is simply never invoked. No double-logging observed. Could be avoided with
  an explicit `<exclusion>` on `rainbowgum-jul` in the `rainbowgum-jdk` dependency if a
  fully "clean" dependency tree is wanted; left as-is here since it's inert.
