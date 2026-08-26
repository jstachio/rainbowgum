# Roadmap to 1.0.0

The next release off this work is 0.10.0, not 1.0.0 - the items below are what's
still outstanding before an eventual 1.0.0, not a blocker list for 0.10.0.

See also `code-todos.md` for a themed survey of the `// TODO` comments still scattered
through the codebase - several feed directly into the items below.

## 1. Replace pull-style status with separate alerts and metrics systems

The old design (`LogResponse.Status`, one `status()` snapshot per component) is being
replaced by **two separate pushed systems**, not a single unified status API:

- An **alerts** system - event-driven, for the "this just broke" / "this got a little
  worse" case `MetaLog`'s push-style error logging already covers informally. This is
  the direction `feature/log-status-manager` (currently on hold, not merged) was
  headed with `LogStatusReporter`, a bounded drop-oldest ring buffer of `StatusEvent`s
  on `LogConfig`.
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

- [ ] Design the alerts system: finish or restart `feature/log-status-manager`'s
      `LogStatusReporter` approach (bounded ring buffer bridging `MetaLog`'s push-style
      errors), decide bootstrap scope (only `BlockingQueueAsyncLogPublisher` and
      `DisruptorLogPublisher` routed through `statusReporter()` in that branch -
      `LogAppender`'s reentry diagnostic (`AbstractLogAppender.shouldDropForReentry`'s
      `MetaLog.error(...)` call on `REENTRY_LOG`), `ServiceRegistry`, and external
      modules like the RabbitMQ output and `RainbowGumSystemLoggerFinder` still only
      ever reached stderr), and expose a real pull/subscribe API for consumers (health
      checks, admin endpoints) instead of a separate, undiscoverable thing bolted onto
      `LogOutputRegistry.status()`.
- [ ] Design the metrics system: needs its own home for queue-depth-style gauges now
      that `QueueStatus` is gone - not necessarily reusing `LogResponse.Status` at all,
      since that type was built around single-snapshot health rather than metrics.
- [ ] Sanity check the alerts ring buffer's default capacity (currently 250 in the
      on-hold branch, via `logging.global.status.capacity`) against a real consumer
      instead of a guess.
- [ ] Revisit whether coalescing repeated identical alerts (a stuck queue dropping
      every event) is needed - explicitly deferred out of the on-hold branch's first
      pass.
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

## 2. Replace Eclipse JDT nullability annotations with JSpecify

`org.eclipse.jdt.annotation.Nullable` + CheckerFramework's Nullness Checker are used
today, but only on `core` - `bin/analyze.sh checkerframework` is hardcoded to `-pl core`,
so every other module (`rainbowgum-pattern`, `rainbowgum-json`, the Spring modules,
`rainbowgum-disruptor`, etc.) currently has zero null-checking. CheckerFramework has also
shown real friction: its bundled stubs don't treat `Objects.requireNonNull`/
`Objects.requireNonNullElse` the way you'd expect for narrowing a `@Nullable` value,
which forced awkward workarounds in test code this cycle.

- [ ] Mechanically replace `org.eclipse.jdt.annotation.Nullable` with
      `org.jspecify.annotations.Nullable` throughout - the vendor-neutral standard, and
      what the wider ecosystem (Guava, Spring, etc.) is converging on. This is a big,
      mostly-mechanical multi-module migration, probably worth its own dedicated
      branch rather than folding into other work.
- [ ] Settle the enforcement tool as a separate decision from the annotation swap:
      CheckerFramework's Nullness Checker already understands JSpecify annotations, so
      keeping CheckerFramework is one option; switching to error-prone + NullAway (a
      lighter-weight, JSpecify-native checker) is the other. Don't assume the tool
      change is bundled with the annotation change.
- [ ] Whichever tool wins, extend checking to every module, not just `core`, closing
      the current gap.
- [ ] Confirm this also retires the known-broken Eclipse profile tooling wrinkle, since
      it's tied to the JDT/CheckerFramework combination.

## 3. Consider file rolling

This reverses the current stance: the old backlog explicitly said "not going to support"
and `doc/overview.html`'s "Rolling Files" section currently leans entirely on external
tools (`logrotate`) for safe rotation. If this moves forward, both need to change
together, not just the code.

- [ ] Decide the trigger model up front - time-based, size-based, or both - before
      writing any implementation.
- [ ] Decide where it lives: a new `LogOutput` wrapping `FileOutput`, or a decorator
      that composes with the existing safe-external-rotation mechanism rather than
      replacing it.
- [ ] Stay in RainbowGum's own lane rather than porting Logback's rolling-policy
      hierarchy wholesale - the differentiator here is staying simple/low-overhead.
- [ ] Update `doc/overview.html`'s Rolling Files section and the old "Features not
      going to support" note once a direction is picked.

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
- [ ] **Reuse-by-name config**: decide whether `name:///somename`-style references
      (letting one property block, e.g. `logging.encoder.somename.*`, be reused from
      another, e.g. `logging.appender.default.encoder=name:///somename`) are actually
      worth building. The scaffolding for this (`LogOutputRegistry.NAMED_OUTPUT_SCHEME`,
      the `name://` normalization trick) was removed since it was never implemented -
      using it would only ever throw `NotFoundException`. If revisited, needs a real
      design (which registries support it, how it interacts with `{name}` key
      parameters) rather than reintroducing dead scaffolding.
- [ ] `LogAppenderRegistry` carries its own admission of guilt in a comment: "The shit
      in here is a mess because auto configuration of appenders based on properties is
      complicated." A focused cleanup pass is overdue, now that `fileAppender()` has
      already been simplified once this cycle via `mapResult`.
- [ ] **Misleading error wrapper from the `logging.file.name` auto-configuration
      shortcut**: `LogAppenderRegistry.fileAppender()` wraps the entire downstream
      `FileOutputBuilder.build()`/`fromProperties()` call inside a `.map()` chained off
      the `logging.file.name` property lookup. Any exception thrown deep inside -
      including a `ValidationException` for a completely unrelated property like
      `logging.output.file.uri` or `logging.output.file.bufferSize` - gets mislabeled
      in the outer message as "Error converting property. key: 'logging.file.name'"
      even though `logging.file.name` itself converted fine. Confirmed present in both
      `FileOutputPropertiesTest.URI_WITH_BAD_BUFFER_SIZE` and `BAD_OUTPUT_URI`'s golden
      strings. Adam's read: this is specifically a side effect of the
      `logging.file.name` convenience/auto-configuration path - the same failure
      configured the "non-default" way (e.g. `logging.appender.<name>.output=file:...`
      directly, bypassing the shortcut) reads better since there's no such wrapping
      `.map()` in the way. Worth fixing when `fileAppender()` gets its cleanup pass
      above, rather than as a one-off.
- [ ] The `Property`/`PropertyGetter`/`Result` monad (`map`, `mapResult`, `or`,
      `orElse`, multi-key fallback, etc.) has essentially no direct unit tests of its
      own composition/error-propagation/fallback-chain behavior - it's exercised only
      indirectly through callers. Give it the same treatment `LogFormatter`,
      `LogAppender`, and `RainbowGum`'s entry points got this cycle.
- [ ] Old backlog item, still open: fix `LogProperties` search to use interpolated
      keys.
- [ ] **Design critique worth revisiting before any deeper rework here**: the
      current model spreads a property's lifecycle across a different class per
      step - a type alone gets a `PropertyGetter`, a key plus type gets a
      `Property`, and a key plus type plus value gets a `Result` (reached via
      `PropertyValue`) - essentially a curried chain of types. A simpler
      alternative worth considering: a single `Property`-like object that just
      holds the config, the type, the key, *and* the last-retrieved value/result
      together, instead of threading that state through several distinct types.
      Not a quick fix - would touch every call site in this section - but should
      be the starting point if `LogProperty` gets a real redesign pass.

## 5. Whatever else before 1.0.0

- [ ] **rainbowgum-tomcat throughput regression**: a reproducible ~15-20% regression
      (default/GELF scenarios) survived three separate rule-out investigations (raw-JUL
      bypass instrumentation, JFR CPU/allocation profiling, exhaustive reflective-
      construction counting) without a root cause. Needs a clean, non-shared benchmark
      environment to chase further - or, failing that, a documented known-issue before
      shipping 1.0 with Tomcat integration included.
- [ ] A few fixes from this cycle are sitting on branches that were never confirmed
      merged - worth a final check before release: `FileChannelOutput`'s
      closed-after-close guard, and `ForwardingOutputTest`'s post-`ByteBuffer`-default
      fix update.
- [ ] `StdErrOutput` is missing the no-op-after-close override that `StdOutOutput`
      already has - flagged mid-cycle, deferred, never circled back to.
- [ ] Once items 1 and 2 above land, sweep `doc/overview.html` for consistency
      (status reporting section, nullability mentions) rather than patching it
      piecemeal per-PR the way this cycle did.
