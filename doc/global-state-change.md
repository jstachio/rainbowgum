# Global binding and clear notifications

This is the follow-up to [the global push review](review-global-change-push.md),
on `feature/global-clear-push`, based on `review/global-change-push`.

## API choice

Keep `RainbowGum.onGlobalChange(Consumer<RainbowGum>)` compatible: its
subscribers still receive only newly bound, non-null gums. Add:

```java
RainbowGum.onGlobalStateChange(Consumer<Optional<RainbowGum>> consumer)
```

The optional explicitly represents the absence of a binding. This avoids
passing null to existing listeners, and lets one subscriber process both
bindings and clears in order. Both APIs retain listeners weakly, so owners
must retain their consumer. Registration does not replay current state;
the facade helpers retain their subscribe-then-read initialization guard.

| Transition | New state notification | Legacy notification |
| --- | --- | --- |
| Start and bind a gum | `Optional.of(gum)` | `gum` |
| Close the bound gum | `Optional.empty()` | None |
| Unset the bound gum | `Optional.empty()` | None |
| Register a supplier while a gum is bound | `Optional.empty()` | None |
| Clear an already empty binding | None | None |
| Close an older, replaced gum | None | None |
| Fail to start a replacement supplier | None; state remains empty | None |

Registering a supplier retains its existing semantics: it neither starts
the replacement nor closes the previous gum. The global router keeps its
previous delegate until the replacement starts or the router is explicitly
reset. An empty binding notification reports holder state; it does not claim
that every previous output has closed. Closing/unsetting a bound gum resets
the router to its bootstrap queue.

## Delivery and locking

Bindings and clears share one queue. Lifecycle operations enqueue under the
holder's write lock; a single drainer invokes consumers after releasing that
lock and without holding the queue monitor. Runtime failures in either kind
of listener are reported internally and do not prevent later notifications.

Reentrant removal from component code must not start draining while an outer
startup or removal still owns the write lock. The drainer is therefore
entered only after the delivering thread has released its outermost lifecycle
write lock, including exceptional exits. A failed output close still leaves
the binding empty and delivers its clear event.

A concurrent or reentrant transition can return before an existing drainer
has delivered its event. These are ordered transition notifications with
eventual convergence, not a lease on a gum's lifetime. A slow subscriber can
delay later notifications. A listener should normally release cached state
on clear without calling `RainbowGum.of()`, which would start a replacement
during shutdown; deliberate reentrant rebinding is supported and tested.

A queued bootstrap gum borrows the global router. Its cleanup must not close
that router again after removal has reset it: a clear listener may already
have bound a replacement, and a second close would reset that replacement's
router. Removal owns the borrowed router's reset; cleanup still closes the
bootstrap config and services.

## Consumers

- The three JUL-style facade holders clear their gum and resolve message
  formatting from system properties. Invalid system properties leave the
  standard JUL formatter in place and are reported through the publisher's
  internal error path. Clearing the gum happens before property validation.
  The next binding supplies its configured formatter as usual.
- The System.Logger finder's initializing router provider follows the same
  holder instead of caching the original gum separately. First use retains
  its `TRUE`/`CHECK`/`REUSE` initialization rules and resolves the bound config
  before formatter fallback properties. After it has initialized, an empty
  state uses the global router without implicitly starting another gum.
- SLF4J stores its current gum and decorator chain in one immutable factory
  state. A clear removes both and resets the shared contributor delegate;
  per-thread MDC remains the facade's own context. A new logger name during
  the gap uses the global router without the old config or decorators. After
  a router reset, its queued events drain to the next binding, and its
  changeable logger follows that binding. New names after the next binding
  use the new config's decorators and contributors.

Existing cached SLF4J loggers and System.Logger instances are not recreated.
Their established fixed-route/decorator semantics remain in place; this does
not promise to release arbitrary references captured inside previously
created loggers or user decorators. Logger instances created during an empty
state cannot retroactively acquire the next config's decorators or caller
wrappers. Events queued during the gap keep their original formatter and
key values.

## Validation

Regression coverage includes close/unset/supplier clears, duplicate clear
suppression, legacy listener compatibility, failed replacement startup,
concurrent delivery ordering, nested removal, output-close failures, and
reentrant replacement of a queued bootstrap gum. Each formatter holder is
checked for clearing, system fallback, invalid fallback, and subsequent
rebinding. SLF4J and the System.Logger finder are exercised through actual
logger calls across close, queueing, and a new binding.

The full reactor `./mvnw --fail-at-end install` passed (21,871 tests, no
failures, errors, or skips). The final core verification also covers closing
older normal and queued gums after their replacement has started.

Checker Framework passed core and System.Logger; the latter was analyzed
separately against installed core to avoid the documented close-call compiler
crash. Error Prone and NullAway passed core, System.Logger, and SLF4J together.
See [the build note](agent-build-issues.md) for the exact workaround. Native
image execution was not repeated because `native-image` is unavailable here.
