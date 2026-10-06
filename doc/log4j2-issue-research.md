# What Log4j2 users ask for and trip over (2022 to 2026)

Purpose: find feature gaps, API gaps, and risks Rainbow Gum should address before 1.0,
by looking at where Log4j2 users spend their reactions and comments.

## Method

- Window: issues created from 2022-10-01 to 2026-10-03. Log4Shell related issues are
  excluded.
- GitHub (`apache/logging-log4j2`, 717 issues in the window), ranked by reactions and by
  comment count through the public search API. Log4j2 moved its tracker from JIRA to GitHub
  partway through this window, so the Apache JIRA `LOG4J2` project (83 issues in the
  window) was also ranked by votes and watchers.
- Engagement is low across the board: the most reacted GitHub issue in the window has 8
  reactions and the most voted JIRA issue has 1 vote. Comment count turned out to be the
  stronger signal (long threads mean many affected users or a hard problem), so both are
  reported. Treat the ranking as indicative, not statistical.

## Headline

There is no runaway feature request. The engagement is concentrated in **reliability bugs**:
logging that hangs or stops, logger lookups that return `null` after an upgrade, and
context (MDC) data silently lost. Feature requests cluster around **OpenTelemetry,
virtual threads, native images and jlink, configuration diagnostics, and security
hardening by default**, all areas where Rainbow Gum is already positioned well, plus a few
real gaps.

## Most engaged issues

| Issue | Engagement | Kind | Summary |
|---|---|---|---|
| [#3399](https://github.com/apache/logging-log4j2/issues/3399) | 49 comments | bug | Intermittent deadlock or thread starvation on startup in `InternalLoggerRegistry` (2.24.3, Spring Boot 3.3) |
| [#2946](https://github.com/apache/logging-log4j2/issues/2946) | 36 comments, 5 reactions | bug | `ThreadContext.putAll` wipes the whole context, breaking `CloseableThreadContext` (2.24.0) |
| [#3100](https://github.com/apache/logging-log4j2/issues/3100) | 26 comments, open | bug | `StatusLogger` started printing internal startup messages users never asked for |
| [#3779](https://github.com/apache/logging-log4j2/issues/3779), [#4007](https://github.com/apache/logging-log4j2/issues/4007) | 25 and 21 comments | bug | 2.25.x upgrades: a property missing from the published parent pom, and Gradle compiler warnings from annotation dependencies |
| [#3904](https://github.com/apache/logging-log4j2/issues/3904) | 19 comments | bug | Log4j requires `java.desktop` (`java.beans`), breaking slim `jlink` runtimes |
| [LOG4J2-3619](https://issues.apache.org/jira/browse/LOG4J2-3619) | 18 comments, tied for top JIRA by votes and watchers (with LOG4J2-3622, virtual threads), open | bug | Async logging stops for good when a logged throwable is a `StackOverflowError`; every thread then blocks on the full ring buffer |
| [#3196](https://github.com/apache/logging-log4j2/issues/3196), [#3143](https://github.com/apache/logging-log4j2/issues/3143) | 17 and 8 comments | bug | `getLogger()` returns `null` after mixing api and core versions, or when GC runs at the wrong moment |
| [#2169](https://github.com/apache/logging-log4j2/issues/2169), [LOG4J2-3623](https://issues.apache.org/jira/browse/LOG4J2-3623) | 15 and 5 comments | bug | Async consumer stuck at 100% CPU; application unresponsive under load with the async logger |
| [#2655](https://github.com/apache/logging-log4j2/issues/2655) | 14 comments, open | bug | Provider initialization hangs indefinitely (OSGi, Apache POI) |
| [#2363](https://github.com/apache/logging-log4j2/issues/2363) | 14 comments | feature | Users migrating from Logback lose stack traces: wanted Logback's "last argument is the throwable" semantics |
| [#2852](https://github.com/apache/logging-log4j2/issues/2852) | 14 comments, open | feature | Load configuration from every file in a directory |
| [#3593](https://github.com/apache/logging-log4j2/issues/3593) | 14 comments, open | feature | Periodic sync so file timestamps update on network file systems (Azure file shares) |
| [#4058](https://github.com/apache/logging-log4j2/issues/4058) | 13 comments | feature | Log *where* configuration was found, not only where it was looked for |
| [#1532](https://github.com/apache/logging-log4j2/issues/1532) | 12 comments | feature | Replace `synchronized` on hot paths so virtual threads are not pinned |
| [#1539](https://github.com/apache/logging-log4j2/issues/1539), [#3771](https://github.com/apache/logging-log4j2/issues/3771) | 12 comments each | feature | GraalVM native image support and its friction |
| [#2447](https://github.com/apache/logging-log4j2/issues/2447) | 10 comments, open | bug | Thread contention on `OutputStreamManager.writeBytes` took an application down |
| [#3754](https://github.com/apache/logging-log4j2/issues/3754) | 8 reactions (most in window) | bug | 2.25.0 unusable from Gradle on Java 8 because an annotation dependency needs Java 11 |
| [#3770](https://github.com/apache/logging-log4j2/issues/3770) | 8 comments | bug | Spring Boot produced no logging output after upgrading to 2.25.0 |
| [#2033](https://github.com/apache/logging-log4j2/issues/2033) | 6 reactions, open | feature | OpenTelemetry Logging support |
| [#3068](https://github.com/apache/logging-log4j2/issues/3068) | 5 reactions | bug | Time based rolling rotates incorrectly on ext4 because of file timestamp caching |
| [#1813](https://github.com/apache/logging-log4j2/issues/1813) | 4 reactions, open | bug | Key values from SLF4J 2's `addKeyValue` are silently lost |
| [#4064](https://github.com/apache/logging-log4j2/issues/4064) | 4 reactions, open | feature | Make XML `XInclude` opt in for security hardening |
| [#1484](https://github.com/apache/logging-log4j2/issues/1484) | 7 comments | bug | Internal warnings written to standard out instead of standard error, corrupting piped output |
| [#1229](https://github.com/apache/logging-log4j2/issues/1229) | 2 reactions | feature | JMX should be disabled by default |
| [#1508](https://github.com/apache/logging-log4j2/issues/1508) | 11 comments | feature | Zstd compression for rolled files |

## Themes and what they mean for Rainbow Gum

### 1. Logging that hangs or stops (highest risk)

The most engaged threads are hangs: a logger registry deadlock (#3399), a provider
initialization hang (#2655), async consumers stuck (#2169, LOG4J2-3623), and async logging
stopping permanently because of an `Error` (LOG4J2-3619). Users experience these as their
whole application freezing, which is the worst failure a logging library can have.

Rainbow Gum status:

- **The default async publisher has the LOG4J2-3619 class of bug today.**
  `BlockingQueueAsyncLogPublisher`'s worker only catches `Exception`, so an `Error` from an
  appender ends the worker while it still reports running; once the queue fills, every
  logging thread blocks forever in `queue.put`. It can also deadlock if an output itself
  logs through Rainbow Gum while the queue is full. `OpusAsyncPublisher` (branch
  `feature/opus-async-publisher`) is designed against both and should replace it or its
  fixes should be ported **before 1.0**.
- Initialization goes through `RainbowGumHolder`'s lock with a reentrancy guard ("tried to
  log too early"). It deserves the same concurrency testing these Log4j2 threads show is
  needed: many threads calling `RainbowGum.of()` and obtaining loggers during startup.

### 2. Upgrades that break builds or return null loggers

Build breakage and warnings from published poms and annotation dependencies (#3754, #3779, #4007),
`null` loggers when api and implementation versions are mixed (#3196), and Spring Boot
losing all output after a minor upgrade (#3770).

Rainbow Gum status and actions:

- Core's module requires only `java.base`; annotation modules are `requires static`, which
  avoids the #3754 kind of problem. Keep checking the published poms of every module for
  compile scope dependencies that only exist for tooling.
- **No BOM is published** (there is no `rainbowgum-bom` artifact). Every Rainbow Gum module
  shares one version and mixing versions is not supported, so a BOM is the simplest
  protection against the #3196 class of breakage. Recommended for 1.0.
- The Spring Boot 3 and 4 integration test modules guard against #3770 style regressions;
  keep them in the release checklist.

### 3. Context data silently lost

`ThreadContext.putAll` wiping context (#2946, 36 comments) and SLF4J 2 `addKeyValue` data
dropped (#1813, still open).

Rainbow Gum status: `addKeyValue` is supported (`RainbowGumEventBuilder`, covered by
`RainbowGumEventBuilderTest`), and `rainbowgum-scopedkeyvalues` offers a `ScopedValue` based
alternative to MDC. The JUL bridge still does not carry MDC (its own
`// TODO fix key values aka MDC`), which matters for JUL heavy frameworks such as Quarkus
and Helidon.

### 4. File rolling edge cases

Time based rolling on ext4 (#3068), stopping at midnight, recovery after the active file
is deleted, and metadata not flushing on network file systems (#3593).

Rainbow Gum status: rolling is deliberately size based only, which avoids the time based
bug class, and recovery after failed rotation and reopen was recently added. Gaps:
no periodic `fsync` or metadata flush option (#3593), and only gzip compression (#1508
asks for zstd). Neither looks required for 1.0.

### 5. Diagnostics: too noisy, or silent when it matters

Unwanted internal messages (#3100), internal warnings on standard out (#1484), and wanting
to know which configuration file was actually used (#4058) or to load a directory of files
(#2852).

Rainbow Gum status: internal errors go to standard error through `MetaLog`; alerts and
metrics carry problems as data; `LogReporter` and debug mode show which property sources
were loaded. This is an area to advertise.

### 6. Requested platform and security features

| Request | Log4j2 | Rainbow Gum |
|---|---|---|
| OpenTelemetry logs (#2033) | open | `rainbowgum-otlp` on branch `feature/otlp`, needs review and merge |
| Virtual thread friendly locking (#1532) | changed over time | lock based appender types and a global reentrant lock option exist |
| GraalVM native (#1539) | supported with friction | supported, with a native image test module |
| Slim `jlink` runtimes (#3904) | needed `java.desktop` | core needs only `java.base` |
| XML hardening (#4064) | XInclude on by default | no XML configuration at all |
| JMX off by default (#1229) | requested | no JMX |
| Logback style throwable argument (#2363) | added as an option | to check: confirm a trailing throwable argument is always rendered |

## Suggested 1.0 checklist from this research

| Item | Status |
|---|---|
| Replace the default async publisher, or port its handling of `Error`, reentrant logging, and never throwing from `log()` | done: `BatchSwapAsyncLogPublisher` |
| Publish a BOM | decided against for now, see below |
| Startup concurrency test (many threads initializing and getting loggers at once) | done: `RainbowGumEntryPointConcurrencyTest`, `LogConfigConcurrentBindTest` |
| Review and merge the OTLP module | done: `rainbowgum-otlp` |
| Carry MDC through the JUL bridge | done: `JULBridge` reads `KeyValuesContributor.global()` |
| Confirm Logback compatible trailing throwable handling in the SLF4J facade | done: permanent test |

### Why no BOM yet

A BOM is imported with `<scope>import</scope>`, and Maven's dependency guide says the
import is replaced with "the effective list of dependencies in the specified POM's
`<dependencyManagement>` section". Properties are not imported. The problem is the word
effective: a BOM module that simply inherits the project's parent publishes everything
the parent manages along with our own artifacts.

For Rainbow Gum that is real. The root `pom.xml` manages 23 of our artifacts and 19 third
party ones, including `slf4j-api`, `log4j-api`, `jboss-logging`, `junit-jupiter`, `jansi`,
and `disruptor`. A naive BOM would pin those versions in every application that imports
it, which a logging library has no business doing.

The fix does not need the usual restructuring (an empty aggregator, a separate real
parent, and a separate BOM module that does not inherit it). Three smaller options exist:

1. The `flatten-maven-plugin` `bom` mode: the BOM module keeps inheriting the real parent
   for versions and release metadata, and the published POM has no parent and keeps its
   own `dependencyManagement` "as-is without resolving parent influences".
2. A BOM module with no parent that is still listed in the reactor, so release tooling
   sets its version; the cost is repeating the Central metadata (name, URL, licenses, SCM,
   developers) in that one POM.
3. Maven 4's `bom` packaging, which publishes a flattened consumer POM, once the build
   moves to Maven 4.

Without a BOM users set one property:

```xml
<properties>
    <rainbowgum.version>1.0.0</rainbowgum.version>
</properties>
```

That is fine here because every Rainbow Gum artifact is released together with the same
version. What a BOM would add is alignment of Rainbow Gum artifacts a user only gets
transitively, for example a third party library built against an older `rainbowgum-core`.
That is unlikely today, so the BOM can wait; if it is added, option 1 is the least work.

Sources: [Maven dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html),
[flatten:flatten](https://www.mojohaus.org/flatten-maven-plugin/flatten-mojo.html),
[What's new in Maven 4](https://maven.apache.org/whatsnewinmaven4.html).

Researched and written by Claude Opus 5.5.
