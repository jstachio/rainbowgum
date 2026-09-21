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

Requires a real GraalVM JDK as `JAVA_HOME` (a plain JDK cannot run `native-image`).
This project's own CI (`.github/workflows/native-image.yml`) pins GraalVM 21, but the
numbers in [RESULTS.md](RESULTS.md) were mostly measured on the latest available
(`25.3.4+1.r25`) after finding it roughly doubles throughput across all three apps over
GraalVM 21 - either works, they are just not the same numbers:

```sh
export JAVA_HOME=~/.sdkman/candidates/java/25.3.4+1.r25-graalce   # or any recent GraalVM install
./mvnw -f benchmark/native/rainbowgum-benchmark-native-rainbowgum/pom.xml -Pnative package
./mvnw -f benchmark/native/rainbowgum-benchmark-native-log4j2/pom.xml -Pnative package
./mvnw -f benchmark/native/rainbowgum-benchmark-native-logback/pom.xml -Pnative package
```

The same jars also run as plain apps on any regular JDK (no native-image, no GraalVM
needed) - `java -cp target/<jar>:<classpath> io.jstach.rainbowgum.benchmark.nativeimage.<name>.App`,
useful for an AOT-vs-JIT comparison; see the "HotSpot" section of
[RESULTS.md](RESULTS.md).

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

Load-test numbers for both scenarios (TTLL and structured) are in
[RESULTS.md](RESULTS.md).

## Structured logging (`STRUCTURED_FORMAT=gelf`)

Each app also supports a second, structured-output mode, selected by setting
`STRUCTURED_FORMAT=gelf` before starting it (an env var read in each `App.main`, which
sets the relevant framework's own config-selection system property before the first
logger is created - `logging.appender.console.encoder` for Rainbow Gum,
`log4j2.configurationFile` for Log4j2, `logback.configurationFile` for Logback). No
separate native image is needed for this - both configs are bundled in the same
executable.

"Varying support" turned out to be real: Rainbow Gum (`rainbowgum-json`'s `GelfEncoder`)
and Log4j2 (`log4j-core`'s own built-in `GelfLayout`, no extra dependency) both have
first-party GELF support. Logback has none - the third-party
`net.logstash.logback:logstash-logback-encoder` is used instead, producing
Logstash-format JSON rather than GELF, deliberately Logback's own idiomatic choice
rather than a forced, less-maintained GELF option nobody would actually pick in
practice.

Two more silent-at-runtime GraalVM gotchas turned up getting this working, both the
same *shape* as the two above - a clean build, a running server, wrong or missing
output only visible by actually reading it:

* The `-H:IncludeResources=logback\.xml$` fix from the plain-TTLL scenario only matched
  that one literal filename - `logback-json.xml` (the structured config) was just as
  silently absent as `logback.xml` originally was, for the identical reason. Broadened
  to `-H:IncludeResources=logback.*\.xml$` to cover both.
* `net.logstash.logback.encoder.LogstashEncoder` itself was not reachable by GraalVM's
  closed-world analysis - nothing statically referenced it, only a class-name string in
  `logback-json.xml` did - so even with the resource fixed, Logback logged
  `ClassNotFoundException` for the encoder and silently dropped it, leaving the
  `ConsoleAppender` with "No encoder set". Diagnosed with the GraalVM tracing agent
  (`-agentlib:native-image-agent=config-output-dir=...` against a real JVM-mode run of
  the same scenario) rather than guessing, then fixed with one small, hand-written
  `META-INF/native-image/io.jstach.rainbowgum/rainbowgum-benchmark-native-logback/reflect-config.json`
  entry for just that one class - deliberately not the agent's full, noisier captured
  output, most of which turned out to already be unnecessary.

## Locking strategy (`APPENDER_TYPE`, Rainbow Gum only)

Rainbow Gum's app also honors `APPENDER_TYPE`, setting
`logging.appender.console.type` before the first logger is created - e.g.
`APPENDER_TYPE=SYNCHRONIZED_THREAD_LOCAL_BUFFER` to try the locking/buffering strategy
closer to Log4j2's own `synchronized`-based approach instead of the
`LOCK_THREAD_LOCAL_BUFFER` default. See [RESULTS.md](RESULTS.md) for what this actually
does to the numbers here - it is a real, repeatable win in this specific environment
(GraalVM Substrate VM), the opposite of what the plain-HotSpot finding that made
`LOCK_THREAD_LOCAL_BUFFER` the default would predict.

## Output encoding strategy (`OUTPUT_TYPE=STRING`, `ENCODER_TYPE=GET_BYTES`, Rainbow Gum only)

Rainbow Gum's app also honors `OUTPUT_TYPE=STRING`, setting
`logging.appender.console.output=string-stdout:///` - a custom `LogOutput` (this
module's own `StringStdOutOutput`, registered via a hand-written `Configurator`/
`META-INF/services` entry, since this module has no `@ServiceProvider` annotation
processor wired up) identical to the default stdout output except it hints
`WriteMethod.STRING` instead of `BYTES`. This genuinely hands the output a `String` -
`LogOutput.write(LogEvent, String)`'s default implementation then does a plain
`String.getBytes(UTF_8)` call (no `CharsetEncoder`), matching Logback's own encode
strategy, but *inside* the appender's lock (`Buffer.drain(...)` runs there), which the
default `BYTES` path avoids by design - see [RESULTS.md](RESULTS.md) for why this isn't
quite an apples-to-apples "try Logback's strategy" swap and what it actually does to the
numbers.

`ENCODER_TYPE=GET_BYTES` sets `logging.appender.console.encoder=get-bytes-ttll:///` -
this module's own `GetBytesEncoderConfigurator`, registered the same way, resolving to
the standard TTLL encoder built with `LogEncoder.Builder#useGetBytes(true)`. Unlike
`OUTPUT_TYPE=STRING`, this pairs the `getBytes()` encode strategy with the **default**
`BYTES`-hinting output - the conversion happens in `encodeToBuffer(...)`, outside the
lock, the same timing the default `CharsetEncoder`-based path already gets. This is the
correct way to isolate "which encode strategy" from "how much of it happens under the
lock"; an earlier attempt at that isolation modified `StringBuilderBuffer`
(`WriteMethod.STRING`'s own buffer) to do this instead, which was reverted - it broke
`STRING`'s actual contract of genuinely handing the output a `String` (some outputs,
e.g. a hypothetical JFR-backed one, want that and nothing else). See
[RESULTS.md](RESULTS.md) for the numbers.

## Baseline: logging mostly off (`LOG_LEVEL`)

All three apps honor `LOG_LEVEL` (e.g. `LOG_LEVEL=ERROR`), overriding the root level -
Rainbow Gum via `logging.level`, Log4j2/Logback via a `${...:-INFO}` substitution in
each config file. Since none of `BenchHandler`'s calls are above `INFO`, this disables
every log call in the request path, exercising each framework's near-zero-cost
disabled-level path through a real HTTP server under load rather than a synthetic
microbenchmark. See [RESULTS.md](RESULTS.md) - this is the scenario where memory (not
throughput) turns out to be the most reliable, repeatable signal this benchmark
produces.

## Not yet done

* Why `SYNCHRONIZED_THREAD_LOCAL_BUFFER` wins here specifically (Substrate VM's own
  lock/monitor implementation? this sandbox? something else?) - the result is recorded,
  the explanation is not.
* Platform-thread scenario (currently virtual threads only, both client and server
  side).
* Real profiling (JFR/async-profiler) of the TTLL time-formatting theory in
  RESULTS.md - not chased yet, flagged as unverified.
