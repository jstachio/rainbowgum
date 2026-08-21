# Roadmap to 1.0.0

The next release off this work is 0.10.0, not 1.0.0 - the items below are what's
still outstanding before an eventual 1.0.0, not a blocker list for 0.10.0.

## 1. Improve status reporting

A first pass exists on `feature/log-status-manager` (currently on hold, not merged):
`LogStatusReporter` - a bounded, drop-oldest ring buffer of `StatusEvent`s living on
`LogConfig`, meant to bridge `MetaLog`'s push-style error logging with
`LogResponse.Status`'s pull-style single-snapshot health checks. Rationale: a single
`status()` per component can't express "this got a little worse over the last minute" -
metrics beat a binary health check. To land before 1.0:

- [ ] Decide bootstrap scope and finish wiring: only `BlockingQueueAsyncLogPublisher`
      and `DisruptorLogPublisher` route through `statusReporter()` today.
      `LogAppender`'s `LogReentryAppenderLock`, `ServiceRegistry`, and external modules
      (RabbitMQ output, `RainbowGumSystemLoggerFinder`) still only ever reach stderr.
- [ ] Design and expose an actual pull API for consumers (health checks, admin
      endpoints) that surfaces `recent()` history alongside the existing instantaneous
      `LogOutputRegistry.status()` snapshot, not as a separate, undiscoverable thing.
- [ ] Sanity check the default capacity (currently 250, via
      `logging.global.status.capacity`) against a real consumer instead of a guess.
- [ ] Revisit whether coalescing repeated identical errors (a stuck queue dropping every
      event) is needed - explicitly deferred out of the first pass.

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
also handles bare `/`-absolute paths via a `name://` trick. This means an absolute-path
URI for a generic `output=` property may still behave inconsistently with the same value
used for a publisher or encoder. Worth auditing and unifying.

- [ ] Reconcile `LogOutputRegistry.normalize()` and `DefaultLogProviderRef.normalize()`
      into one implementation (or a clearly documented reason they must differ).
- [ ] `LogAppenderRegistry` carries its own admission of guilt in a comment: "The shit
      in here is a mess because auto configuration of appenders based on properties is
      complicated." A focused cleanup pass is overdue, now that `fileAppender()` has
      already been simplified once this cycle via `mapResult`.
- [ ] The `Property`/`PropertyGetter`/`Result` monad (`map`, `mapResult`, `or`,
      `orElse`, multi-key fallback, etc.) has essentially no direct unit tests of its
      own composition/error-propagation/fallback-chain behavior - it's exercised only
      indirectly through callers. Give it the same treatment `LogFormatter`,
      `LogAppender`, and `RainbowGum`'s entry points got this cycle.
- [ ] Old backlog item, still open: fix `LogProperties` search to use interpolated
      keys.

## 5. Whatever else before 1.0.0

- [ ] **rainbowgum-tomcat throughput regression**: a reproducible ~15-20% regression
      (default/GELF scenarios) survived three separate rule-out investigations (raw-JUL
      bypass instrumentation, JFR CPU/allocation profiling, exhaustive reflective-
      construction counting) without a root cause. Needs a clean, non-shared benchmark
      environment to chase further - or, failing that, a documented known-issue before
      shipping 1.0 with Tomcat integration included.
- [ ] **rainbowgum-nio** (`DirectByteBufferEncoder`) is explicitly experimental. Decide
      before 1.0 whether it graduates to a real recommendation, stays clearly marked
      experimental, or gets pulled if it hasn't earned its keep.
- [ ] A few fixes from this cycle are sitting on branches that were never confirmed
      merged - worth a final check before release: `FileChannelOutput`'s
      closed-after-close guard, and `ForwardingOutputTest`'s post-`ByteBuffer`-default
      fix update.
- [ ] `StdErrOutput` is missing the no-op-after-close override that `StdOutOutput`
      already has - flagged mid-cycle, deferred, never circled back to.
- [ ] Once items 1 and 2 above land, sweep `doc/overview.html` for consistency
      (status reporting section, nullability mentions) rather than patching it
      piecemeal per-PR the way this cycle did.
