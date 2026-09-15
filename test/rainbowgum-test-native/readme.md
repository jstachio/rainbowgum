A smoke test proving Rainbow Gum works under GraalVM native-image: real logging,
through the normal `rainbowgum-simple` zero-config bootstrap, plus a custom,
ServiceLoader-discovered `RainbowGumServiceProvider.RainbowGumProvider`
(`NativeTestRainbowGumProvider`), so this also proves a user's own SPI extension
survives native-image's closed-world analysis, not just Rainbow Gum's own built-in
providers.

## Why this module is not part of the normal build

Deliberately not listed in `test/pom.xml`'s `<modules>`. A native-image build takes
minutes, needs a GraalVM JDK, and produces nothing worth installing/deploying, so it
must not run as part of the normal reactor (`mvn install`/`mvn verify` from the root). It
is built on its own, in CI (`.github/workflows/native-image.yml`, a matrix job running
in parallel across operating systems) and locally on demand:

```
./mvnw install                                          # from the repo root, once
./mvnw -f test/rainbowgum-test-native/pom.xml -Pnative verify   # needs a GraalVM JDK on PATH
```

Without `-Pnative`, this module just compiles; nothing runs, since its one test class,
`NativeImageRunIT`, is an integration test (`maven-failsafe-plugin`, not
`maven-surefire-plugin`), and it needs the native executable the `native` profile builds.

## How this actually works

`native-maven-plugin`'s `compile-no-fork` goal (bound to the `package` phase) builds a
real executable from `Main`, a plain class with no JUnit anywhere near its classpath.
`NativeImageRunIT` (`maven-failsafe-plugin`, `integration-test`/`verify` goals, so it
runs after `package`) launches that already-built executable itself with a plain
`ProcessBuilder`, captures its output, and asserts on it: a black-box smoke test,
deliberately, run from a normal JVM with no part in the native image itself.

An earlier version of this module used `native-maven-plugin`'s own `test` goal instead,
compiling JUnit's launcher/engine directly into the native image. Dropped that approach
after hitting two separate problems worth recording here:

- That goal bundles its own JUnit Platform dependency, at whatever version was current
  when `native-maven-plugin` 0.10.6 was released (1.10.0), alongside whatever JUnit
  version the project's own classpath already has (6.1.3 here). Having both on the same
  native-image classpath fails with `OutputDirectoryCreator not available; probably due
  to unaligned versions of the junit-platform-engine and junit-platform-launcher jars`,
  since `junit-platform-engine` 6.1.3 expects an SPI method the bundled 1.10.0
  `junit-platform-launcher` doesn't have.
- Even after resolving that (by declaring `junit-platform-launcher`/`-console`/
  `-reporting` explicitly at 6.1.3, so the plugin's own "missing dependency" inference
  had nothing left to inject), the native JUnit runner's own progress-reporting code
  triggers a `DecimalFormatSymbols`/`Currency`/`Calendar` chain that calls
  `System.getLogger(...)`, which resolves and instantiates `rainbowgum-jdk`'s
  `SystemLoggingFactory` as a side effect: a GraalVM build-time class-initialization
  error, unrelated to anything this module's own code does.

A plain executable sidesteps both. It still needs a
`--initialize-at-build-time` flag for two specific Rainbow Gum classes
(`io.jstach.rainbowgum.jdk.systemlogger.SystemLoggingFactory` and
`io.jstach.rainbowgum.systemlogger.RainbowGumSystemLoggerFinder$RouterProvider`):
GraalVM's own class-initialization analysis reaches `System.getLogger(...)` from
unrelated JDK static-initialization paths (`Currency`/`DecimalFormatSymbols`, `java.time`
formatting) even without JUnit involved at all, each of which resolves and instantiates
the registered `System.LoggerFinder` (`SystemLoggingFactory`) as a side effect.

This module's own `pom.xml` does not pass that flag, though, and does not need to:
`rainbowgum-jdk` and `rainbowgum-systemlogger` each bundle their own
`META-INF/native-image/.../native-image.properties` declaring it, which native-image's
own embedded configuration discovery picks up automatically from their jars on this
build's classpath. See `doc/overview.html`'s GraalVM Native Image section for the full
explanation, including why this used to require flagging the *whole*
`io.jstach.rainbowgum` package (fixed: `RainbowGumSystemLoggerFinder`'s constructor no
longer does real, eager work). Verified by hand, across both fixes, that the resulting
binary still produces correct, real-runtime-derived output (varying command-line args
across separate runs of the same binary), not stale build-time-baked state.
