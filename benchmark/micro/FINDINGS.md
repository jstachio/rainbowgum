# SLF4J logging microbenchmark findings

A from-scratch microbenchmark comparing Logback, Log4j2, and RainbowGum through the plain
SLF4J facade (not log4j2's own native API), console output, no Spring, no MDC (deferred).
Deliberately separate from the older, simpler `benchmark/rainbowgum-benchmark-{logback,
log4j,rainbowgum,share}` modules (not edited) - this one tests multiple message shapes,
disabled-level cost, and the SLF4J 2.x fluent `EventBuilder` API, across single-threaded,
platform-thread, and virtual-thread execution. See `run-all.sh` to reproduce.

## Setup

- **Pattern**: identical literal pattern string on all three -
  `%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n` (Time/Thread/Level/Logger - "TTLL",
  RainbowGum's own name for this layout, confirmed from `AbstractStandardEventFormatter`'s
  javadoc example). RainbowGum has a purpose-built native `ttll` encoder that's likely
  faster than pattern parsing, but deliberately **not** used here - Logback/Log4j2 have no
  such shortcut and must always go through their pattern engines, so RainbowGum goes
  through its pattern engine (`rainbowgum-pattern`) too, for a fair comparison of the thing
  all three actually have to do.
- **`immediateFlush` explicitly on for all three** (Logback/Log4j2 default to this anyway;
  RainbowGum via `logging.appender.console.flags=immediate_flush`), so none of the earlier
  buffered-vs-flush-every-line asymmetry from the webapp benchmark applies here.
- **Root level INFO** on all three, via each framework's native config (`logback.xml`,
  `log4j2.xml`, RainbowGum's `logging.level=INFO` system property - confirmed this is
  RainbowGum's bare root-level key, `LogProperties.LEVEL_PREFIX` used without a logger-name
  suffix).
- **Scenarios** (`Scenario.java`): `NO_ARG`, `ONE_ARG`, `TWO_ARG`, `THREE_ARG` (forces
  SLF4J's varargs overload), `LEVEL_CHECK` (just `isDebugEnabled()`, sunk into a counter to
  block dead-code elimination), `DISABLED` (a full DEBUG call when DEBUG is disabled),
  `EVENT_BUILDER` (`log.atInfo().setMessage(...).addArgument(...).log()`).
- **Threading modes**: `SINGLE` (one caller thread, examines the pure pipeline),
  `PLATFORM` (fixed pool, 16 workers by default), `VIRTUAL` (one virtual thread per task,
  16 concurrent tasks by default).
- Each combination: 5,000 ops/worker warmup (discarded), 20,000 ops/worker measured.
  Console output for each framework goes to `results/<name>-out.log`; per-combination
  report and the shared CSV go to `results/<name>-report.log` / `results/results.csv`.

## Results (default sizes: warmup 5000, measure 20000, concurrency 16)

Ops/second, all three frameworks, all three threading modes:

| scenario | mode | logback | log4j2 | rainbowgum |
|---|---|---:|---:|---:|
| NO_ARG | SINGLE | 666,516 | 594,199 | 579,183 |
| NO_ARG | PLATFORM | 638,443 | 743,387 | 612,049 |
| NO_ARG | VIRTUAL | 200,624 | 249,783 | 198,090 |
| ONE_ARG | SINGLE | 612,275 | 814,137 | 666,099 |
| ONE_ARG | PLATFORM | 622,981 | 752,724 | 592,606 |
| ONE_ARG | VIRTUAL | 194,281 | 237,514 | 199,893 |
| TWO_ARG | SINGLE | 620,553 | 941,684 | 812,088 |
| TWO_ARG | PLATFORM | 579,215 | 766,683 | 676,910 |
| TWO_ARG | VIRTUAL | 195,038 | 257,257 | 197,828 |
| **THREE_ARG** | SINGLE | 886,781 | 936,870 | **438,903** |
| **THREE_ARG** | PLATFORM | 594,657 | 767,007 | **526,717** |
| THREE_ARG | VIRTUAL | 191,592 | 236,416 | 193,036 |
| **LEVEL_CHECK** | SINGLE | 38,116,583 | 29,782,380 | **179,933,784** |
| **LEVEL_CHECK** | PLATFORM | 142,149,063 | 149,770,804 | **138,836,464** |
| **LEVEL_CHECK** | VIRTUAL | 1,552,546,419 | 1,101,863,872 | **1,910,938,330** |
| **DISABLED** | SINGLE | 38,903,164 | 20,000,920 | **187,460,750** |
| **DISABLED** | PLATFORM | 102,953,975 | 192,214,819 | **76,855,477** |
| DISABLED | VIRTUAL | 118,198,769 | 1,230,617,770 | 78,579,074 |
| EVENT_BUILDER | SINGLE | 704,580 | 677,788 | **976,325** |
| EVENT_BUILDER | PLATFORM | 579,168 | 740,874 | 569,640 |
| EVENT_BUILDER | VIRTUAL | 201,340 | 197,416 | 197,416 |

## Finding 1: RainbowGum's disabled/level-check path is dramatically faster (single-threaded)

`LEVEL_CHECK` and `DISABLED`, `SINGLE` mode: RainbowGum is **~5-9x faster** than both
Logback and Log4j2 (180M vs 38M/30M ops/sec for `LEVEL_CHECK`; 187M vs 39M/20M for
`DISABLED`). This is a clean, uncontended measurement of exactly the mechanism confirmed
earlier this session in `core/.../LevelResolver.java`: `LevelResolver.Builder.build()`
always wraps the result in `CachedLevelResolver` (a plain `ConcurrentHashMap<String,Level>`
lookup), and it's paying off enormously here. Matches the general "RainbowGum is fast for
the disabled case" reputation and directly reproduces (without any Spring/Tomcat noise)
the ERROR-level convergence seen in the webapp benchmark - except here, isolated from HTTP/
Tomcat overhead, RainbowGum's edge is actually *visible* rather than getting drowned out.

Under `PLATFORM`/`VIRTUAL` concurrency this edge mostly evaporates or reverses (`DISABLED`
under `PLATFORM`: RainbowGum 76.9M vs Log4j2's 192.2M) - likely `ConcurrentHashMap.get`
contention/cache-line effects at 16-way concurrency versus whatever Logback/Log4j2 do
(Logback: plain field checks via `Level` comparison in generated logger classes; Log4j2:
similar). Not investigated further here - flagged as a real, opposite-direction data point
worth keeping in mind alongside the SINGLE-mode result.

## Finding 2: RainbowGum's 3+-argument call path is notably slower

`THREE_ARG` (forces SLF4J's varargs overload) is the one "real formatting" scenario where
RainbowGum is clearly behind, both `SINGLE` (438,903 vs 886,781/936,870 - roughly half) and
`PLATFORM` (526,717 vs 594,657/767,007). `ONE_ARG`/`TWO_ARG` don't show this gap (RainbowGum
keeps pace with or beats Logback there), so it's specific to 3+ args. Traced the code path:

- `event1`/`event2` (`EventCreator.java`) build specialized `OneArgLogEvent`/
  `TwoArgLogEvent` records (`LogEvent.java`) holding `arg1`/`arg2` as direct fields.
- `eventArray` (used for 3+ args) calls `LogEvent.ofArgs(...)` -> `ofAll(...)`, whose
  `switch` only produces `OneArgLogEvent`/`TwoArgLogEvent` for `length` 1/2 *after* args has
  already arrived as an `Object[]`/`List`; for length >= 3 it falls through to
  `ArrayArgLogEvent`, which holds the raw array and formats via
  `messageFormatter.formatArray(sb, message, args, length)` - a generic,
  array-and-length-based method on `LogMessageFormatter`, structurally different from
  whatever code path `OneArgLogEvent`/`TwoArgLogEvent` use for their fixed-arity case.

Not traced further into `LogMessageFormatter.StandardMessageFormatter.SLF4J.formatArray`
itself to find the exact hot line - flagging the code path (`ArrayArgLogEvent` /
`formatArray` vs the specialized 1/2-arg records) as the concrete, reproducible lead.

## Finding 3: virtual threads hurt all three roughly equally here, not just RainbowGum

Every "real formatting" scenario (`NO_ARG` through `THREE_ARG`, `EVENT_BUILDER`) drops by
roughly 3x under `VIRTUAL` vs `PLATFORM` mode, for **all three frameworks essentially
equally** (e.g. `NO_ARG`: Logback 638K->201K, Log4j2 743K->250K, RainbowGum 612K->198K -
all ~3.1x). This is unlike the webapp benchmark's virtual-threads finding, where Log4j2
specifically regressed while Logback improved - here, with `immediateFlush` forcing every
write through a shared, contended sink (`System.out`), virtual threads contending on that
one shared resource seems to cost about the same regardless of which framework is doing the
contending. Consistent with 12-factor reasoning for treating console/synchronous logging as
low priority for virtual-thread optimization work generally, at least for the plain
console-write case tested here (no HTTP/Tomcat layer in this benchmark to interact with, so
this doesn't speak to the webapp benchmark's Log4j2-specific virtual-thread regression -
that's presumably from something in Log4j2's own thread-local caching machinery which this
microbenchmark's simple scenarios may not exercise the same way).

## Finding 4: RainbowGum's `EVENT_BUILDER` path is competitive, even fastest single-threaded

`EVENT_BUILDER`, `SINGLE`: RainbowGum 976,325 vs Logback 704,580 / Log4j2 677,788 -
RainbowGum's fluent API implementation isn't a naive/slow bolt-on despite likely being the
newest/least-used code path of the three (SLF4J's fluent API is the newest addition to the
spec). Under concurrency the three converge to roughly the same range as the other
formatting scenarios.

## Not yet done

- No MDC (explicitly deferred per Adam - this benchmark tests the plain call shapes first).
- Haven't varied concurrency (only tested at 16) or run repeated trials to check
  reproducibility of any of these numbers the way the webapp benchmark's findings were
  cross-checked with reruns.
- Haven't profiled this microbenchmark with JFR yet - the code-path leads above (Finding 1's
  concurrent-mode reversal, Finding 2's `ArrayArgLogEvent`/`formatArray` path) are both good
  next targets for that, now that there's no Tomcat/Spring noise to filter out first.

Reproduce: `./run-all.sh` (env vars `WARMUP`, `MEASURE`, `CONCURRENCY` override the
defaults). Each framework module also has its own `run.sh` for running just one in
isolation, with `-Dbench.*` system properties for quick/targeted runs.
