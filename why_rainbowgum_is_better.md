# Why Rainbow Gum is Better than X

Where X is one of the following JVM logging implementations:

* [Logback](https://logback.qos.ch/)
* [Log4j 2](https://logging.apache.org/log4j/2.x/)
* [Reload4j](https://reload4j.qos.ch/) (Log4j 1, still alive as a security-patched fork)
* [tinylog](https://tinylog.org/v2/)

This document is naturally biased and somewhat opinionated marketing but has
some reasonable backing. If you are the author of one these libraries and would
like us to make corrections we are happy to do so.

For those looking for regular documentation the [user
guide](https://jstach.io/rainbowgum/) is the actual documentation.

Rainbow Gum aims to make JVM logging safer, smaller, faster, and easier to operate:

* **Safer:** configuration errors fail at startup with context, and core avoids
  expression languages, XML parsers, and mutable logging components.
* **Smaller:** core requires only `java.base`. File output and other optional
  capabilities live in separate modules.
* **Faster:** specialized loggers minimize disabled-level overhead, and configurable
  locking strategies help tune throughput for HotSpot and GraalVM Native Image.
* **Modern:** built on Java 21+, with sealed APIs, JSpecify nullability annotations,
  and static analysis to catch mistakes before they reach production.
* **More observable:** built-in alerts, metrics, and diagnostic reports show both
  failures and how the logging system is actually configured.
* **More integration:** native support for Spring Boot, Helidon, and Micronaut,
  alongside SLF4J, System.Logger, JUL, Log4j2 API, and JBoss Logging integrations.

Regardless, logging facades make Rainbow Gum easy to try: it can replace Logback
without changing application logging code.

The comparisons below explain these choices, with measurements and tradeoffs.

## Safe

Rainbow Gum makes configuration mistakes visible and keeps the logging system's
moving parts limited. It fails fast on invalid configuration during initialization;
after startup, logging failures are reported through alerts rather than crashing
the application.

In our testing, other frameworks often swallowed configuration errors or reported
them without enough context. Logback and Log4j2's older configuration advice also
made it easy to select the wrong settings or miss that the intended configuration
had not loaded. See [error_messages_comparison.md](error_messages_comparison.md)
for concrete examples of what each framework reports when a component is
misconfigured.

Rainbow Gum also limits what configuration can do:

* **Stable components:** configuration is generally fixed after initialization,
  apart from logging levels. This avoids the mutable component graphs and
  getter/setter configuration used by Logback, Log4j2, and reload4j.
* **No expression language:** log messages remain data, with no expression
  evaluation or lookup interpolation. Message lookups enabled
  [Log4Shell](https://en.wikipedia.org/wiki/Log4Shell) in affected Log4j2 versions.
* **Fewer implicit mechanisms:** core has no XML configuration parser and avoids
  reflection apart from mechanisms such as `ServiceLoader`.
* **Explicit input sources:** apart from `ServiceLoader` discovery, core does not
  read resources from the classpath. It also does not read files from the filesystem
  or, apart from color detection below, environment variables on its own. Its
  default configuration source is Java
  system properties. Environment variables are read only when you add them as a
  property source, and loading a `logging.properties` file is the job of an optional
  module such as `rainbowgum-simple-props`. The only environment variables core
  reads on its own are two standard ones, `NO_COLOR` (the
  [no-color.org](https://no-color.org/) convention) and `TERM`, for ANSI color
  detection. That check runs only when a formatter that colors asks for it: the
  TTLL formatter when its `color` property is `default` or `detect`, or
  `rainbowgum-pattern`.
* **Explicit output capabilities:** core writes only to stdout and stderr. File
  output and other destinations require additional modules; configuration alone
  cannot enable an output implementation that is not installed.

These choices reduce the security surface and make failures easier to diagnose;
they do not prove Rainbow Gum is more secure. Rainbow Gum has a shorter track
record and less scrutiny than Logback or Log4j2. Logback was not affected by
Log4Shell, and its author also maintains reload4j with security fixes. That history
and experience deserve weight alongside a smaller feature surface.

## Small

Rainbow Gum has one of the smallest footprints among general purpose JVM logging
implementations. The comparisons below cover the library JARs, the Java runtime
modules they need, and process memory when application logging is disabled.

### Library size

| Library | Version | Jar size(s) | Sum | Notes |
| --- | --- | ---: | ---: | --- |
| **rainbowgum** (core + slf4j) | 0.11.0 | 366 + 63 KiB | **429 KiB** | Requires only `java.base` |
| logback (classic + core) | 1.6.3 | 286 + 635 KiB | 921 KiB | Requires `java.xml`, transitively |
| log4j2 (core + api + slf4j2 binding) | 3.0.0-beta2 | 1434 + 347 + 26 KiB | 1.76 MiB | Kitchen sink of features |
| tinylog (api + impl + slf4j binding) | 2.8.0 | 63 + 134 + 14 KiB | 211 KiB | Smallest JAR total |

These are the library sizes for the versions shown, excluding the Java runtime.
Rainbow Gum is smaller than Logback and Log4j2. tinylog has the smallest JARs, with
fewer features: for example, it has no programmatic configuration API comparable to
Rainbow Gum's builders.

### Java runtime size

Rainbow Gum's `rainbowgum-core` requires only `java.base`. Logback and Log4j2 require
`java.xml`, including when the application does not use XML configuration. Those
module dependencies matter when packaging a minimal Java runtime with `jlink`.

tinylog's smaller JARs also come with additional runtime dependencies.
`tinylog-api` requires `java.management`. `tinylog-impl` declares `java.sql` and
`java.naming` as optional (`requires static`), but references to `java.sql` extend
beyond its JDBC writer into pattern formatting. For example,
[`DateToken`](https://github.com/tinylog-org/tinylog/blob/2934408bfa30a02cb241720211d391b88b18c61f/tinylog-impl/src/main/java/org/tinylog/pattern/DateToken.java#L16)
imports `java.sql.PreparedStatement` and `java.sql.Timestamp`; `jdeps -v` finds 28
classes referencing `java.sql` types. The modules an application needs therefore
depend on which features it uses, not just which dependencies are marked optional.

The following JDK 26 measurements show how adding modules increases the size of an
**unpacked runtime image on disk**. Each row includes the modules from the previous
row. These are runtime sizes, excluding application JARs:

| Runtime modules | Unpacked image size |
| --- | ---: |
| `java.base` (what Rainbow Gum core needs) | 40.70 MiB |
| + `java.management` (`tinylog-api` requires it) | 41.36 MiB |
| + `java.sql` | 45.64 MiB |
| + `java.naming` (for the JDBC writer's JNDI lookup) | 46.05 MiB |

Adding all three modules costs **5.35 MiB (+13%)**, more than ten times the roughly
200 KiB saved by tinylog's smaller JARs. The exact runtime footprint depends on the
application's other dependencies and the JDK used to build it.

A ZIP distribution is smaller than the unpacked image. The
[`rainbowgum-test-jlink`](test/rainbowgum-test-jlink) example packages an application
with its Java runtime into a roughly **14 MB ZIP**. That archive size is a separate
measurement from the unpacked runtime sizes above.

### Memory with application logging disabled

In the [GraalVM native-image benchmark](https://github.com/jstachio/rainbowgum/blob/feature/graalvm-native-benchmark/benchmark/native/RESULTS.md#baseline-logging-mostly-off-log_levelerror),
setting `LOG_LEVEL=ERROR` disables every logging call in the HTTP request path,
since those calls are all at `INFO` or below. The logging frameworks remain loaded,
and the server continues handling requests under load.

| Measurement (average of three runs) | Rainbow Gum | Logback | Log4j2 |
| --- | ---: | ---: | ---: |
| Process RSS | **40.9 MB** | 51.0 MB | 59.0 MB |

Rainbow Gum used about **20% less memory than Logback** and **31% less than Log4j2**
in this scenario, with similar throughput across all three (about 75,600 requests
per second). RSS measures the whole native process, including the HTTP server;
these figures describe that benchmark's baseline footprint, not the logging
library's memory use in isolation.

## Fast

Rainbow Gum combines low logging overhead with configurable buffering and locking.
The following benchmarks compare throughput in real HTTP applications under load.

### Spring Boot

The [Spring Boot benchmark](https://github.com/jstachio/rainbowgum/tree/feature/webapp-benchmark/benchmark/webapp)
covers platform and virtual threads with plain and structured (GELF) logging:

| scenario | Rainbow Gum | Logback | Log4j2 |
|---|---:|---:|---:|
| virtual threads | **26,324 req/s** | 25,554 req/s | 18,990 req/s |
| virtual threads + GELF | **27,450 req/s** | 25,797 req/s | 17,734 req/s |
| container/12-factor style (console-only, GELF) | **34,172 req/s** | 15,002 req/s | 19,639 req/s |
| container/12-factor + virtual threads | **25,572 req/s** | 21,587 req/s | 15,445 req/s |

Rainbow Gum's strongest result is the console-only GELF scenario, a common setup
for container applications: about **2.3x Logback** and **1.7x Log4j2** throughput.

### GraalVM Native Image

The [native-image benchmark](https://github.com/jstachio/rainbowgum/tree/feature/graalvm-native-benchmark/benchmark/native)
uses `com.sun.net.httpserver.HttpServer`, virtual threads, and TTLL or GELF logging:

| format | Rainbow Gum | Logback | Log4j2 |
|---|---:|---:|---:|
| TTLL | **91,773 req/s** | 71,939 req/s | 85,066 req/s |
| GELF | **92,264 req/s** | 56,772 req/s | 82,037 req/s |

We put extra work into getting the competing implementations running correctly
under GraalVM Native Image. For Logback, that included resource configuration and
reflection metadata so the intended configuration and GELF encoder actually loaded.
Rainbow Gum needed neither. The benchmark README records the setup, and
`0-11-2-RESULTS.md` contains the measurements.

The native results above used `SYNCHRONIZED_THREAD_LOCAL_BUFFER`. To let Rainbow Gum
select platform-specific locking and encoding defaults, enable
[`logging.global.optimize`](https://jstach.io/rainbowgum/io.jstach.rainbowgum/io/jstach/rainbowgum/LogProperties.html#GLOBAL_OPTIMIZE_PROPERTY):

```properties
logging.global.optimize=true
```

Or pass `-Dlogging.global.optimize=true` as a system property. Currently this selects
synchronized buffering and `String.getBytes(Charset)` encoding under native-image,
while leaving HotSpot defaults unchanged. Explicit appender or encoder choices take
precedence; the automatic selections can evolve between releases.

### Configurable locking strategy

Under concurrent load, contention on output writes can dominate logging cost.
Rainbow Gum lets each appender choose a buffering and locking strategy independently
of its encoder and output, through `logging.appender.<name>.type` or
`LogAppender.Builder#appenderType`:

* `LOCK_THREAD_LOCAL_BUFFER` (default): encode in a reused per-thread buffer,
  then acquire a `ReentrantLock` to write.
* `SYNCHRONIZED_THREAD_LOCAL_BUFFER`: the same buffering, with a `synchronized`
  block guarding the write.
* `LOCK_NEW_BUFFER`: allocate a fresh buffer per event, encode outside the lock,
  then lock to write. No `ThreadLocal` reuse.
* `REUSE_BUFFER`: share one buffer, locking across both encoding and writing.

Logback's output locking follows the `LOCK_NEW_BUFFER` approach: a fresh buffer,
encoding outside the lock, and a `ReentrantLock` for the write. Log4j2's
[`OutputStreamManager`](doc/log4j2-flush-attribution.md) uses `synchronized` for
buffer writes and flushing, with no configuration option to switch that locking
strategy. On JDK 21, blocking I/O inside `synchronized` can
[pin virtual threads to their carrier threads](https://openjdk.org/jeps/444),
reducing scalability.

There is nothing inherently wrong with `synchronized`; Rainbow Gum offers it too,
and it performs well in our native-image benchmarks. JDK 24 also
[removes monitor-related virtual-thread pinning](https://openjdk.org/jeps/491).
The limitation is having the choice made for you. Rainbow Gum lets applications
select the locking strategy that suits their JDK and workload.

### Minimal overhead for disabled levels

For loggers with fixed levels, Rainbow Gum resolves the level when the logger is
created and selects a specialized SLF4J implementation. Disabled methods have empty
bodies, which the JIT can eliminate. For a logger configured at `WARN`:

```java
@Override
public void info(String msg) {
}
```

There is no level comparison on each call. `isInfoEnabled()` still returns `false`,
but `info(...)` does not need to consult it. Loggers configured for runtime level
changes use a separate implementation.

tinylog also optimizes disabled calls using constant flags, but those flags reflect
the lowest level across the application. Enabling `DEBUG` for one package makes
other debug calls require a runtime check. Rainbow Gum's fixed-level optimization
is per logger, so changing another logger's configuration does not affect its cost.

## Modern

Rainbow Gum will continue to embrace modern JDK features and uses the latest JDK
for building. The code uses newer JDK 21+ features such as sealed classes,
triple quote strings, and pattern matching making contribution easier and safer.
Advance static analysis tools such as Checkerframework, Error Prone and Nullaway
are checked on every build. Rainbow Gum follows JSpecify.

Rainbow Gum also follows [Tip and Tail](https://openjdk.org/jeps/14) as well as
semver. Expect frequent releases corresponding to improvements with the JDK or
GraalVM native but with a core and configuration that is very backward
compatible. The downside to this is that old versions will not get features
backported (ignoring security issues).

The other logging libraries move slower and some have had a tradition of breaking semver or
don't have clear versioning policies.

Logback does get credit here as it has been rapidly improving.

### Follows 12 Factor

Rainbow Gum has been designed for smaller but more elastic cloud
services where logging is likely aggregated and output should
follow [12 Factor](https://12factor.net/logs) recommendations: *"unbuffered, to
stdout"*. That is why file output, rolling file output, are not included in the
core module (why pay for this when everyone is logging to stdout). 12 Factor
also recommends [backing services be URI based](https://12factor.net/backing-services).
Rainbow Gum's output properties are actually URIs: `logging.appender.console.output=stdout:///`
which allows for pretty powerful one line configuration.

While the other logging frameworks can be configured for 12 factor we found
disturbingly that **Log4J2 actually buffers events** and cannot be turned off.
It is a small window but even on synchronous and immediate flush turned on it
does buffer events waiting for a flush winner and the thread that produced the
event may not be the one that finally writes it. In our testing we could only
get this buffer to hold a maximum of 3 events but on different hardware this
maybe much greater.

Logback and Rainbow Gum flush on every event with the thread that created the
event. In irony TinyLog uses what Log4J2 does as a form of asynchronous log
writting with what it calls the writer thread.

### Framework integration

Rainbow Gum provides dedicated integrations for Spring Boot 3/4, Micronaut 5, and
Helidon SE 4. These participate in framework initialization or logging management,
in addition to accepting log calls through a facade.

| Logging implementation | Spring Boot | Micronaut | Helidon SE |
| --- | --- | --- | --- |
| **Rainbow Gum** | Dedicated `LoggingSystem` (Boot 3/4) | Dedicated `ManagedLoggingSystem` (5.x) | Dedicated `LoggingProvider` (4.x) |
| Logback | Built-in support; default backend | Built-in logging and management support | Through Helidon's SLF4J integration |
| Log4j2 | Built-in support | Built-in logging and management support | Dedicated Helidon Log4j provider |
| tinylog | Adapter-based setup | SLF4J adapter; no dedicated management integration identified | Adapter-based setup; no dedicated Helidon provider identified |

Spring Boot documents its [Logback and Log4j2 integrations](https://docs.spring.io/spring-boot/how-to/logging.html),
and Micronaut supplies [management implementations for both](https://docs.micronaut.io/4.9.1/api/io/micronaut/management/endpoint/loggers/impl/package-summary.html).
Helidon provides [SLF4J integration](https://helidon.io/docs/4.0.7/apidocs/io.helidon.logging.slf4j/io/helidon/logging/slf4j/package-summary.html)
and a [Log4j provider](https://helidon.io/docs/v4/apidocs/io.helidon.logging.log4j/io/helidon/logging/log4j/Log4jProvider.html).
tinylog offers [logging adapters](https://tinylog.org/download-preview/) and links to a
[Spring Boot example](https://tinylog.org/external-resources/); routing log calls does
not by itself integrate framework configuration or management endpoints.

Rainbow Gum's [integration guide](https://jstach.io/rainbowgum/#spring_boot) covers
the modules and setup. Its Micronaut module supports `logger.levels.*` and
`/loggers`, while the Helidon provider handles bootstrap. Spring Boot 4 also has an
optional Actuator module for Rainbow Gum metrics and diagnostic reports.

### Scoped Key Values

Both Logback and Rainbow Gum are exploring Scoped Values:

* https://jstach.io/rainbowgum/#scoped_key_values
* https://github.com/qos-ch/logback-scoped-mdc

A notable caveat at the moment for Logbacks Scoped Values:

> * <p><b>Note:</b> This converter reads from the thread-current {@link ScopedValue}
> * binding at format time. It works correctly with synchronous appenders. With
> * asynchronous appenders, the scoped values will not be available on the
> * formatting thread.</p>

Rainbow Gum scoped key values gets passed down the stack and is available to async publishers.


### Built-in operational metrics, no extra dependency

Rainbow Gum tracks a small, well known set of counters about the logging system itself
(events dropped, encoder buffer trims, failed writes) out of the box, with zero required
dependency beyond `java.base`:

```java
var snapshot = config.metrics().snapshot();
// [Metric[name=events.dropped, level=ERROR, value=3], ...]
```

Neither Logback nor Log4j2 ship anything like this: finding out how many events
an appender has silently dropped, or how often the encoder's buffer had to
shrink back down, means wiring up Micrometer or JMX yourself first. Rainbow Gum
answers that question with a method call. Rainbow Gum provides observability
for Spring Boot users with a Spring Boot actuator.

### GraalVM Native, and jlink friendly


#### GraalVM Native

None of the other frameworks in this comparison has strong, native-first GraalVM support
today, though it's worth being fair about where each one actually stands:

* **Logback** ships no GraalVM reachability metadata of its own. What exists comes from
  the third-party [`graalvm-reachability-metadata`](https://github.com/oracle/graalvm-reachability-metadata)
  repository, not `logback.qos.ch`. It is, however, doing real work in this direction:
  [`logback-tyler`](https://github.com/qos-ch/logback-tyler) translates a `logback.xml`
  file into a plain Java class (`TylerConfigurator`) that configures Logback with no XML
  parser and no reflection at all, the same two things Rainbow Gum avoids by design from
  the start. It is a genuinely good sign for Logback's native-image future, even though it
  is a still-new, opt-in translation step bolted onto an XML-first architecture rather
  than something built in from the ground up.
* **Log4j2** does have an official GraalVM page (`logging.apache.org/log4j/2.x/graalvm.html`),
  which is more first-party documentation than Logback, Reload4j, or tinylog have. It is
  brief, though: a page of "here's what's supported, here are links to GraalVM's own
  docs" rather than a worked, CI-verified example.
* **Reload4j and tinylog** have no dedicated native-image documentation at all.

Rainbow Gum's own GraalVM Native Image documentation (in the
[user guide](https://jstach.io/rainbowgum/)) goes further than any of these: it explains,
with root causes, the specific JDK-internal
code paths (`java.time`/`java.util.Locale` formatting, `TrustStoreManagerFeature`) that
incidentally call `System.getLogger(...)` during a native-image build and why that is
safe, and `test/rainbowgum-test-native` is a real application built to a native
executable and run as a black-box smoke test in CI on every change. This is more than a claim
that it works.

#### Module first: jlink friendly

Rainbow Gum is very modular and every module has a `module-info.java` making jlink
packaging much easier. Unless there is an explicit integration most of Rainbow
Gum's modules only depend on `java.base`.

* Log4j2, Reload4j, and Logback all require the `java.xml` module. Logback pulls it in
  transitively even for applications that never write a line of XML.
* Log4j2 and Reload4j (and Tiny Log) ship their own separate logging facade API on top of
  SLF4J that cannot be removed even if your application only ever calls SLF4J.

### The correct external log rotation, not "copy truncate"

The classic Unix daemon convention for external log rotation is: an external tool (e.g.
`logrotate`) moves the log file aside, then signals the running process to close and
reopen it by its original name, releasing the moved file's descriptor cleanly, with no
dropped or corrupted events. The alternative most JVM logging frameworks push you toward
instead (`logrotate`'s `copytruncate` mode, or the framework's own internal size/time
based rolling policy) either truncates the file out from under a process that still has
it open (a real event-loss/corruption race) or takes rotation out of `logrotate`'s hands
entirely.

Rainbow Gum's `LogOutputRegistry#reopen()` does the correct move-then-reopen dance
against any output, triggerable over a plain HTTP endpoint (no extra dependency, a few
lines of application code) or, on Linux/Docker, directly from a logrotate `postrotate`
script via a real Unix signal (`kill -USR1 $(cat app.pid)`, no open port and no
application code at all) through the optional `rainbowgum-signal` module. Neither
Logback nor Log4j2 offers anything like it: both expect their own internal rolling
policy to be the only thing touching the file, with `copytruncate` as the sole fallback
if you insist on using `logrotate` alongside them anyway.

### Type and null safety

Rainbow Gum's entire public API is annotated with [JSpecify](https://jspecify.dev/)
`@Nullable`/`@NullMarked`, checked in CI with both CheckerFramework's Nullness Checker and
NullAway. A `@Nullable`-returning method genuinely means "can return null," not "might,
depending on an implementation detail the annotations don't capture." Neither Logback nor
Log4j2's public API carries any nullability annotations at all.

### Test coverage

Rainbow Gum sits at **95% line coverage** (JaCoCo, aggregated across every module and
republished with every snapshot build; see the
[coverage report](https://jstach.io/rainbowgum/coverage/)), including end-to-end
golden-string tests of what happens when a component is misconfigured, not just the
happy path. See
[error_messages_comparison.md](error_messages_comparison.md) for what that buys you in
practice.

Credit where due again: tinylog does the same thing, and does it well. Its
[Codecov badge](https://app.codecov.io/gh/tinylog-org/tinylog/tree/v2.8) shows **94%**,
tracked continuously. That figure uses Codecov's stricter metric, which counts a line
with partially covered branches as a miss; by that metric Rainbow Gum measured **92%**
when it still reported to Codecov, so the two projects are close. Logback's and Log4j2's actual
coverage, by contrast, is not something you can just go look up: neither publishes a
number anywhere. The obvious place to check, Codecov, returns "unknown" for both
([logback](https://codecov.io/gh/qos-ch/logback), [log4j2](https://codecov.io/gh/apache/logging-log4j2)),
meaning no coverage data has ever been uploaded there. That doesn't mean they're
untested (both have large, long-running test suites); it means there is no public
number to compare against, favorable or not, the way there is for Rainbow Gum and
tinylog.

To be clear, 95% is not a number Rainbow Gum is chasing toward 100 for its own sake.
Line coverage measures which lines *ran* during a test, not which lines were actually
*verified*. A test that only exists to touch a line pads the percentage without proving
anything, and 100% can just as easily mean "we wrote a trivial test for every line"
as "we deleted the dead code that shouldn't have been there in the first place."
Rainbow Gum prefers real, end-to-end tests (including parameterized ones that sweep many
input shapes through the same real assertion, like `ConfigFailureTest`'s enum-driven
cases) over hand-crafted unit tests aimed at specific lines. The percentage is a
byproduct of testing real behavior thoroughly, not the target itself.

## Easy

### Programmatic configuration is dramatically less verbose

All three frameworks support programmatic (no XML/properties file) configuration. Here is
the same target configuration (a rolling file appender, a pattern with a timestamp/level/
logger/message, 10MB rotation, 7 files retained) written natively in each, actually
compiled and run against the real libraries (not hand-waved):

<table>
<tr><th>Rainbow Gum (7 lines)</th><th>Log4j2 (18 lines)</th><th>Logback (23 lines)</th></tr>
<tr><td>

```java
RainbowGum.builder()
    .route(r -> {
        r.appender("rolling", a -> a.output(
            RollingFileOutput.of(b -> {
                b.fileName("app.log");
                b.maxFileSize(DataSize.ofMegabytes(10));
                b.maxHistory(7);
            })));
        r.level(Level.INFO);
    })
    .set();
```

</td><td>

```java
var builder = ConfigurationBuilderFactory
    .newConfigurationBuilder();
builder.setStatusLevel(Level.WARN);

var layout = builder.newLayout("PatternLayout")
    .addAttribute("pattern",
        "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n");

var trigger = builder.newComponent("Policies")
    .addComponent(builder
        .newComponent("SizeBasedTriggeringPolicy")
        .addAttribute("size", "10MB"));

var strategy = builder
    .newComponent("DefaultRolloverStrategy")
    .addAttribute("max", "7");

var appender = builder
    .newAppender("rolling", "RollingFile")
    .addAttribute("fileName", "app.log")
    .addAttribute("filePattern",
        "app-%d{yyyy-MM-dd}-%i.log.gz")
    .add(layout)
    .addComponent(trigger)
    .addComponent(strategy);
builder.add(appender);

builder.add(builder.newRootLogger(Level.INFO)
    .add(builder.newAppenderRef("rolling")));

Configurator.initialize(builder.build());
```

</td><td>

```java
LoggerContext context =
    (LoggerContext) LoggerFactory.getILoggerFactory();

var encoder = new PatternLayoutEncoder();
encoder.setContext(context);
encoder.setPattern(
    "%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n");
encoder.start();

var policy =
    new SizeAndTimeBasedRollingPolicy<ILoggingEvent>();
policy.setContext(context);
policy.setFileNamePattern(
    "app-%d{yyyy-MM-dd}.%i.log.gz");
policy.setMaxFileSize(FileSize.valueOf("10MB"));
policy.setMaxHistory(7);

var appender = new RollingFileAppender<ILoggingEvent>();
appender.setContext(context);
appender.setName("rolling");
appender.setFile("app.log");
appender.setEncoder(encoder);
appender.setRollingPolicy(policy);
policy.setParent(appender);
policy.start();
appender.start();

var root = context.getLogger(Logger.ROOT_LOGGER_NAME);
root.setLevel(Level.INFO);
root.addAppender(appender);
```

</td></tr>
</table>

Roughly **2.5x** less code than Log4j2's builder API and **3x** less than Logback's.
Unlike both, nothing here needs a generics parameter naming the concrete logging
event type, a two-phase "construct, then call `.start()` in the right order" lifecycle,
or a `Configurator.initialize(...)` step separate from actually building the config.

### Simple properties configuration, including one-liner URIs

Log4j2 additionally supports a flat `log4j2.properties` format as an alternative to XML.
**Logback has no equivalent**: Joran (Logback's configuration engine) is XML or Groovy
only; there is no supported flat key/value format for anything beyond the bare
`SLF4JBridgeHandler`-style logger-level shortcuts some frameworks bolt on top.

The same rolling-file setup as a `log4j2.properties` file:

```properties
appender.rolling.type = RollingFile
appender.rolling.name = rolling
appender.rolling.fileName = app.log
appender.rolling.filePattern = app-%d{yyyy-MM-dd}-%i.log.gz
appender.rolling.layout.type = PatternLayout
appender.rolling.layout.pattern = %d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n
appender.rolling.policies.type = Policies
appender.rolling.policies.size.type = SizeBasedTriggeringPolicy
appender.rolling.policies.size.size = 10MB
appender.rolling.strategy.type = DefaultRolloverStrategy
appender.rolling.strategy.max = 7

rootLogger.level = INFO
rootLogger.appenderRef.rolling.ref = rolling
```

The same thing in Rainbow Gum uses one property, because the rolling output builder takes
its options as a URI query string:

```properties
logging.appenders=rolling
logging.appender.rolling.output=rolling:///app.log?maxFileSize=10485760&maxHistory=7
```

That is not a cherry-picked example; it is how every Rainbow Gum output/encoder is
configurable: a URI scheme picks the component, the query string is the same named
properties the programmatic builder exposes. A `file://` output, a `gelf://`/`logstash://`/
`ecs://` structured encoder, or a custom output your own code registers all follow the
same one-line-per-component shape.
