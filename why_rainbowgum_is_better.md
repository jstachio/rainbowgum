# Why Rainbow Gum is Better than X

Where X is one of the following JVM logging implementations:

* [Logback](https://logback.qos.ch/)
* [Log4j 2](https://logging.apache.org/log4j/2.x/)
* [Reload4j](https://reload4j.qos.ch/) (Log4j 1, still alive as a security-patched fork)

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

Of the major general purpose JVM logging implementations, Rainbow Gum's `rainbowgum-core`
is the only one that requires **just** `java.base` - nothing else. Logback and Log4j2 both
require the `java.xml` module (Logback pulls it in transitively even if you never touch
XML config yourself), and both bring their own separate facade concepts on top of what
SLF4J already provides. On a JDK 21+ `jlink`/GraalVM native image, that difference in
module graph is the difference between a minimal runtime and one that has to carry XML
parsing along for the ride.

## Fast

Real Spring Boot webapp benchmark, HTTP load, virtual and platform threads, plain and
structured (GELF) logging - see the
[`feature/webapp-benchmark`](https://github.com/jstachio/rainbowgum/tree/feature/webapp-benchmark)
branch (`benchmark/webapp`) for methodology and the driver code:

| scenario | Logback | Log4j2 | Rainbow Gum |
|---|---:|---:|---:|
| virtual threads | 25,554 req/s | 18,990 req/s | **26,324 req/s** |
| virtual threads + GELF | 25,797 req/s | 17,734 req/s | **27,450 req/s** |
| container/12-factor style (console-only, GELF) | 15,002 req/s | 19,639 req/s | **34,172 req/s** |
| container/12-factor + virtual threads | 21,587 req/s | 15,445 req/s | **25,572 req/s** |

The container/12-factor scenario - structured logging straight to stdout, no file output,
the way most people actually run a Spring Boot app in Kubernetes today - is Rainbow Gum's
best result, roughly **2x** both Logback and Log4j2, and also the closest thing to
"zero extra configuration" of the scenarios tested: it's exactly what Rainbow Gum's
native structured-logging support produces with nothing but a property.

(For completeness: on plain platform threads with no structured logging, Log4j2 still
leads there; that specific gap isn't closed.)

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

## Modular, GraalVM Native, and jlink friendly

* Log4j2, Reload4j, and Logback all require the `java.xml` module - Logback pulls it in
  transitively even for applications that never write a line of XML.
* Log4j2 and Reload4j (and Tiny Log) ship their own separate logging facade API on top of
  SLF4J that cannot be removed even if your application only ever calls SLF4J.
* Rainbow Gum's core has zero required dependencies beyond `java.base`, uses zero
  reflection other than the `ServiceLoader` (which is GraalVM native friendly and the
  preferred way to do pluggable components on modern JDKs), and needs no special
  configuration to work correctly under GraalVM native.

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
