# Roadmap to 1.0.0

The next release off this work is 0.11.0, not 1.0.0 - the items below are what's
still outstanding before an eventual 1.0.0, not a blocker list for 0.11.0.

Deliberately deferred to the release after 0.11.0 (planned to be mostly a cleanup
release):

- **JSpecify migration** (item 2 below) - not started this cycle.
- **spring-javaformat-maven-plugin version bump** - a Dependabot PR was left open on
  purpose; a version bump there could shift formatting rules across the whole codebase
  and warrants its own dedicated pass, not a drive-by alongside other work.
- **`rainbowgum-apt`'s moditect "already modular" build flakiness** (surfaces as a
  scoped `-pl X -am install` failing after a prior build already ran moditect against
  the same jar, without an intervening `clean` - a real annoyance for local dev loops
  and any script that builds a module subset more than once, e.g.
  `benchmark/webapp`'s `run-all.sh`/`run-k8s.sh`) - Adam has a known fix for this from
  other projects, planned for next cycle rather than a one-off workaround here.

## 1. Replace pull-style status with separate alerts and metrics systems

The old design (`LogResponse.Status`, one `status()` snapshot per component) is being
replaced by **two separate pushed systems**, not a single unified status API:

- An **alerts** system - event-driven, for the "this just broke" / "this got a little
  worse" case `MetaLog`'s push-style error logging used to only cover informally
  (stderr, no history). **First cut landed on `feature/log-alerts` (not yet merged):**
  `LogAlerts`, obtained per-instance via `LogConfig#alerts()` - a bounded drop-oldest
  ring buffer (`dump()`/`stats()`/`clear()`) plus `addListener(Listener)` for push
  subscribers (metrics bridges, ops/paging integrations), superseding the earlier
  on-hold `feature/log-status-manager`/`LogStatusReporter` direction. `MetaLog` itself
  is now package-private and forwards to the bound `RainbowGum`'s `LogAlerts` when one
  exists, falling back to its old direct-stderr behavior otherwise. Deliberately
  **error-only for now** (every entry is constructed at `Level.ERROR`) - both Log4j2's
  `StatusLogger` and Logback's `StatusManager` carry the full level range (INFO/WARN/
  ERROR), so widening `LogAlerts` the same way (using `System.Logger.Level` directly,
  plus possibly some sort of small `Component`/category enum with a parent interface
  so an alert can say *what kind of thing* broke, not just its level) is the planned
  next step - **but stay disciplined about scope**: that's still just a tagged event
  ring buffer, not an invitation to grow a full metrics system. Counters/gauges (queue
  depth, buffer-resize counts, dropped-event counts) belong to the separate metrics
  system below, not to `LogAlerts`.
- A **metrics** system - numeric/gauge-style data (queue depth vs capacity, dropped
  event counts, etc.) that a snapshot-per-component `status()` call was a poor fit for
  in the first place.

`LogOutput.status()` and `LogPublisher.status()` (the old per-component pull-style
health check) have already been removed, including `BlockingQueueAsyncLogPublisher`'s
only real override (`QueueStatus` - queue depth vs capacity, exactly the kind of data
the future metrics system should own). `LogAppender.status()`/
`LogPublisherRegistry.status()` still exist but now always report `StandardStatus.OK`
unconditionally, since there is nothing left to delegate to - not a regression, just
scaffolding waiting on whichever of the two new systems replaces it.

To land before 1.0:

- [x] **Two-pass config**: `LogConfig.Builder.build()` builds the global level resolver
      from `logProperties` as it stood *before* any `Configurator` runs, then only
      afterward calls `RainbowGumServiceProvider.Configurator.runConfigurators(...)` - so
      a configurator that contributes additional property sources (or otherwise changes
      what the level resolver should have seen) is invisible to it. Flagged inline where
      `runConfigurators` is called in `LogConfig.java`. Proper fix: run configurators
      first, then rebuild whatever is purely derived from properties (starting with the
      level resolver) a second time against the now-fully-configured `LogConfig`, rather
      than building it once, early, and never revisiting it. Deliberately not done as
      part of the `LevelResolver` alerting work above - a bigger change to `LogConfig`'s
      build lifecycle than that warranted on its own.
      (ADAM because this is internal and not exposed its not a big deal for 1.0)
- [ ] A third, still-unaddressed facet the old `status()` API used to partly cover:
      a **static configuration report** - not alerts (event-driven) or metrics
      (gauges), just "what actually got wired up." With `REUSE_BUFFER`/
      `LOCK_THREAD_LOCAL_BUFFER`/`SYNCHRONIZED_THREAD_LOCAL_BUFFER`, the JDK-version-sniffed
      default, the global `GLOBAL_APPENDER_LOCK_PROPERTY` override, and
      (possibly) a future Spring Boot virtual-thread sniff all in play,
      there is no way today to tell *which concrete appender class* actually got
      selected for a given route/output short of reading code or attaching a
      debugger. The raw introspection already exists -
      `ServiceRegistry.find(LogAppender.class)`/`forEach(...)` returns every
      registered appender by name, and `CompositeLogAppender.components()` exposes
      the per-output appenders under a route that resolved to a composite - what's
      missing is a small utility that walks that and renders a human-readable
      summary (name, concrete class, flags, output, encoder). Where this should live
      (a plain utility method vs. a Spring actuator-style endpoint vs. something
      printed at startup) is still undecided.
      (ADAM I think we just have some pump out a List of LogEvent at the top level like LogRouter).


## 4. Improve the LogProperty API and friends; at least add test coverage

Concrete issue found this cycle, not yet fixed: there are **two different**
URI-normalization implementations. `LogOutputRegistry`'s private `normalize(URI)` only
special-cases `./`-prefixed relative paths; `DefaultLogProviderRef.normalize(URI)` (used
by `LogPublisherRegistry` and `LogEncoderRegistry`, but *not* by `LogOutputRegistry`)
used to also handle bare `/`-absolute paths via a `name://` trick, letting a scheme-less
value reference another named component's config (e.g.
`logging.appender.default.encoder=name:///somename` pointing at
`logging.encoder.somename.*`). That trick was removed - it was never actually wired up
to look anything up (nothing registered a provider for the `name` scheme, so using it
just traded one unresolvable-URI failure for another), and it read as confusing
scaffolding, especially next to the unrelated `logging.file.name` property. See
"Reuse-by-name config" below for whether it's worth building for real. The two
normalize implementations otherwise still differ on `./`-relative paths - `LogOutputRegistry`
converts those to real `file:` URIs (appropriate, since only outputs are file-like);
`DefaultLogProviderRef` now just fails clearly instead of guessing. Worth auditing and
unifying.

- [ ] Reconcile `LogOutputRegistry.normalize()` and `DefaultLogProviderRef.normalize()`
      into one implementation (or a clearly documented reason they must differ).
      (ADAM I think this is done?)
- [ ] **Reuse-by-name config**: decide whether `name:///somename`-style references
      (letting one property block, e.g. `logging.encoder.somename.*`, be reused from
      another, e.g. `logging.appender.default.encoder=name:///somename`) are actually
      worth building. The scaffolding for this (`LogOutputRegistry.NAMED_OUTPUT_SCHEME`,
      the `name://` normalization trick) was removed since it was never implemented -
      using it would only ever throw `NotFoundException`. If revisited, needs a real
      design (which registries support it, how it interacts with `{name}` key
      parameters) rather than reintroducing dead scaffolding.
      (ADAM: Lets table this but lets reserve the "name" schema. 
      Dot no allow it or "null" or "default" as a schema registered).
- [ ] **Logger name lookup on `LogProperties` (something like `findLoggerName`)**,
      needed before 1.0. The Spring Boot style per logger keys break both the leaf
      rule and the naming rules in the `LogProperties` javadoc. The part after the
      prefix is an opaque logger name, not more key path. There are four such
      namespaces (the same ones `UnusedKeyCheck.isIgnored` skips):
      - `logging.level.<logger>` (`LEVEL_PREFIX`)
      - `logging.route.<route>.level.<logger>` (`ROUTE_LEVEL_PREFIX`)
      - `logging.change.logger.<logger>` (`CHANGE_LOGGER_PREFIX`)
      - `logging.caller.logger.<logger>` (`CALLER_LOGGER_PREFIX`)

      Today a source only ever sees the flat key, so a nested format has to hard
      code these prefixes to read them. TOML is the clearest case (checked against
      the TOML 1.0 spec): a dotted key defines a table for every part before the
      last, and a key that already holds a value cannot also become a table. So:
      - `logging.level = "info"` next to `logging.level.com.example = "debug"` is
        invalid TOML. The root level needs a quoted `"logging.level"` key or a name
        such as Spring Boot's `root`; Rainbow Gum has no `root` alias today.
      - `com.example = "info"` next to `com.example.sub = "debug"` under
        `[logging.level]` is invalid for the same reason. Logger names must be
        quoted keys (`"com.example" = "info"`), so the source has to know that the
        quoted key is a single logger name and not split it.
      - Names like `tc.floci/floci-gcp@sha256:405c128b...` (#752) only work as
        quoted keys anyway.

      YAML and nested maps (Spring's YAML uses `logging.level.root`) have the same
      shape problem. A lookup that takes the prefix and logger name separately (for
      example `findLoggerName(prefix, loggerName)`, defaulting to
      `concatKey(prefix, loggerName)` for flat sources) would let a nested source
      find `[logging.level]."com.example"` directly. Today's internal callers would
      move onto it: `ConfigLevelResolver.levelOrNull` and
      `AbstractChangePublisher.findForLogger`, which use
      `DefaultLogProperty.ofLoggerKey` since #752. Also decide whether to add a
      `root` alias for the root logger.

## 5. Whatever else before 1.0.0

- [ ] **rainbowgum-tomcat throughput regression**: a reproducible ~15-20% regression
      (default/GELF scenarios) survived three separate rule-out investigations (raw-JUL
      bypass instrumentation, JFR CPU/allocation profiling, exhaustive reflective-
      construction counting) without a root cause. Needs a clean, non-shared benchmark
      environment to chase further - or, failing that, a documented known-issue before
      shipping 1.0 with Tomcat integration included.
- [ ] **Draft GitHub issue for spring-projects/spring-boot: `LoggingSystem` has no
      way to signal its own health, only logger level state.** Not filed yet - draft
      below, written to match their issue template ("describe the problem you're
      solving", no separate enhancement template, no Discussions tab so it goes in
      as a normal issue). Checked prior art first: #43384 → #43575 → #43822 → #43931
      already wired a `SystemStatusListener` (`OnConsoleStatusListener`) so Logback's
      own internal status reliably reaches the console - genuine progress, but
      Logback-only and console-only (nothing retained, nothing queryable via
      `LoggingSystem` or Actuator). That gap is what this targets. Doubles as a
      natural way to put RainbowGum in front of the Spring Boot maintainers, since
      the "related prior art" section below is us.

      > **Title:** No way to query/observe internal logging system health (only
      > console output via #43575/#43931)
      >
      > **Problem**
      >
      > `LoggingSystem` (and the Actuator `LoggersEndpoint` built on top of it) only
      > exposes *logger level* state - `getLoggerConfigurations()`/
      > `getLoggerConfiguration()`/`setLogLevel()`. There's no way, at runtime or via
      > Actuator, to find out whether the logging system itself is healthy: did an
      > appender fail to write, did a rolling policy break, did an async queue start
      > dropping events. If you depend on log delivery for anything (audit logging,
      > log shipping to a collector, compliance), that's currently a blind spot - the
      > only signal is whatever got printed to stderr at the moment it happened, and
      > only if someone happened to be watching.
      >
      > #43384 → #43575 → #43822 → #43931 already made real progress here for Logback
      > specifically, by wiring a `SystemStatusListener` (`OnConsoleStatusListener`)
      > so Logback's own internal status events reliably reach the console. That's a
      > genuine improvement, but it's:
      > - Logback-only - Log4j2 has an equivalent mechanism (`StatusLogger`,
      >   `ErrorHandler`) that isn't wired up the same way, and other `LoggingSystem`
      >   implementations have no equivalent at all.
      > - Console-only - nothing is retained after the fact, and nothing is queryable
      >   through `LoggingSystem` or Actuator. If you weren't tailing stdout at the
      >   exact moment, the information is gone.
      >
      > **Proposal (open to whatever shape maintainers prefer)**
      >
      > Could `LoggingSystem` grow a small, optional extension point for this -
      > something like a bounded, recent history of internal logging errors
      > (level/message/throwable/timestamp), default-implemented as empty so it's
      > non-breaking? Each implementation could back it however fits its underlying
      > framework (Logback's `StatusManager`, Log4j2's `StatusLogger`, etc.), and
      > Actuator's `LoggersEndpoint` (or a small companion endpoint) could then expose
      > it read-only, the same way it already exposes level configuration.
      >
      > **Related prior art from outside Spring Boot**
      >
      > For context: I maintain RainbowGum (a small SLF4J-compatible logging
      > framework, github.com/jstachio/rainbowgum, with its own `LoggingSystem`
      > integration for Spring Boot 3/4) and ran into exactly this gap building that
      > integration. We ended up adding `LogAlerts` - an instance-scoped (not static)
      > interface with a small ring buffer (`dump()`/`stats()`) plus listener
      > registration, obtained from our own config object - explicitly modeled after
      > comparing Logback's `StatusManager` (capped history + listener support) and
      > Log4j2's `StatusLogger` (which actually deprecated its own pull-based history
      > API in 2.23 in favor of listener registration). Happy to share more detail or
      > help prototype if there's interest in something like this for `LoggingSystem`
      > itself.

- [ ] **Validate `maxBufferSize`/`initialBufferSize` on `LogEncoder.Builder`**: for
      `DirectByteBufferBuffer` specifically (the `WriteMethod.BYTES`/`BYTE_BUFFER` buffer
      most outputs get), `isOversized()` sums all three backing buffers'
      capacities (`stringBuilder` + `charBuffer` + `byteBuffer`), and a trim resets
      `charBuffer`/`byteBuffer` back to `initialByteCapacity`, not down to near-zero the
      way `StringBuilderBuffer`'s single-`StringBuilder` trim does. If `maxBufferSize` is
      set at or below roughly `2 * initialBufferSize`, that reset floor alone stays
      "oversized," so every subsequent `clear()` trims again, forever, incrementing
      `BUFFER_TRIMMED_METRIC` on every single append once triggered - not a growth signal
      anymore, just noise. `DirectByteBufferBuffer`'s constructor javadoc already warns
      about this ("a low maxBufferSize can make isOversized() unconditionally true from
      the very first event"), but nothing actually validates it - `LogEncoder.Builder`
      should reject (or at least warn on) a `maxBufferSize` too close to
      `initialBufferSize` instead of silently producing this behavior. Defaults are not
      affected: `maxBufferSize` defaults to `-1` (disabled entirely) and
      `initialBufferSize` defaults to `DirectByteBufferBuffer.DEFAULT_INITIAL_BYTE_CAPACITY`
      (8192) - this only bites a caller who explicitly opts into a small `maxBufferSize`
      without sizing `initialBufferSize` accordingly, which is exactly what happened
      building `rainbowgum-test-kitchensink`'s `LogReporterKitchenSinkTest` diagnostics
      route on `explore/static-configuration-report`.
- [ ] **Facades and the global change push**: the System.Logger finder, JBoss Logging
      provider and JUL LogManager now follow the bound gum through
      `RainbowGum.onGlobalStateChange` (`GlobalRainbowGum`, one copy per module) instead of
      calling `RainbowGum.getOrNull()` when they create a logger,
      so loggers created before Rainbow Gum starts pick up `logging.messageFormat.*`.
      Left to decide:
  - [x] Binding and clear events share the ordered publisher through
        `onGlobalStateChange(Consumer<Optional<RainbowGum>>)`. Facade holders and the
        SLF4J factory release their current gum on clear. The original `onGlobalChange`
        remains binding-only for compatibility. See [the design](doc/global-state-change.md).
  - [ ] Whether `GlobalRainbowGum` should move to core as public API instead of three
        copies.
  - [ ] Events queued before startup keep the formatter they were created with.
  - [x] `getOrNull()` now reads the volatile snapshot directly. The lifecycle lock
        still serializes provisioning and router changes; a read lock cannot keep a
        returned gum alive after the call. Global notifications are queued in binding
        order and delivered without holding either the lifecycle lock or the queue
        monitor. See [the review](doc/review-global-change-push.md).
- [ ] **`console` as a scheme alias for `stdout`**: `LogOutput.STDOUT_SCHEME`/
      `STDERR_SCHEME` (`LogOutput.java`) are the only registered console output
      schemes today - `logging.appender.myapp.output=console` currently just fails
      with `NotFoundException`. Logback/Spring Boot users reach for "console" by
      habit (`ConsoleAppender`), so registering it in `LogOutputRegistry`'s
      `StandardLogOutputProvider` as a plain alias resolving to the same stdout
      output `STDOUT_SCHEME` does would be a small, low-risk win. Surfaced while
      adding the `doc/overview.html` "Console" output subsection.
      (ADAM: Is this even worth doing given 99/100 output defaults to stdout?)

## 6. Reconsider depth-based caller-info tracking (SLF4J 3.0 research)

Adam's hunch, prompted by noticing "CallerData" work upstream: `rainbowgum-slf4j`'s
whole caller-info mechanism - `LoggerDecoratorService.DepthAwareLogger`/
`DepthAwareEventBuilder`, `withDepth(int)`, `setDepth(int)`, `AbstractFilteringLogger`'s
hand-verified `DEPTH` constant, `RainbowGumEventBuilder`'s `DEPTH_DELTA` - is brittle:
every wrapper layer has to know exactly how many stack frames it adds and get that
count right, enforced only by tests (`AbstractFilteringLoggerTest`), with no compiler
help. This is exactly the scenario `LoggerDecoratorService` is designed to invite -
arbitrary third-party decorators wrapping the logger an arbitrary number of times - so
it's the worst case for hand-counted depth. Tinylog's design was the original draw
toward depth-counting; the reasoning for reconsidering it: caller-info capture is
already dominated by the cost of the stack walk itself, so precisely-counted-depth
saves very little on top of that "you're already slow" baseline - not worth the
brittleness it buys.

**Findings from researching SLF4J's own 3.0-line work (master branch vs `v_2.0.19`,
scoped to `slf4j-api/` only - the repo has other non-facade modules that are noise for
this)**:

- The 2 -> 3 major bump is primarily about a **Java 11 baseline** (up from Java 8), not
  CallerData - confirmed via commit `ff224725` ("given the bump to JDK 11, bump the
  major version to 3 instead of 2"). Two parallel lines exist right now:
  `branch_2.1.x` (`2.1.0-alpha2-SNAPSHOT`, Java 8, incremental) vs `master`
  (`3.0.0-rc0-SNAPSHOT`, Java 11+, where CallerData actually landed). Both are
  in-flux, pre-RC, dated to right around when this was researched (2026-09-17) - not
  a stable target to design against, a direction to watch.
- New `org.slf4j.helpers.CallerData` (explicitly ported from
  `ch.qos.logback.classic.spi.CallerData`) does content-based stack-boundary
  detection: walk a captured `Throwable`'s stack, skip frames matching a given
  boundary class/an internal fixed list of `org.slf4j.*` classes/an optional
  caller-supplied package-prefix list, and the first non-matching frame is the real
  caller. Wired in as `default` methods (`LoggingEventBuilder.withCallerData(int)`,
  `LoggingEvent.getCallerData()`) - fully binary/source compatible, nothing existing
  needs to change to keep compiling against it.
- **`org.slf4j.spi.CallerBoundaryAware` (`void setCallerBoundary(String fqcn)`)
  already ships in the slf4j-api version we already depend on (2.0.19, confirmed by
  decompiling the actual jar in `~/.m2`) - this is not 3.0-only.** If a boundary-based
  redesign happens, implementing this interface directly (rather than inventing a
  Rainbow-Gum-specific name) gets free recognition from anyone already familiar with
  the SLF4J/Logback idiom, at zero new dependency cost.
- **It is not actually depth-free, only differently brittle** - a single boundary
  class name doesn't by itself solve "N layers of decorators deep" any more cleanly
  than an integer does. Needs either (a) a package-prefix allowance (easy for
  `io.jstach.rainbowgum.slf4j`'s own built-in wrappers, one prefix covers all of
  them), or (b) each additional third-party decorator layer re-asserting its own
  boundary (`setCallerBoundary(itsOwnClass)`) when it delegates further out - the
  FQCN-shaped analog of today's `depth + 1` chaining
  (`LoggerDecoratorService.decorate(RainbowGum, DepthAwareLogger, int depth)`'s
  `depth` parameter exists for exactly this reason today). Whichever shape wins,
  "who currently owns the boundary" still needs an answer - the question just moves,
  it doesn't disappear.
- Adam's naming lean so far: keep the `DepthAware*` interface family name (or possibly
  rename toward something like `CallerBoundaryAware`-adjacent, still undecided - see
  below), but replace `setDepth(int)`/`withDepth(int)` with something FQCN-shaped
  (`setCallerBoundary(String)` if just implementing SLF4J's own interface directly, or
  a Rainbow-Gum-spelled-out equivalent) for the decorator case specifically.
- **Logback has not actually adapted to any of this yet** - the commit that bumps its
  own `slf4j.version` to `3.0.0-rc0-SNAPSHOT` (`13f21ffe5`) is two lines of substance
  (the version bump plus an unrelated license-header touch-up), no functional change,
  zero interop with `withCallerData`/`getCallerData`. Logback's own
  `ch.qos.logback.classic.spi.CallerData` (the class SLF4J's new one was modeled on)
  hasn't been touched functionally either. **There is no reference "how a real
  provider wires this up" implementation to crib from yet** - whatever Rainbow Gum
  does here would be its own design call, not a port of prior art.

Relates to (and would directly inform) the still-open JCL item in section 5 above -
extracting the shared depth/changeable-logger machinery out of `rainbowgum-slf4j` for
reuse by a future second facade implementation is a lot more attractive to do *after*
deciding whether that machinery stays depth-counted or becomes boundary-based, not
before.

- [ ] Grep the actual current blast radius: every file in `rainbowgum-slf4j` touching
      `Depth`/`depth` (`DepthAwareLogger`, `DepthAwareEventBuilder`, `withDepth`,
      `setDepth`, `AbstractFilteringLogger`'s `DEPTH` constant,
      `RainbowGumEventBuilder`'s `DEPTH_DELTA`, `WrappingLogger`/`ForwardingLogger`'s
      own depth handling, `LoggerDecoratorService.decorate(...)`'s `depth` parameter) -
      not yet done, needed before committing to a shape given how large this sounds
      ("Depth" is apparently "all over the place").
- [ ] Decide the actual shape: implement `org.slf4j.spi.CallerBoundaryAware` directly
      vs. a Rainbow-Gum-specific interface/method name; keep or rename the
      `DepthAware*` family; how a third-party `LoggerDecoratorService` decorator is
      expected to identify itself (re-assert boundary per layer vs. a registered
      trusted-package list vs. something else).

## 7. Track slf4j-jdk-platform-logging double formatting (qos-ch/slf4j#498)

SLF4J's `System.Logger` adapter (`org.slf4j:slf4j-jdk-platform-logging`,
`SLF4JPlatformLogger.performLog`) formats the message with `MessageFormat` and then
still passes the same parameters on as SLF4J arguments, so `{}` inside a parameter is
substituted a second time, unreferenced parameters are logged, and a malformed pattern
throws `IllegalArgumentException` out of `System.Logger.log`. Reproduced on 2026-10-20
with SLF4J 2.0.20 and `rainbowgum-slf4j`: `a=X b=X` and `user={} {} unreferenced`.
Rainbow Gum's own `rainbowgum-jdk` logs `a=X b={}`, `user={} {}` and falls back on the
malformed pattern. The user guide now warns against the adapter (`#slf4j_platform_logging`).

https://github.com/qos-ch/slf4j/pull/498 proposes dropping the SLF4J arguments. Adam
commented there against changing it silently: the behavior has shipped for years, so by
Hyrum's law something depends on the parameters, for example a filter that blocks
events whose parameters look sensitive would stop seeing them and let the data through.
Adam's suggestion for 3.0 is to remove the module, or to do no formatting and tag every
call with a shared marker (as Commons Logging's `Slf4jLogFactory` does with
`SYSTEM-LOGGING`) so a provider knows the message and parameters are `MessageFormat`
style. Keep following the PR and encourage Ceki toward the marker.

- [ ] Follow #498 and any 3.0 change; update the user guide warning when it is resolved.
- [ ] If the marker lands, have `rainbowgum-slf4j` recognize it: format with
      `MessageFormat` itself, or raise an alert (or fail) since `rainbowgum-jdk`
      should be handling `System.Logger`.
- [ ] Until then, consider a startup check: if
      `org.slf4j.jdk.platform.logging.SLF4JSystemLoggerFinder` is on the class path,
      raise a warning alert (so `logging.alerts.fail=warning` stops startup). If both it
      and `rainbowgum-jdk` are present, the JDK uses whichever `System.LoggerFinder` it
      finds first, which on the class path is jar order (verified both ways).
