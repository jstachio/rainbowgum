# Global properties refactor

Working notes for reworking Rainbow Gum's global and cross-cutting properties. They record
what each property does today, the problems found, and the decisions made so far, so
anyone (human or agent) picking this up has the reasons and not just the diffs.

Branch: `refactor/global-properties`.

## Why

`fix/strict-booleans` made `LogProperty.ofBoolean` reject anything but `true`/`false`.
Adam held it back because it exposed a bigger problem with the globals:

- Many are booleans. A boolean parses badly (`yes` silently means false), allows only two
  choices, and pushes names toward negatives like `disable`.
- They are awkward to set: most have no `LogConfig.Builder` method, and integrations
  inject them as properties.
- Some are read again and again by different readers, with different defaults.
- Some write process-wide statics from a per-config property.
- They differ in when they must be read. Some must be read before anything loads, some
  once per config, some per logger.

Making them all enums is the obvious fix, but each needs a proper home first, decided by
its lifecycle. Removing `ofBoolean` entirely is under consideration.

## Inventory by lifecycle

### 1. Before any `LogProperties` exist (system properties only)

| Key | Type | Read where and when |
|---|---|---|
| `logging.global.queue.level`, `logging.global.queue.error` | Level | `QueueEventsRouter.of()`: in the global router's static initializer, and again on close. |
| `logging.debug` | `DebugModeType` enum | First line of `LogConfig.Builder.build()`. |
| `logging.systemlogger.initialize` | `InitOption` enum | On the first `System.Logger`, through `findGlobalProperties()`: the bound gum's properties, else system properties. Cached. |

These really must be read before anything loads, so they cannot be builder methods.

### 2. Once per `LogConfig` (in `Builder.build()` and the `DefaultLogConfig` constructor)

| Key | Type | Effect |
|---|---|---|
| `logging.change.mode` (was `logging.global.change`) | `ChangeMode` enum | See the decision below. |
| `logging.global.appender.reentrantLock` | `ofBoolean` | Writes the static `AbstractLogAppender.forceReentrantLockAppenders`. |
| `logging.global.threadlocalDisabled` | private TRUE/FALSE enum | Writes a static `forceNoThreadLocalAppenders`. |
| `logging.global.optimize` | private TRUE/FALSE enum | Writes the static `globalOptimizeEnabled`, read when appenders and encoders are built. |
| `logging.keyvalues.disabled` | list of enum | Stored in the service registry. |
| `logging.alerts.fail`, `logging.alerts.unusedKey` | enums | Read twice: raw `valueOrNull` to decide on key read recording, then in `DefaultLogAlerts.of`. |
| `logging.alerts.unobservedErrorsAction`, `logging.alerts.capacity` | enum, int | `DefaultLogAlerts.of`. |

### 3. Once per config, in a module's `Configurator`

| Key | Parse | Module |
|---|---|---|
| `logging.jansi.disable` | raw `Boolean.parseBoolean(valueOrNull)`: no error, no source, no unused key tracking | jansi |
| `logging.jul.disable` | `ofBoolean` | jul |
| `logging.pattern.disable` | `ofBoolean` | pattern (whether it becomes the default console encoder) |
| `logging.jfr.alerts.enabled` | generated `Boolean` | jfr |

### 4. Read many times while components are built

`logging.global.ansi.disable` (`ofBoolean`) has four readers that disagree:

- `LogEncoderRegistry.ansiDisabled`: default `false`, once per default TTLL or JSON5
  encoder built.
- `PatternCompiler`: default `!AnsiSupport.isAnsiSupported()`, so a missing key means
  "auto".
- `JAnsiConfigurator`: default `false`.
- The Spring Boot integration writes it from `spring.output.ansi.enabled`.

It is really three states (auto, on, off) disguised as a boolean.

### 5. Facade initialization and per logger

- SLF4J `initialize()` reads `logging.mdc.type` and reads
  `logging.global.threadlocalDisabled` again, with its own copy of the key string and
  its own TRUE/FALSE enum.
- `logging.change.logger.*` and `logging.caller.logger.*` are read per logger name, cached, and
  cleared on publish. These are fine as they are.
- `LogReporter` reads the global keys as strings only to print them.

### Component booleans (not global, same parse problem)

Generated builder parameters: `file` `append`/`prudent`, `rolling`
`prudent`/`cleanHistoryOnStart`, `ecs` `structured`/`prettyPrint`, `prettyPrint` on gelf,
logstash and logback, pattern config `ansiDisabled`, and `LogEncoder.Builder.useGetBytes`.
Black-box QA hit these: `append=yes` truncated a log file, and `structured=yes` gave the
flat layout.

## Problems

1. Silent boolean parsing (listed above).
2. One setting with many readers that disagree: ANSI, `threadlocalDisabled`.
3. Per-config properties writing process-wide statics, last writer wins:
   `reentrantLock`, `threadlocalDisabled`, `optimize`. None of them has a builder method.
4. Negative names: five `disable` or `disabled` keys.
5. Raw `valueOrNull` reads that bypass the property machinery: `jansi.disable` and part
   of the alerts keys.

## Decision: change mode

`logging.global.change` was a boolean that only ever unlocked `logging.change.*`, and every
integration (Spring Boot pre-boot, Micronaut, the simple-props level file) set it together
with `logging.change=level`. It also silently gated caller info: with the global off,
`logging.caller.*` did nothing.

Dropping the global and letting the per-logger property decide alone was rejected. Turning
change on and choosing what may change are separate decisions: change being on must not
mean every logger can change.

New keys:

```properties
# whole config: are runtime changes on (off | on, default off; true/false are aliases)
logging.change.mode=on
# per logger: what may change, walked up the logger name hierarchy as before
logging.change.logger=level
logging.change.logger.com.mycompany=level
```

- **Why a `logger` sub-namespace:** `logging.change.<name>` was the per-logger namespace,
  so `logging.change.mode` would collide with a logger named `mode`. This follows the
  nested typed key decision (`{component}.uri` plus `{component}.{scheme}.*`). A side
  benefit: the unused key check now only exempts `logging.change.logger.*`, so a stale
  `logging.change.com.foo` or `logging.global.change` is reported.
- **When it is read:** once at config build, like the old global. What is really early is
  a logger's own change setting: facades decide once, when a logger is created, whether
  it can change (SLF4J `LevelLogger` versus `ReplaceableLogger`). That is why Spring's
  pre-boot config and the Micronaut and level file layers must be in place from the
  first config.
- **`LogConfig.Builder.changeMode(ChangeMode)`:** the default when the property is absent,
  the same pattern as `alertsFail`.
- **Caller info is no longer gated.** `logging.caller.logger.*` applies whatever the mode. When
  the mode is off, each new logger name now costs one cached property walk for caller,
  which `IgnoreChangePublisher` used to skip. `IgnoreChangePublisher` is gone; the one
  publisher checks the mode.
- **Off means:** `allowedChanges` is always empty, and `subscribe` and `publish` do
  nothing (so publishing does not clear the level cache), the same as before.
- No compatibility alias for the old keys (pre-1.0, no deprecation).
- **Caller keys follow the same shape.** `logging.caller.<name>` moved to
  `logging.caller.logger.<name>` (bare `logging.caller.logger` for the root), leaving
  room for caller settings that are not per logger. The unused key check now exempts
  `logging.caller.logger.*`; before, per logger caller keys were not exempt even though
  they are read lazily like level and change keys.

## Decision: the reporter lives on the config

`LogReporter` used to be built standalone (`LogReporter.builder()...build().report(gum)`)
and printed a fixed static list of "global" keys. That list was a utility class in
disguise, and nobody could know which properties belonged on it.

Now (branch `feature/config-reporter`, based on this one):

- `LogConfig.reporter()` returns the config's own reporter. The router is bound to it
  when a `RainbowGum` is built from the config; before that, `COMPONENTS` says no
  RainbowGum is built yet.
- Rendering options moved to `LogReporter.Options` (`Options.builder()`,
  `Options.of(section)`), passed per report: `config.reporter().report(options)`, or
  `report()` for the defaults.
- `addProperty(Class<?> source, String key)`: whoever reads a property worth reporting
  registers it. The report lists keys sorted, each with its current value and the
  packages that added it, so a key read in several places shows every reader (for
  example `logging.global.ansi.disable` from core and from pattern). The package, not
  the module name, so output is the same on the class path and the module path.
- Registered today: core's config properties and the queue properties (from
  `DefaultLogConfig`), ANSI (core encoder registry, pattern compiler, jansi),
  `logging.pattern.disable`, `logging.jul.disable` and `logging.jansi.disable`. Module
  switches therefore only appear when the module is in use.

Open: whether the report should also show the resolved value (with builder defaults),
since `(unset)` can hide a default set through `LogConfig.Builder`.

## Open items

- **Avaje config:** it calls `changePublisher().publish()` on config changes, but does
  not set `logging.change.mode=on`, so publishing does nothing unless the user turns it on.
  Should it supply `mode=on` as a lowest precedence default like the level file? Per-logger
  changes would still default to none.
- **ANSI:** become one `auto | on | off` setting with one reader and one default, and
  decide how it relates to the formatters' color mode and `NO_COLOR`.
- **Statics:** whether `reentrantLock`, `threadlocalDisabled` and `optimize` stay
  process-wide, or become config values with a static only for the bound gum. SLF4J should
  stop parsing `threadlocalDisabled` itself.
- **Module switches** (`jansi.disable`, `jul.disable`, `pattern.disable`,
  `jfr.alerts.enabled`): enums on positive keys, read by their own configurator.
- **A resolved options object:** stages 2 and 4 could be resolved once into one immutable
  config-scoped value with builder methods, and every reader asks it instead of
  reparsing properties.
- **`ofBoolean`:** remove it once nothing needs it; `fix/strict-booleans` is on hold
  until then.
- **Component booleans:** the generated builder parameters listed above.
