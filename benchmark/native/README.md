# GraalVM native image benchmark

Compares Rainbow Gum, Log4j2, and Logback as real GraalVM native executables - no Spring
Boot (nothing to control for beyond the logging backend itself), a plain
`com.sun.net.httpserver.HttpServer` for the HTTP input, TTLL-equivalent console output
for all three (see each module's `log4j2.xml`/`logback.xml`, matched field-for-field
against Rainbow Gum's zero-config `LogEncoder.ofTTLL()` default).

## Modules

* `rainbowgum-benchmark-native-share` - the shared request-handling workload
  (`BenchHandler`, five log calls + MDC request id, same shape as
  `feature/webapp-benchmark`'s `BenchController`) and `BenchServer` (starts
  `HttpServer` on virtual threads).
* `rainbowgum-benchmark-native-driver` - the same dependency-free HTTP load generator as
  `feature/webapp-benchmark` (`benchmark/webapp/rainbowgum-benchmark-webapp-driver`),
  copied verbatim (only the package name changed - `native` is a Java keyword, so the
  Java package is `nativeimage`, not `native`, even though the Maven module/directory is
  `native`).
* `rainbowgum-benchmark-native-{rainbowgum,log4j2,logback}` - one app per framework.

## Building and running a native image

Requires a real GraalVM JDK as `JAVA_HOME` (a plain JDK cannot run `native-image`):

```sh
export JAVA_HOME=~/.sdkman/candidates/java/21.0.12-graal   # or any GraalVM 21+ install
./mvnw -f benchmark/native/rainbowgum-benchmark-native-rainbowgum/pom.xml -Pnative package
./mvnw -f benchmark/native/rainbowgum-benchmark-native-log4j2/pom.xml -Pnative package
./mvnw -f benchmark/native/rainbowgum-benchmark-native-logback/pom.xml -Pnative package
```

Each produces a real executable at `target/rainbowgum-benchmark-native-<name>`. Run one,
then drive it with the shared driver from another shell:

```sh
target/rainbowgum-benchmark-native-rainbowgum &        # PORT env var overrides 8080
./mvnw -f benchmark/native/rainbowgum-benchmark-native-driver/pom.xml package -q
java -jar benchmark/native/rainbowgum-benchmark-native-driver/target/*.jar \
  --url http://localhost:8080/greet/world --duration 30 --label rainbowgum-native
```

The native-image step is profile-gated (`-Pnative`, same template as
`test/rainbowgum-test-native/pom.xml`) - a normal `mvn install` from the repo root only
compiles plain jars for all three apps, same as every other `benchmark/` module; it never
invokes `native-image` and needs no GraalVM JDK to succeed.

## What actually needed fixing to get Log4j2 and Logback working under native-image

Both **built** on the first try with no changes. Both **silently produced wrong output at
runtime** on that first try - neither crashed, which is what made this worth writing
down.

* **Log4j2**: `rainbowgum-benchmark-parent`'s own `log4j-slf4j2-impl` pin
  (`3.0.0-beta2`, used by the older hand-rolled micro-benchmarks) built fine under
  native-image, but at runtime threw `NoSuchMethodException` trying to reflectively
  construct `Log4jContextFactory`, and silently fell back to a no-op `SimpleLogger` -
  meaning **zero** of the app's own log lines were ever written, while the app otherwise
  ran and served requests completely normally. Log4j's own GraalVM reachability
  metadata ("bundled... since 2.25.0" per `logging.apache.org/log4j/2.x/graalvm.html`)
  is on the 2.x line; the still-in-development 3.x beta doesn't have it yet. Fixed by
  pinning this module's own `log4j-slf4j2-impl` to `2.26.1`, overriding the parent's
  3.x pin (see `rainbowgum-benchmark-native-log4j2/pom.xml`).
* **Logback** (`1.6.3`, the version already pinned in `rainbowgum-benchmark-parent`):
  built fine, ran fine, served requests fine - but `logback.xml` was silently absent
  from the image (GraalVM does not automatically bundle classpath resources whose name
  isn't statically determinable from the calling code, unlike Log4j2's own config
  lookup, which apparently is), so Logback logged "Could NOT find resource
  [logback.xml]" and fell back to its own zero-config `BasicConfigurator` - the wrong
  pattern, and crucially the wrong level (`DEBUG`, not `INFO`), which would have
  meant significantly more log volume than the other two apps in the same benchmark.
  Fixed with one `native-maven-plugin` buildArg:
  `-H:IncludeResources=logback\.xml$` (see
  `rainbowgum-benchmark-native-logback/pom.xml`).
* A `<statusListener class="ch.qos.logback.core.status.NopStatusListener" />` was tried
  to quiet Logback's own startup status noise on stdout, but that listener class itself
  isn't reflectively instantiable under native-image without its own separate
  reflect-config entry - not chased further since the noise is one-time at startup, never
  during the actual measured load.

Rainbow Gum needed no changes at all: `rainbowgum-slf4j` on the classpath is the entire
setup, console + TTLL are its own zero-config defaults.

## Not yet done

* Actual load-test numbers (throughput/latency/RSS via the driver, across virtual vs
  platform threads) - the point of this pass was getting all three working under
  native-image at all, not measuring yet.
* Lock strategy tuning (`logging.appender.<name>.type`) if Rainbow Gum's native numbers
  need it - deliberately deferred until there is a real number to react to.
