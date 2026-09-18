# Design notes: the "what actually got wired up" problem

Status: **discussion, not a decision.** Nothing here is implemented. This exists to
collect the problem statement, prior art, and options so we can talk through it, not to
propose a final shape.

## The problem

`todo.md` item 1's third, still-unaddressed facet (the other two, alerts and metrics,
already landed):

> A third, still-unaddressed facet the old `status()` API used to partly cover: a
> **static configuration report**, not alerts (event-driven) or metrics (gauges), just
> "what actually got wired up." With `REUSE_BUFFER`/`LOCK_THREAD_LOCAL_BUFFER`/
> `SYNCHRONIZED_THREAD_LOCAL_BUFFER`, the JDK-version-sniffed default, the global
> `GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY` override, and (possibly) a future Spring
> Boot virtual-thread sniff all in play, there is no way today to tell *which concrete
> appender class* actually got selected for a given route/output short of reading code
> or attaching a debugger.

Since that was written, the surface this would need to cover has only grown:
`AppenderType.AUTO_DETECT` (platform-gated), `logging.global.optimize` (not yet merged,
`feature/global-optimize-property` - opts appenders/encoders into platform-specific
defaults), `LOCK_NEW_BUFFER`. Every one of these makes "which concrete strategy actually
got picked for this deployment" a real question with no answer today short of reading
source or attaching a debugger.

This is a third, distinct concern from the two that already have homes:

| | shape | question it answers | home |
|---|---|---|---|
| alerts (`LogAlerts`) | event-driven, pushed, bounded history | "did something just go wrong" | done, `LogConfig#alerts()` |
| metrics (`LogMetrics`) | numeric, gauge-style | "how much/how many, right now" | done, `LogConfig#metrics()` |
| **this** | static, structural, pull-on-demand | "what is actually configured, concretely" | **not done** |

The raw introspection already exists: `ServiceRegistry.find(LogAppender.class)`/
`forEach(...)` returns every registered appender by name (confirmed: `LogAppender.java`
puts both plain and `CompositeLogAppender` instances into `config.serviceRegistry()` at
construction time), `CompositeLogAppender.appenders()` exposes the per-output appenders
under a route that resolved to a composite, and `LogRouter.java` puts every constructed
`LogPublisher` into the same `ServiceRegistry` too (`config.serviceRegistry().put(LogPublisher.class,
name, pub)`) - so publisher instances are already reachable, not a gap by themselves (see
open question below on whether that's actually what "we need publishers too" meant).
What's missing is a small utility that walks all of this and renders something
human-readable: name, concrete class, flags, output, encoder, per route.

## Why not just reuse `LogAlerts`

Raised and rejected already, worth writing down why: `LogAlerts` is deliberately
event-driven and transient - "this just happened," bounded, drop-oldest, meant to be
consumed by a listener or dumped after the fact. This problem is the opposite shape:
"what is true right now," queried on demand, not a thing that *happens* at a point in
time. Logback's own `StatusManager` (and Log4j2's `StatusLogger`) are actually the same
event-history shape as `LogAlerts`, not this - `LogAlerts` was explicitly modeled after
comparing the two (see `todo.md`'s draft Spring Boot issue). Reusing alerts for this
would mean synthesizing a fake "event" every time someone wants to know what an
appender's concrete class is, which is backwards - conflating "record that something
happened" with "describe what currently exists."

## Prior art already in this codebase

- **`LogResponse.Status`/`AggregateStatus`** (removed, see `git log -- LogOutput.java`,
  commit `7f25b47c`/`719e33d5`): the old `LogOutput.status()`/`LogPublisher.status()`
  pull-style health check. `AggregateStatus` (`List<Status> status, Level level`) is the
  interesting bit for this discussion - a tree-of-statuses shape - but it was married to
  health semantics (`StandardStatus.OK`/`ErrorStatus`/`QueueStatus` as a
  `MetricStatus`), which is exactly what got split out into alerts/metrics. The
  tree-walking *shape* is reusable; the health-check *purpose* it was built for is not
  what we need now.
- **`LogStatusReporter`/`LogStatusManager`** (`feature/log-status-manager`, on-hold,
  superseded by `LogAlerts`): bounded history of `StatusEvent(Instant, Class<?>, String,
  Status)` - again, event-history shaped, this is `LogAlerts`'s direct ancestor, not
  this facet.

Neither prior attempt in this codebase was actually shaped like "walk the live component
tree and describe it right now" - both were history/event shaped. This facet has no real
prior art here yet.

## Option: don't build this

A real option, not a placeholder - on the fence about whether this is worth having at
all, and "don't do it" is a strong, legitimate choice here, not just the absence of one.
Reasons it could be the right call:

- The actual pain point ("which concrete appender class got selected") is a debugging
  question, asked rarely, by the kind of person (the app's own maintainer, during setup
  or an incident) who can already answer it by reading `AppenderType`'s javadoc plus
  whatever properties/`AUTO_DETECT`/`logging.global.optimize` are in play, or by
  attaching a debugger once. A whole new cross-cutting API surface (new interface
  methods on `LogOutput`/`LogEncoder`/possibly `LogAppender`/`LogPublisher`, a
  description/report type, a naming decision, a rendering format) is a lot of permanent
  surface area for a "sometimes useful during setup" need.
- Every interface this would touch is public API - `LogOutput`/`LogEncoder` especially,
  which third-party implementations extend. Adding a method (even `default`) to them is
  a decision that outlives whatever specific report format seems right today, similar to
  how `status()` itself was added, then had to be walked back.
- The `ServiceRegistry.find(...)`/`CompositeLogAppender.appenders()` introspection
  already exists and already answers the question for anyone willing to write ~10 lines
  against it once, ad hoc - which is a meaningfully smaller commitment than a
  first-class, permanent API.

Reasons it might still be worth doing, for balance:

- It is explicitly called out as a gap left over from a deliberate three-way split
  (alerts/metrics/this) - leaving it permanently undone means the split was never
  actually completed, just two-thirds of it.
- "Attach a debugger or read code" is a real barrier for anyone evaluating Rainbow Gum
  who isn't already deep in its source - a visible "here's what's actually wired up"
  story is also a legibility/trust signal, not purely a debugging convenience.

Not resolved here - this is the first-order question, upstream of every naming/shape
question below.

## Option: revive `LogOutput`'s old method as a tree-walk, on `LogEncoder` too

The idea from the prompt: put a method back on `LogOutput` (and add the equivalent to
`LogEncoder`, which never had one), each returning a small description of itself, and
something walks `LogRouter` → `LogPublisher` → `LogAppender` (unwrapping
`CompositeLogAppender.appenders()`) → `LogOutput`/`LogEncoder`, assembling a tree.

Rough shape (illustrative, not proposed as final):

```java
interface LogOutput {
    default XxxDescription describe() {
        return XxxDescription.of(getClass().getSimpleName());
    }
}

record XxxDescription(String name, Class<?> type, List<XxxDescription> children, Map<String, String> facts) {}
```

**Open problem, explicitly flagged by the prompt: the name.** `status()` is wrong now -
it already means "health," post alerts/metrics split, and reusing it here would
reintroduce exactly the ambiguity that split was meant to resolve.

Worth being precise about *why*, since it isn't that "status" is a bad word for this
concept - Adam's point: English-wise, "status" is inherently a pull word. Nobody in the
history of work has volunteered "here is my status" unprompted; a status is something
you're *asked for*, which is exactly this facet's own shape (pull, on-demand, current
state). The word would have been a fine fit. It's disqualified here specifically because
Logback's `StatusManager` (and Log4j2's `StatusLogger`, and this project's own removed
`LogResponse.Status`/`LogStatusReporter`) already spent it on the *push*, event-history
concept instead - so "status" now reads as "the push thing" across the ecosystem this
project has to coexist with, not because the word itself was ever wrong for a pull
query. A correct word, squatted by the wrong shape.

Candidates, no favorite yet:

| candidate | reads as | concern |
|---|---|---|
| `describe()` / `Description` | plain, unambiguous | maybe too generic a word to grep for |
| `inspect()` / `Inspection` | implies looking inside, matches "attaching a debugger" framing from the todo | "inspection" sometimes implies mutation/debugging tools, not a query |
| `explain()` / `Explanation` | nice as a verb (`gum.explain()`) | "explanation" is an unusual noun for a data class |
| `report()` / `ComponentReport` | matches "static configuration report" wording already in `todo.md` | "report" has connotations of a generated document, maybe heavier than intended |
| `summary()` / `ComponentSummary` | short, calm | slightly bland |
| `introspect()` / `Introspection` | precise, matches "the raw introspection already exists" wording already used above | long, and "introspection" already has a specific meaning in reflection-heavy Java code that isn't quite this |

Also worth deciding early, independent of the name: is this a `default` method every
`LogOutput`/`LogEncoder`/`LogAppender`/`LogPublisher` implementation gets for free (like
the old `status()` was `default`), or a separate visitor/walker that inspects concrete
types from outside (via `instanceof`/pattern matching, the way
`AppenderAsModeFlagPermutationTest`-style code already does) without adding a method to
every interface at all? The `default`-method shape couples every future implementation
of these interfaces (including third-party `LogOutput`s) to this concern forever, the
way `status()` did; an external walker keeps the interfaces smaller but has to keep up
with new concrete types by hand.

## The "global config" piece

A useful report would want to show not just the per-route/per-appender tree but also
which global settings are active - `AppenderType.AUTO_DETECT`'s resolution depends on
platform, `logging.global.optimize`/`logging.global.threadlocalDisabled`/
`logging.global.appender.reentrantLock` all change appender-type resolution invisibly,
and none of them show up anywhere in a per-appender description alone.

Current state: every one of these lives as either a `static volatile boolean` on
`AbstractLogAppender` (`forceReentrantLockAppenders`, `forceNoThreadLocalAppenders`,
`globalOptimizeEnabled`) set once during `DefaultLogConfig`'s constructor via a private
`applyGlobalXProperty(properties)` method, or is read ad hoc elsewhere
(`GLOBAL_CHANGE_PROPERTY`, `GLOBAL_QUEUE_LEVEL_PROPERTY`, `GLOBAL_QUEUE_ERROR_PROPERTY`,
`GLOBAL_ANSI_DISABLE_PROPERTY`, `GLOBAL_VERBOSE`). There is no single place today that
knows "here are all the currently-active global settings" - each one is a fact you'd
have to already know to go looking for.

Two shapes raised, plus a third worth adding to the discussion:

1. **A formal `GlobalConfig` object** - explicit fields, one per global setting.
   Rejected-leaning already (per the prompt): its shape would need a change every time a
   new `logging.global.*` property is added, which given the pace of the last few weeks
   (`threadlocalDisabled`, `optimize`, `appender.reentrantLock` all landed close
   together) would mean frequent churn to a supposedly-stable type.
2. **A loose bag** (`Map<String, Object>` or `Map<String, String>`) - a snapshot, not a
   type. Cheap to extend (a new global property just adds a key, no type change), but
   loses compile-time discoverability - nothing forces a new global property to actually
   get added to the bag, the way a missing field in option 1 would at least be visible
   in the class.
3. **Not a new object at all: re-read the known `GLOBAL_*_PROPERTY` keys from
   `LogProperties` at report time.** Every one of these is already a named constant in
   `LogProperties.java` (`GLOBAL_CHANGE_PROPERTY`, `GLOBAL_OPTIMIZE_PROPERTY`, etc.) -
   the report could just hold a list of those constants (itself a small, append-only
   list that grows exactly when a new global property is added - the same maintenance
   burden as option 2's "did anyone remember to add the key" problem, but without
   introducing a second parallel model of state at all) and print `key ->
   properties().valueOrNull(key)` for each, resolving straight from the same
   `LogProperties` everything else already reads from. Doesn't capture the *derived*
   state (e.g. `globalOptimizeEnabled`'s resolved boolean after parsing/aliasing) unless
   the report also duplicates that parsing, so it's a partial answer, not a full one -
   but it sidesteps "the shape changes often" entirely, since there is no shape.

None of these three feel obviously right yet - flagging the tradeoff instead of picking.

## Open questions for discussion

- **Build this at all, or leave it as a known, accepted gap?** Genuinely open (see "don't
  build this" above) - everything below only matters if the answer is yes.
- What do we actually call this (see naming table above) - and does the *verb*
  (`describe`/`inspect`/`explain`/...) need to match the *noun* exactly, or can they
  diverge (e.g. method `describe()` returning a `ComponentReport`)?
- Default interface method (every `LogOutput`/`LogEncoder`/`LogAppender`/`LogPublisher`
  gets one) vs. an external walker using pattern matching over known concrete types?
- Does this reach `LogRouter`/`LevelResolver` too, or stop at appender/publisher/output/
  encoder? The todo's own framing ("which concrete appender class actually got
  selected") is appender-centric, but level-resolution has the same "invisible until you
  read code" problem (`CachedLevelResolver`, `CompositeLevelResolver`, etc.).
- Global config: object, bag, or re-read-from-properties (or some combination - e.g.
  re-read-from-properties for the raw values, plus a small bag for genuinely *derived*
  state that doesn't correspond to a single property 1:1)?
- What actually confirms "publishers... not easily accessible" - is
  `ServiceRegistry.find(LogPublisher.class)` (confirmed to already work, see above) not
  sufficient for some reason, or was this about something more specific (e.g. tying a
  specific publisher instance back to the specific route/appenders it's associated with,
  which `ServiceRegistry` alone doesn't encode since it's a flat name→instance map, not a
  tree)?
- Where does this get triggered from - a plain utility method a user calls
  programmatically, something printed automatically at startup (opt-in? always?), a
  Spring Boot Actuator-style endpoint for the Spring integration modules specifically, or
  more than one of these on top of one shared core representation? `todo.md`'s own text
  already flagged this as undecided.
- Format: does the "tree" need a real rendered-text form (indented, human-readable at a
  glance) as a first-class output, or is a plain data structure (that a caller can print
  however they like, or serialize to JSON for an Actuator endpoint) enough on its own?
