# Global binding and removal notifications

This is the follow-up to [the global push review](review-global-change-push.md).
It combines two attempts at publishing removal of the global Rainbow Gum:
Codex's `feature/global-clear-push` and Claude's `feature/global-remove-push`.
The result is on `feature/global-clear-push-claude`.

## API choice

`RainbowGum.onGlobalChange` stays the only subscription and now covers removal:

```java
RainbowGum.onGlobalChange(Consumer<@Nullable RainbowGum> consumer)
```

The consumer receives what `getOrNull()` returns after each change: the newly
bound gum, or `null` once none is bound. Subscribing, reading `getOrNull()`
once, and then following notifications therefore tracks the binding exactly.
One subscription keeps bindings and removals in one ordered stream with no
second listener list to keep in step.

This changes the contract of an existing method instead of adding one, which
is acceptable before 1.0. A consumer written for bindings only still compiles
and can now receive `null`; nullness analysis reports it (the core tests'
own subscribers were caught this way). `Optional<RainbowGum>` was considered.
It suits a second, new method, but as the parameter of the existing one it
would differ from `getOrNull()` and from the JSpecify style used elsewhere,
and every consumer converted it straight back to a nullable value.

| Transition | Notification |
| --- | --- |
| Start and bind a gum | `gum` |
| Close the bound gum | `null` |
| Unset the bound gum | `null` |
| Register a supplier while a gum is bound | `null` |
| Remove when none is bound | None |
| Close an older, replaced gum | None |
| Fail to start a replacement supplier | None; none stays bound |

Registering a supplier neither starts it nor closes the gum it replaces. The
global router keeps its previous delegate until the replacement starts or the
router is reset. Closing or unsetting the bound gum resets the router to its
bootstrap queue.

## Delivery and locking

Bindings and removals share one queue. Lifecycle operations enqueue under the
holder's write lock; a single drainer calls consumers after that lock is
released and without holding the queue monitor. A runtime failure in one
consumer is reported internally and does not stop later notifications.

Component code can reenter `remove()` during startup or shutdown, for example
from an output's `close()`. Only the outermost lifecycle operation drains, so
no consumer runs while the write lock is held. `remove()` unbinds and enqueues
before resetting the global router, so a failing output close still leaves
none bound and still publishes the removal. `close()` shuts the gum down even
if removal throws.

A concurrent or reentrant change can return before an existing drainer has
delivered it. Notifications describe changes with eventual convergence, not a
lease on a gum's lifetime. A slow consumer delays later notifications. A
consumer should drop what it holds on `null` rather than call
`RainbowGum.of()`, which would start another gum during shutdown; deliberate
reentrant rebinding is supported and tested.

A queued bootstrap gum borrows the global router. Its shutdown no longer
closes that router: `remove()` owns the reset, and closing it again could
reset a replacement a consumer has already bound.

## Consumers

- The three JUL style holders drop their gum and take the message format style
  from system properties, as before the first gum starts. An invalid system
  property leaves the default style and is reported through the publisher.
- The System.Logger finder's initializing router provider follows the same
  holder instead of keeping the first gum. The first lookup keeps its
  `TRUE`/`CHECK`/`REUSE` behavior. Afterwards, with none bound, it uses the
  global router without starting another gum.
- The SLF4J factory keeps its current gum and decorator chain in one immutable
  state, re-resolving decorators for each bound gum. On removal it drops both
  and resets the shared contributor. A name looked up while none is bound gets
  a replaceable logger whose events go through the global router, so they
  queue and then reach the next binding. Those loggers are cached apart from
  the main logger map and dropped on the next binding, so the same name looked
  up afterwards gets that gum's decorators and settings.
- Any SLF4J logger created on the global router, including through a queued
  bootstrap gum without `logging.change.logger=level`, is replaceable. A fixed
  logger there would stay on the queue that is abandoned once a router binds.

Existing cached SLF4J loggers created on a removed gum's own router are not
recreated: a fixed logger keeps its route, and a replaceable one listens to
that router's change publisher, which the next binding does not use. Events
queued while none is bound keep their original formatter and key values.
