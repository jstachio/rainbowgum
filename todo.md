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
      default, the global `GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY` override, and
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

- [ ] **`console` as a scheme alias for `stdout`**: `LogOutput.STDOUT_SCHEME`/
      `STDERR_SCHEME` (`LogOutput.java`) are the only registered console output
      schemes today - `logging.appender.myapp.output=console` currently just fails
      with `NotFoundException`. Logback/Spring Boot users reach for "console" by
      habit (`ConsoleAppender`), so registering it in `LogOutputRegistry`'s
      `StandardLogOutputProvider` as a plain alias resolving to the same stdout
      output `STDOUT_SCHEME` does would be a small, low-risk win. Surfaced while
      adding the `doc/overview.html` "Console" output subsection.
      (ADAM: Is this even worth doing given 99/100 output defaults to stdout?)
