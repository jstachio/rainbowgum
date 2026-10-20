# Review of `feature/global-change-push`

Reviewed commit: `58aa5b2c658c9bac8d0b39a459c080c2bc69e9f0`, against
`e4b814dc6d78a5bbaf664bc7c9da2dee15224a27` (`main`). Fixes and regression tests
are on `review/global-change-push`, based directly on the reviewed commit.

## Findings fixed

1. **P1: A holder can finish with an older gum after a newer binding.**
   All three holders accept each pushed gum unconditionally
   ([original System.Logger holder, lines 61–64](https://github.com/jstachio/rainbowgum/blob/58aa5b2c658c9bac8d0b39a459c080c2bc69e9f0/rainbowgum-systemlogger/src/main/java/io/jstach/rainbowgum/systemlogger/GlobalRainbowGum.java#L61-L64)).
   The existing core publisher releases the lifecycle write lock before invoking
   subscribers, so notification order need not match binding order. For example,
   subscriber 1 binds B while receiving A: subscriber 2 sees B, then A, and its
   formatter stays on A while global routing uses B. A concurrent binding while
   subscriber 1 is blocked has the same effect. `synchronized accept` prevents
   overlapping writes but does not establish which notification is newer; the
   `pushed` flag only protects the initial lookup.

   Core now enqueues under the lifecycle write lock and uses one drainer to
   deliver each binding to subscribers in order. It invokes callbacks without
   holding the lifecycle lock or the queue monitor. Concurrent and reentrant
   binders enqueue and return rather than wait for the active subscriber.
   Two deterministic lifecycle tests cover reentrant and concurrent replacement.

2. **P2: An overridden system property can prevent a facade from initializing.**
   Each `GlobalRainbowGum.of()` parses system properties before checking for an
   already bound gum
   ([original lines 36–42](https://github.com/jstachio/rainbowgum/blob/58aa5b2c658c9bac8d0b39a459c080c2bc69e9f0/rainbowgum-systemlogger/src/main/java/io/jstach/rainbowgum/systemlogger/GlobalRainbowGum.java#L36-L42)).
   With a bound `logging.messageFormat.apply=index` and an overridden system
   value of `invalid`, logger/provider initialization throws, even though the
   effective configuration is valid. Previously logger creation read the bound
   configuration. Holders now subscribe first and resolve bound properties when
   available, reading system properties only when no gum has been observed.
   A regression runs against each of the three copies.

3. **P2: An alert listener can replace a gum and then have the replacement overwritten.**
   The invalid-property catch reports an alert before writing holder state
   ([original lines 72–80](https://github.com/jstachio/rainbowgum/blob/58aa5b2c658c9bac8d0b39a459c080c2bc69e9f0/rainbowgum-systemlogger/src/main/java/io/jstach/rainbowgum/systemlogger/GlobalRainbowGum.java#L72-L80)).
   During the initial lookup of A, an alert listener can bind B. Java monitors
   are reentrant, so `accept(B)` completes, then the outer call overwrites it
   with A. Holders now store the fallback formatter and gum before reporting
   the alert. Each facade has a regression using a real alert listener.

The existing publisher also allowed a subscriber's runtime exception to escape
`RainbowGum.of()` after binding succeeded and skip all later subscribers. The
shared delivery fix reports that exception through the internal error log and
continues; a lifecycle regression covers this case. This predates the reviewed
commit, but matters more once these facades depend on notifications.

## Locking assessment

The volatile publication of the fully started gum is sufficient for
`getOrNull()`'s nonblocking snapshot contract. Its old `readLock().tryLock()`
could return null merely because a writer held the lock, even if a fully bound
gum still existed. A late subscriber could then miss both the earlier push and
the current snapshot. The follow-up reads the volatile field directly, as the
existing internal `peek()` already does.

The lifecycle write lock remains necessary to serialize supplier resolution,
startup, and global router changes. `of()` retains its existing blocking and
same-thread early-initialization guard. Changing to `StampedLock` would add
reentrancy concerns without solving notification ordering or object lifetime.
Neither a read lock nor a stamp held only during lookup can keep the returned
gum open afterward.

The subscription ordering is sensible: retain a strong reference, subscribe,
then read the current snapshot; synchronize that initialization with callbacks.
The two volatile fields safely publish their individual values. They do not
form an atomic pair, but current callers do not consume them as one transaction.
If a future caller requires a matching gum and formatter, expose one immutable
snapshot through one volatile reference.

Callbacks describe bindings, not a lease on the active instance. While a
subscriber runs, another thread can bind or close its gum. Ordered delivery
provides eventual convergence after binding callbacks finish, rather than an
atomic transition of router and every facade. A blocked subscriber delays
later notifications, although it does not hold up concurrent lifecycle writes.
This behavior is now documented on `onGlobalChange`.

## Remaining design decisions

The removal-event question below is addressed by the separate
[`feature/global-clear-push` follow-up](global-state-change.md). This review
records the original branch and its first round of fixes.

- Closing, unsetting, or registering a replacement supplier does not send a
  removal event. Holders can retain the last gum and formatter until the next
  successful binding; newly created loggers can register names against that
  old config. This is already called out in the branch's TODO. Fixing it needs
  an explicit empty-state subscription contract, also accounting for SLF4J and
  Spring consumers that currently expect a non-null gum. Passing null to
  existing consumers would break them.
- Keep the three helpers internal for now. A shared core API would be useful
  once removal, initial replay, ordering, and snapshot semantics are settled;
  exporting this formatter-specific helper would freeze those choices early.
- Events already queued capture their original formatter. Updating a holder
  affects future events, not queued events.
- Startup and removal still call component code under the lifecycle write lock.
  Components that start another thread and wait for it to call blocking
  `RainbowGum.of()` can deadlock; the existing early-initialization guard only
  catches reentry on the same thread. The notification changes do not redesign
  that provisioning contract.

## Validation

The initial six regression cases failed on the reviewed implementation and
passed after the fixes. Three further cases cover alert-listener reentrancy.
The full reactor `./mvnw --fail-at-end install` passed, including all nine
regressions and the JDK facade matrix. Core's cached result from the earlier
filtered run required an additional unfiltered affected-module run:

```sh
./mvnw -pl core,test/rainbowgum-test-core,rainbowgum-systemlogger,rainbowgum-jboss-logging,rainbowgum-jul-logmanager -am -Dmaven.build.cache.enabled=false verify
```

That run passed 5,908 tests with no failures, errors, or skipped tests.
Checker Framework, Error Prone, and NullAway also passed for core and
System.Logger:

```sh
_run_modules=core,rainbowgum-systemlogger bin/analyze.sh
```

The six facade regressions were rerun successfully after the final analyzer
compatibility changes to their null assertions.

Native image execution was not repeated: `native-image` is unavailable in this
environment. The changes add no new core implementation class, and retain the
branch's existing build-time initialization declarations.

Reviewed and implemented by Codex, at Adam Gent's request.
