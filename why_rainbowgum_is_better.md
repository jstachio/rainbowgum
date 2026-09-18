# Why Rainbow Gum is Better than X

Where X is one of the following JVM logging implementations:

* [Logback](https://logback.qos.ch/)
* [Log4j 2](https://logging.apache.org/log4j/2.x/)
* [Reload4j](https://reload4j.qos.ch/) (Log4j 1, still alive as a security-patched fork)
* [tinylog](https://tinylog.org/v2/)

This document (like [JStachio's own](https://github.com/jstachio/jstachio/blob/main/why_jstachio_is_better.md),
which it borrows its format from) is deliberately opinionated marketing - the
[user guide](https://jstach.io/rainbowgum/) is the actual documentation.

See also [error_messages_comparison.md](error_messages_comparison.md) - a companion
document comparing what each framework actually does (and says) when a component is
misconfigured, since "small and fast" doesn't matter much if a typo fails silently in
production.

## Small

| Library | Version | Jar size(s) | Sum | Notes |
| --- | --- | ---: | ---: | --- |
| **rainbowgum** (core + slf4j) | 0.11.0 | 366 + 63 KiB | **429 KiB** | Requires only `java.base` |
| logback (classic + core) | 1.6.3 | 286 + 635 KiB | 921 KiB | Requires `java.xml`, transitively |
| log4j2 (core + api + slf4j2 binding) | 3.0.0-beta2 | 1434 + 347 + 26 KiB | 1.76 MiB | Kitchen sink of features |
| tinylog (api + impl + slf4j binding) | 2.8.0 | 63 + 134 + 14 KiB | 211 KiB | Smallest jar - but see below |

Of the major general purpose JVM logging implementations, Rainbow Gum's `rainbowgum-core`
is the only one that requires **just** `java.base` - nothing else. Logback and Log4j2 both
require the `java.xml` module (Logback pulls it in transitively even if you never touch
XML config yourself), and both bring their own separate facade concepts on top of what
SLF4J already provides. On a JDK 21+ `jlink`/GraalVM native image, that difference in
module graph is the difference between a minimal runtime and one that has to carry XML
parsing along for the ride.

tinylog deserves real credit here: its codebase genuinely is lean - no XML configuration
engine, a small class count, `tinylog.properties`/system-property configuration only -
and by raw jar bytes it beats everyone in the table above, Rainbow Gum included. But
`tinylog-impl`'s own module descriptor marks `java.sql` and `java.naming` as
`requires static` ("optional"), while core pattern-formatting classes - not just the
JDBC writer those modules obviously belong to - import them directly:
[`DateToken`](https://github.com/tinylog-org/tinylog/blob/2934408bfa30a02cb241720211d391b88b18c61f/tinylog-impl/src/main/java/org/tinylog/pattern/DateToken.java#L16)
imports `java.sql.PreparedStatement`/`java.sql.Timestamp` at the top of the file, and it's
one of 28 classes in `tinylog-impl` referencing `java.sql` types (`jdeps -v` confirms it).
"Optional" is what the module descriptor claims; the bytecode says otherwise.

That is real, measured size on a `jlink` image someone actually cares enough to size-tune
- not an abstract dependency-graph complaint. On JDK 26
(`--strip-debug --no-header-files --no-man-pages --compress=zip-9`):

| Modules added | Image size |
| --- | ---: |
| `java.base` (what Rainbow Gum needs) | 40.70 MiB |
| + `java.management` (`tinylog-api`'s own hard requirement) | 41.36 MiB |
| + `java.sql` | 45.64 MiB |
| + `java.naming` (the JDBC writer's JNDI lookup) | 46.05 MiB |

Going from Rainbow Gum's `java.base`-only image to what a tinylog-based one actually needs
costs **5.35 MiB (+13%)** - over ten times the roughly 200 KiB the jar-size table above
credited tinylog for saving.

tinylog's jar is also small partly because it has no programmatic configuration API at
all: `tinylog-api` ships property/system-property/JNDI value resolvers and nothing
resembling Logback's, Log4j2's, or Rainbow Gum's builder API. That's a legitimate design
choice for tinylog's target use case, not a size optimization anyone else is leaving on
the table - it's a capability Rainbow Gum has that tinylog's jar-size numbers above simply
don't have to pay for.

## Fast

Real Spring Boot webapp benchmark, HTTP load, virtual and platform threads, plain and
structured (GELF) logging - see the
[`feature/webapp-benchmark`](https://github.com/jstachio/rainbowgum/tree/feature/webapp-benchmark)
branch (`benchmark/webapp`) for methodology and the driver code:

| scenario | Rainbow Gum | Logback | Log4j2 |
|---|---:|---:|---:|
| virtual threads | **26,324 req/s** | 25,554 req/s | 18,990 req/s |
| virtual threads + GELF | **27,450 req/s** | 25,797 req/s | 17,734 req/s |
| container/12-factor style (console-only, GELF) | **34,172 req/s** | 15,002 req/s | 19,639 req/s |
| container/12-factor + virtual threads | **25,572 req/s** | 21,587 req/s | 15,445 req/s |

The container/12-factor scenario - structured logging straight to stdout, no file output,
the way most people actually run a Spring Boot app in Kubernetes today - is Rainbow Gum's
best result, roughly **2x** both Logback and Log4j2, and also the closest thing to
"zero extra configuration" of the scenarios tested: it's exactly what Rainbow Gum's
native structured-logging support produces with nothing but a property.

Real GraalVM native-image benchmark - plain `com.sun.net.httpserver.HttpServer`, virtual
threads, TTLL and GELF logging - see the
[`feature/graalvm-native-benchmark`](https://github.com/jstachio/rainbowgum/tree/feature/graalvm-native-benchmark)
branch (`benchmark/native`, `0-11-2-RESULTS.md`) for methodology and the driver code:

| format | Rainbow Gum | Logback | Log4j2 |
|---|---:|---:|---:|
| TTLL | **91,773 req/s** | 71,939 req/s | 85,066 req/s |
| GELF | **92,264 req/s** | 56,772 req/s | 82,037 req/s |

Getting there takes one explicit choice: `SYNCHRONIZED_THREAD_LOCAL_BUFFER` (see
"Configurable locking strategy" below) instead of the default locking strategy - under
native-image specifically it is a real, repeatable +29-32% over the default, the
difference between beating Log4j2 by roughly 8-12% and trailing it by roughly 15-16%.
It is not a universal win: on plain HotSpot the effect reverses and the default
locking strategy is the better choice, which is exactly why it stays opt-in rather than
becoming the new default.

## Configurable locking strategy

Locking is not a footnote in logging performance - under real concurrent load, contention
on the append path is often the actual bottleneck, not encoding cost. Rainbow Gum makes
the locking/buffering strategy an explicit, per-appender choice
(`logging.appender.<name>.type`, or `LogAppender.Builder#appenderType` programmatically),
and that choice is independent of which encoder or output the appender uses - any encoder
(pattern, GELF, ECS, Logstash, a custom one) paired with any output (console, file, a
custom one) can pick whichever of the four strategies fits:

* `LOCK_THREAD_LOCAL_BUFFER` (default) - encode into a per-thread reused buffer outside
  the lock, hold the lock only for the final write to the output.
* `SYNCHRONIZED_THREAD_LOCAL_BUFFER` - the same shape, but the write is guarded by a
  plain `synchronized` block instead of a `ReentrantLock`.
* `LOCK_NEW_BUFFER` - the same low-contention shape with no `ThreadLocal` at all, for
  deployments that want a hard guarantee against it, at the cost of a fresh buffer
  allocation per event.
* `REUSE_BUFFER` - a single shared buffer, held under lock for the entire
  encode-then-write critical section.

Logback has exactly one locking strategy across every appender it ships - a single lock
held for the whole encode-then-write critical section, the same shape as Rainbow Gum's
`REUSE_BUFFER`. It is not a choice you can make; it is the only implementation Logback
has. Rainbow Gum treats that as one of four options, not the only one.

## Discarding a disabled level costs as close to zero as possible

Most frameworks' SLF4J bindings dispatch every call through a single logger
implementation that checks the current level against an `if` condition -
`isDebugEnabled()` (or the equivalent internal check inside `debug(...)` itself) still
runs a comparison and a branch on every single disabled call, no matter how "cheap" that
check is. Rainbow Gum's SLF4J logger instead resolves the effective level once, when the
logger is obtained, to one of five concrete per-level classes
(`ErrorLogger`/`WarnLogger`/`InfoLogger`/`DebugLogger`/`TraceLogger`, generated - not
hand-written, so there is no per-level combination anyone could forget). For any level
below the one that class represents, the method body is empty:

```java
// a Logger resolved at WARN - this is the entire method, not an excerpt
@Override
public void info(String msg) {
}
```

Calling `.info(...)`/`.debug(...)`/`.trace(...)` on a logger configured at `WARN` does not
evaluate a condition at all - it calls a method with nothing in it, which the JIT can (and
does) treat as free. `isInfoEnabled()` still correctly returns `false` (SLF4J's contract
requires the method to exist), but the actual logging call itself never consults it,
because which behavior to run was already decided once, not on every call.

Credit where due: tinylog's SLF4J binding is close in spirit, if not in mechanism. Every
disabled-level call there is gated by a single `static final boolean` field
(`MINIMUM_DEFAULT_LEVEL_COVERS_DEBUG` and friends), computed once when the class loads -
not a per-call comparison against some mutable level. Once the JIT (or GraalVM
native-image's own build-time constant folding) sees that field can never change, the
`if` and everything it guards collapses away just like Rainbow Gum's empty method does.
The real difference is scope: tinylog's flag is one global minimum across the entire JVM,
so it only stays free while nothing anywhere in the app needs a lower level - configure
even one package to `DEBUG` while the rest of the app sits at `INFO`, and that flag flips
to covering `DEBUG`, so *every* `debug()` call app-wide (not just the one package that
needed it) falls through to a real per-tag runtime check. Rainbow Gum's per-logger class
selection has no such blast radius: each logger's cost depends only on its own resolved
level, however many other loggers elsewhere are configured differently.

## Built-in operational metrics, no extra dependency

Rainbow Gum tracks a small, well known set of counters about the logging system itself -
events dropped, encoder buffer trims, failed writes - out of the box, with zero required
dependency beyond `java.base`:

```java
var counters = config.metrics().counters();
// [Counter[name=events.dropped, level=ERROR, count=3], ...]
```

Each is a plain `LongAdder`, incremented with no per-call allocation and no listener
dispatch - cheap enough to leave on unconditionally, not something you opt into only in
a profiling build. Neither Logback nor Log4j2 ship anything like this: finding out how
many events an appender has silently dropped, or how often the encoder's buffer had to
shrink back down, means wiring up Micrometer or JMX yourself first - Rainbow Gum answers
that question with a method call.

## The correct external log rotation, not "copy truncate"

The classic Unix daemon convention for external log rotation is: an external tool (e.g.
`logrotate`) moves the log file aside, then signals the running process to close and
reopen it by its original name - releasing the moved file's descriptor cleanly, with no
dropped or corrupted events. The alternative most JVM logging frameworks push you toward
instead - `logrotate`'s `copytruncate` mode, or the framework's own internal size/time
based rolling policy - either truncates the file out from under a process that still has
it open (a real event-loss/corruption race) or takes rotation out of `logrotate`'s hands
entirely.

Rainbow Gum's `LogOutputRegistry#reopen()` does the correct move-then-reopen dance
against any output, triggerable over a plain HTTP endpoint (no extra dependency, a few
lines of application code) or, on Linux/Docker, directly from a logrotate `postrotate`
script via a real Unix signal - `kill -USR1 $(cat app.pid)`, no open port and no
application code at all - through the optional `rainbowgum-signal` module. Neither
Logback nor Log4j2 offers anything like it: both expect their own internal rolling
policy to be the only thing touching the file, with `copytruncate` as the sole fallback
if you insist on using `logrotate` alongside them anyway.

## Modular, GraalVM Native, and jlink friendly

* Log4j2, Reload4j, and Logback all require the `java.xml` module - Logback pulls it in
  transitively even for applications that never write a line of XML.
* Log4j2 and Reload4j (and Tiny Log) ship their own separate logging facade API on top of
  SLF4J that cannot be removed even if your application only ever calls SLF4J.
* Rainbow Gum's core has zero required dependencies beyond `java.base`, uses zero
  reflection other than the `ServiceLoader` (which is GraalVM native friendly and the
  preferred way to do pluggable components on modern JDKs), and needs no special
  configuration to work correctly under GraalVM native.

None of the other frameworks in this comparison has strong, native-first GraalVM support
today, though it's worth being fair about where each one actually stands:

* **Logback** ships no GraalVM reachability metadata of its own - what exists comes from
  the third-party [`graalvm-reachability-metadata`](https://github.com/oracle/graalvm-reachability-metadata)
  repository, not `logback.qos.ch`. It is, however, doing real work in this direction:
  [`logback-tyler`](https://github.com/qos-ch/logback-tyler) translates a `logback.xml`
  file into a plain Java class (`TylerConfigurator`) that configures Logback with no XML
  parser and no reflection at all - the same two things Rainbow Gum avoids by design from
  the start. It is a genuinely good sign for Logback's native-image future, even though it
  is a still-new, opt-in translation step bolted onto an XML-first architecture rather
  than something built in from the ground up.
* **Log4j2** does have an official GraalVM page (`logging.apache.org/log4j/2.x/graalvm.html`),
  which is more first-party documentation than Logback, Reload4j, or tinylog have. It is
  brief, though - a page of "here's what's supported, here are links to GraalVM's own
  docs" rather than a worked, CI-verified example.
* **Reload4j and tinylog** have no dedicated native-image documentation at all.

Rainbow Gum's own GraalVM Native Image documentation (in the
[user guide](https://jstach.io/rainbowgum/)) goes further than any of these: it explains,
with root causes, the specific JDK-internal
code paths (`java.time`/`java.util.Locale` formatting, `TrustStoreManagerFeature`) that
incidentally call `System.getLogger(...)` during a native-image build and why that is
safe, and `test/rainbowgum-test-native` is a real application built to a native
executable and run as a black-box smoke test in CI on every change - not just a claim
that it works.

## Programmatic configuration is dramatically less verbose

All three frameworks support programmatic (no XML/properties file) configuration. Here is
the same target configuration - a rolling file appender, a pattern with a timestamp/level/
logger/message, 10MB rotation, 7 files retained - written natively in each, actually
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
                b.maxFileSize(10 * 1024 * 1024);
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

Roughly **2.5x** less code than Log4j2's builder API and **3x** less than Logback's -
and unlike both, nothing here needs a generics parameter naming the concrete logging
event type, a two-phase "construct, then call `.start()` in the right order" lifecycle,
or a `Configurator.initialize(...)` step separate from actually building the config.

## Simple properties configuration - including one-liner URIs

Log4j2 additionally supports a flat `log4j2.properties` format as an alternative to XML.
**Logback has no equivalent** - Joran (Logback's configuration engine) is XML or Groovy
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

The same thing in Rainbow Gum - one property, because the rolling output builder takes
its options as a URI query string:

```properties
logging.appenders=rolling
logging.appender.rolling.output=rolling:///app.log?maxFileSize=10485760&maxHistory=7
```

That is not a cherry-picked example - it is how every Rainbow Gum output/encoder is
configurable: a URI scheme picks the component, the query string is the same named
properties the programmatic builder exposes. A `file://` output, a `gelf://`/`logstash://`/
`ecs://` structured encoder, or a custom output your own code registers all follow the
same one-line-per-component shape.

## Type and null safety

Rainbow Gum's entire public API is annotated with [JSpecify](https://jspecify.dev/)
`@Nullable`/`@NullMarked`, checked in CI with both CheckerFramework's Nullness Checker and
NullAway - a `@Nullable`-returning method genuinely means "can return null," not "might,
depending on an implementation detail the annotations don't capture." Neither Logback nor
Log4j2's public API carries any nullability annotations at all.

## Test coverage

Rainbow Gum sits at **92% test coverage** (aggregated across every module, tracked
continuously - see the [Codecov badge](https://app.codecov.io/gh/jstachio/rainbowgum)),
including end-to-end golden-string tests of what happens when a component is
misconfigured, not just the happy path. See
[error_messages_comparison.md](error_messages_comparison.md) for what that buys you in
practice.

Credit where due again: tinylog does the same thing, and does it well - its
[Codecov badge](https://app.codecov.io/gh/tinylog-org/tinylog/tree/v2.8) shows **94%**,
tracked continuously the same way Rainbow Gum's is. Logback's and Log4j2's actual
coverage, by contrast, is not something you can just go look up: neither publishes a
number anywhere - the obvious place to check, Codecov, returns "unknown" for both
([logback](https://codecov.io/gh/qos-ch/logback), [log4j2](https://codecov.io/gh/apache/logging-log4j2)),
meaning no coverage data has ever been uploaded there. That doesn't mean they're
untested - both have large, long-running test suites - it means there is no public
number to compare against, favorable or not, the way there is for Rainbow Gum and
tinylog.

To be clear, 92% is not a number Rainbow Gum is chasing toward 100 for its own sake.
Line coverage measures which lines *ran* during a test, not which lines were actually
*verified* - a test that only exists to touch a line pads the percentage without proving
anything, and 100% can just as easily mean "we wrote a trivial test for every line"
as "we deleted the dead code that shouldn't have been there in the first place."
Rainbow Gum prefers real, end-to-end tests - including parameterized ones that sweep many
input shapes through the same real assertion, like `ConfigFailureTest`'s enum-driven
cases - over hand-crafted unit tests aimed at specific lines. The percentage is a
byproduct of testing real behavior thoroughly, not the target itself.

## Smaller security surface

* No expression language. Log4j2's JNDI-lookup-capable expression language in log
  messages is exactly what made [Log4Shell](https://en.wikipedia.org/wiki/Log4Shell)
  (CVE-2021-44228) possible - one of the most severe RCEs in the history of the Java
  ecosystem, in a *logging* library. Rainbow Gum has no expression language, and never
  interpolates untrusted string content as anything other than a string.
* No XML/YAML configuration parser in `core` at all, which means no XML entity expansion
  attack surface, no "wait, which XML parser features are enabled by default" question to
  answer.
* Zero reflection other than `ServiceLoader`.

None of this means Logback or Log4j2 are insecure today - both responded to Log4Shell and
have hardened considerably since. It means Rainbow Gum structurally doesn't have the
*category* of surface area that produced it in the first place.
