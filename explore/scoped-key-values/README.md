# ScopedKeyValues - working prototype

Not wired into RainbowGum's build on purpose - this is a concrete reference
implementation to learn from before shaping an API, per the discussion that led here.
Deliberately implementation-neutral (no `io.jstach.rainbowgum` imports) since the goal is
to find a shape that could plausibly inform a future SLF4J API, not just a RainbowGum
internal.

## Design, as agreed so far

- Always-immutable payload (`KeyValues` - index-based, not `java.util.Map`, mirroring
  `io.jstach.rainbowgum.KeyValues`'s existing shape). No runtime mutation at all, which
  is what makes this simpler than an MDC-shaped "mutable container behind a
  `ScopedValue`" design: nothing can race because nothing changes after `run`/`call`
  starts.
- `ScopedKeyValues.Builder` - add all keys/values up front, then `run(name, body)` or
  `call(name, body)` binds them for that call's dynamic extent (and any
  `StructuredTaskScope` children forked inside it).
- Named scopes, managed/interned by a `ScopedKeyValuesFactory` (the SPI a logging
  implementation would provide) the same way `MarkerFactory` interns `Marker`s by name -
  the point is that unrelated code agreeing on a name (by convention, or an FQCN) sees
  the same underlying `ScopedValue`, not independent ones that never see each other.
- **First wins** on a nested `run`/`call` under the same name: the inner call just
  invokes its body directly without rebinding: the outer binding stays in effect for its
  entire extent, silently. Chosen because it's the simplest option, and the expected
  mitigation for accidental same-name collisions is the same convention SLF4J already
  uses for logger names: recommend FQCN-based scope names.
- `ScopedKeyValuesFactory#currentAll()` - the piece that isn't really about
  `ScopedValue` mechanics at all: whatever eventually builds a `LogEvent` has no
  compile-time knowledge of which named scopes exist (a dependency could register its
  own under its own FQCN, entirely unknown to core), so retrieval can't be "look up this
  one name" - it has to be "walk every name ever registered, return whichever are
  currently bound on this thread, merged." Implemented by keeping a separate
  insertion-ordered list of every `Scope` ever interned (not relying on
  `ConcurrentHashMap`'s iteration order) and checking `isBound()` on each.

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
== basicUsage ==
current(request) = Optional[{requestId=abc123, tenant=acme}]
== firstWinsOnNestedSameName ==
outer sees: Optional[{requestId=outer}]
inner sees (should still be 'outer'): Optional[{requestId=outer}]
outer still sees: Optional[{requestId=outer}]
== retrievalDoesNotNeedToKnowNamesAheadOfTime ==
currentAll() merged = {requestId=abc123, txId=tx-789}
== structuredChildInheritsBinding ==
child thread sees: Optional[{requestId=parent-value}] on VirtualThread[#36]/runnable@ForkJoinPool-1-worker-1
== notBoundOutsideAnyScope ==
current(request) outside any run = Optional.empty
currentAll() outside any run = {}
```

Confirms, concretely rather than by reasoning about the spec alone:
- Nested same-name `run` really is a silent no-op for the inner call (outer value
  observed on both sides of the nested call, and after it returns).
- `currentAll()` really does pick up a second scope (`some.library.FQCN.transaction`)
  that the caller never referenced by name anywhere - the actual retrieval problem this
  needs to solve for an appender/encoder that has no idea what a random dependency
  registered.
- A `StructuredTaskScope.fork(...)` child, running on its own virtual thread, really
  does inherit the binding with zero manual copying - confirmed by printing the child's
  own `Thread.currentThread()` alongside the value it sees.
- Genuinely unbound (nothing ever ran) reads back empty/absent, not some default.

## Open questions, not resolved by this prototype

- `currentAll()` is O(number of registered names), walked on every call - fine for "just
  one scope for now," worth watching if named scopes proliferate.
- Merge order for `currentAll()` is currently just registration order (whichever scope
  was interned first sorts first) - arbitrary, not discussed yet.
- No SPI discovery mechanism here (`ScopedKeyValues` hardcodes
  `DefaultScopedKeyValuesFactory.INSTANCE`) - a real version needs whatever discovery
  story SLF4J providers already use.
- `StructuredTaskScope` being preview-in-JDK-26 (unlike `ScopedValue`, which finalized in
  JDK 25) matters for when any of this could actually ship - the *scope inheritance*
  half of the pitch specifically depends on the still-preview API, not just the
  finalized `ScopedValue` piece.
