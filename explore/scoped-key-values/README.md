# ScopedKeyValues - working prototype

Not wired into RainbowGum's build on purpose - this is a concrete reference
implementation to learn from before shaping an API, per the discussion that led here.
Deliberately implementation-neutral (no `io.jstach.rainbowgum` imports) since the goal is
to find a shape that could plausibly inform a future SLF4J API, not just a RainbowGum
internal.

**Second pass, simpler than the first commit on this branch** - the name/factory/SPI
layer (interned scopes looked up by name, `ScopedKeyValuesFactory#currentAll()` walking
a registry) is gone entirely. Kept for the record in git history, not deleted-and-forgotten,
but superseded: it turned out to be solving a problem ("how does retrieval find scopes it
doesn't know the name of") that a simpler design doesn't have in the first place.

## Design, as converged on through discussion

- **One `ScopedValue<List<KeyValues>>`.** No names, no per-name interning, no SPI/factory
  in this pass. `KeyValues` stays index-based/immutable (mirrors
  `io.jstach.rainbowgum.KeyValues`'s existing shape), same as before.
- **Push, don't mutate.** `ScopedKeyValues.builder().add(...).run(body)` builds a brand
  new immutable list - the previously-bound list as the head, the new `KeyValues` as the
  tail - and rebinds via plain `ScopedValue.where(...).run/call(...)`. No COW/CAS, no
  mutable cell anywhere: the JDK's own dynamic-scope unwind *is* the entire "pop"
  mechanism. Nothing to clean up, nothing that can leak.
- **Expected real usage: one push wrapping the entire request/task, at the boundary** -
  the same shape the `ScopedValue` JEP's own examples use (a web filter binds once,
  around the whole request), not something scattered through every layer of business
  logic. This is why the "every append needs a lambda" cost that worried us earlier
  mostly isn't a practical cost at all - there's normally exactly one lambda, at the top.
- **Retrieval no longer needs to know names.** `ScopedKeyValues.current()` just returns
  whatever's on the stack right now - the "how does an appender/encoder find scopes
  registered under an FQCN it's never heard of" problem from the first design pass
  disappears, because there's only one channel to read from.
- **Deliberately does not replace ad-hoc, imperative, single-call-site key-values** (the
  MDC.put-style "add this to just this one log line" need). That's still SLF4J's own
  `LoggingEventBuilder#addKeyValue(...)`'s job - a different shaped need (one log line,
  no lambda, mutate-anytime) than this (a whole request, set up once, immutable). Two
  complementary mechanisms for two different shapes of need, not one mechanism trying to
  cover both and doing neither well.

## What killed the earlier mutable/COW-cell idea

Proved empirically, not just reasoned about (see `Demo.java`'s
`siblingsAndParentDoNotSeeEachOthersAdditions`): with pure immutable nested rebinding,
sibling `StructuredTaskScope` forks - and the parent, after they return - never see
anything a sibling pushed. Everyone gets exactly (and only) whatever was bound before
they started; nothing anyone does afterward leaks sideways or backward. A shared mutable
cell (the earlier "COW list" idea) could not have made this guarantee - a sibling
appending after another sibling forked, or the parent continuing to append after a fork,
would have been visible to whoever held the shared reference. Immutable-per-push turned
out to be strictly better *and* simpler than the mutable version, not just a safer
tradeoff.

Also confirmed empirically (see `InheritCheck`-style checks, folded into this file's
history rather than kept as a separate demo): inheritance is a property of
`StructuredTaskScope.fork(...)` specifically, not of threads/virtual-threads in general -
a plain `Thread.ofVirtual().start(...)` or an ordinary `ExecutorService.submit(...)` sees
nothing at all, `isBound() == false`, same as a thread that was never inside any scope.
Only structured forks inherit, unconditionally, and it keeps propagating to grandchildren
forked further down.

## Running it

Needs JDK 25+ for `ScopedValue` (finalized, JEP 506) - `StructuredTaskScope` is still
preview as of JDK 26 (JEP 505's fifth preview), hence `--enable-preview` below. Verified
against Temurin 26.0.2:

```
cd src
javac --release 26 --enable-preview -d ../out *.java
java --enable-preview -cp ../out Demo
```

## Output, captured verbatim from a real run

```
== typicalUsageOnePushForTheWholeRequest ==
current() = [{requestId=abc123, tenant=acme}]
currentMerged() = {requestId=abc123, tenant=acme}
== nestedPushIsCumulativeNotACollision ==
after outer push: [{requestId=abc123}]
after inner push: [{requestId=abc123}, {step=validate}]
back to outer, inner's layer is gone: [{requestId=abc123}]
== structuredChildInheritsTheStack ==
child sees: [{requestId=parent-value}] on VirtualThread[#36]/runnable@ForkJoinPool-1-worker-1
== siblingsAndParentDoNotSeeEachOthersAdditions ==
parent before fork: [{requestId=req-1}]
sibling A sees: [{requestId=req-1}, {siblingA=own-layer}]
sibling B sees (must NOT have A's layer): [{requestId=req-1}]
parent after both children returned: [{requestId=req-1}]
== notBoundOutsideAnyPush ==
current() outside any push = []
```

## Open questions, not resolved by this prototype

- No SPI/provider-discovery mechanism at all in this pass (the first pass had one, for
  the factory; dropping the factory dropped it too) - a real version presumably still
  needs whatever discovery story SLF4J providers already use, so a specific
  implementation (e.g. RainbowGum's) can plug in its own storage if ever needed. Not
  addressed here since nothing about this design *requires* pluggability the way the
  named-factory version did.
- `current()`/`currentMerged()` walks and copies a `List<KeyValues>` on every call -
  cheap for the expected "one push per request" shape, worth revisiting if deeply nested
  pushes ever become common.
- `StructuredTaskScope` being preview-in-JDK-26 (unlike `ScopedValue`, which finalized in
  JDK 25) still matters for when any of this could actually ship - the structured-child
  inheritance half of the pitch specifically depends on the still-preview API.
