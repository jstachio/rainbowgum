# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running (git revision, resolved framework versions, and the concrete appender/encoder
each app actually picked at runtime - not an assumption from reading config). See
`FINDINGS.md` for the *why* behind these numbers.

## Revision under test

- **RainbowGum core**: `main` @ `2e88f94` ("Remove the virtual-thread bypass from
  ThreadLocal-buffer appenders") plus the not-yet-merged
  `feature/composite-appender-tostring` @ `19162b8` layered on top - a cosmetic
  `CompositeLogAppender.toString()` addition (no behavior change) needed to make the
  config-report endpoint below legible when a route resolves to a composite appender.
- **Benchmark harness**: this branch (`feature/webapp-benchmark`), rebased onto the same
  `main` revision above.
- **JDK**: Temurin 26.0.2 (`openjdk version "26.0.2"`) - above the JDK 24 threshold
  (JEP 491) where RainbowGum's default appender selection switches from
  `THREAD_LOCAL_BUFFER` to `SYNCHRONIZED_THREAD_LOCAL_BUFFER`.
- **Logback/Log4j2 versions**: resolved via `mvn dependency:tree` against each app's
  actual build, i.e. whatever Spring Boot 4.1.0's BOM pins - not manually chosen:
  - `ch.qos.logback:logback-classic:1.5.34` (Maven Central's current `<release>` for
    `logback-classic` is **1.6.3** - two minor versions ahead of what Spring Boot 4.1
    ships).
  - `org.apache.logging.log4j:log4j-core:2.25.4` (Central's current `<release>` is
    **3.0.0-beta3** - still beta, so staying on 2.x is expected here).
  - **Follow-up, not yet resolved**: whether RainbowGum should track/pin a specific
    Logback version for comparison purposes independent of Spring Boot's BOM, and
    whether Logback 1.6.x is even usable under Spring Boot 4 at all - unknown, needs
    checking before drawing any conclusion from the version gap.

## Confirmed appender selection

Added `GET /api/config-report` to `rainbowgum-benchmark-webapp-rainbowgum`
(`ConfigReportController`, new this run): looks up every registered appender via
`RainbowGum.of().config().serviceRegistry().find(LogAppender.class)` and prints each
one's own `toString()`, plus the concrete SLF4J `Logger` implementation bound for that
class. Captured automatically by `run-all.sh` into `results/<label>-config-report.txt`
for every `rainbowgum` run. Trimmed here to the meaningful prefix (full text, including
the level-resolver/Spring-`Environment` internals `InfoLogger.toString()` also drags in,
is in the raw files):

```
slf4j.logger=InfoLogger[..., handler=DefaultLogEventHandler[..., logger=SimpleRouter[...]], mdc=RainbowGumMDCAdapter@...]

appender=CompositeLogAppender[
  appenders=[
    SynchronizedThreadLocalBufferLogAppender[name=file,    encoder=FormatterEncoder@...,        output=ReopenableFileOutput@..., flags=[]],
    SynchronizedThreadLocalBufferLogAppender[name=console, encoder=FormatterEncoder@...,         output=StdOutOutput@...,         flags=[]]
  ],
  lock=ReentrantLock@...[Unlocked]
]
```

(GELF run: `file`'s encoder reads `GelfEncoder@...` instead of `FormatterEncoder@...`;
`console` is unaffected, matching `GelfSpringRainbowGumServiceProvider` only swapping the
`FILE` output type's encoder. VT run: identical to the default above - confirms the
appender selection is JDK-version-gated only, not virtual-thread-aware, per
`FINDINGS.md`.)

Two things confirmed directly rather than assumed: **no explicit flags are set**
(`flags=[]]`) - this route is running purely on the JDK-version-sniffed default, not an
opt-in flag - and the bound SLF4J logger is `InfoLogger` (RainbowGum's dedicated
level-checked logger, not a slower generic dispatcher).

## Numbers

Driver: 50 virtual-thread workers, 10s warmup (discarded) + 30s measured, closed-loop
against `GET /api/greet/world` (or the GELF/VT variants below).

| label | req/s | p50 ms | p90 ms | p99 ms | max ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|
| logback | 21,944.5 | 2.02 | 4.50 | 7.57 | 22.17 | 688.8 |
| log4j2 | 33,365.1 | 1.34 | 2.77 | 4.85 | 23.59 | 684.0 |
| rainbowgum | 29,274.4 | 1.61 | 3.06 | 4.88 | 21.26 | 657.0 |
| logback-gelf | 21,551.8 | 2.10 | 4.02 | 6.65 | 22.56 | 628.7 |
| log4j2-gelf | 16,525.5 | 2.64 | 5.90 | 9.84 | 26.31 | 654.7 |
| rainbowgum-gelf | 24,274.3 | 1.90 | 3.79 | 6.28 | 24.02 | 653.6 |
| logback-vt | 25,710.7 | 1.95 | 2.63 | 3.14 | 12.66 | 644.6 |
| log4j2-vt | 18,889.0 | 2.47 | 5.14 | 7.77 | 21.98 | 639.0 |
| rainbowgum-vt | 19,346.0 | 2.64 | 4.52 | 6.52 | 19.77 | 676.8 |

JFR `jdk.GCHeapSummary` event counts for the VT scenario (per-app GC pressure, cheap
proxy for allocation rate under 50-way virtual-thread concurrency):
logback-vt 256, log4j2-vt 274, rainbowgum-vt 420.

This run's default-scenario spread (log4j2 well ahead of both logback and rainbowgum) is
wider than earlier runs recorded in this file's history: this is a shared, noisy sandbox
machine, and run-to-run variance of this size has shown up before - treat single-run
absolute numbers cautiously, the GELF/VT relative pictures (which match prior runs) are
more trustworthy than this run's default-scenario absolute ranking.

## Reproducing

```
cd benchmark/webapp
./run-all.sh                       # default (platform threads, pattern encoder)
STRUCTURED_FORMAT=gelf ./run-all.sh
VIRTUAL_THREADS=true ./run-all.sh
```

Results land in `results/` (gitignored): `results.csv` (all numeric results, appended
across runs), `<label>-jfr.txt` (GC/allocation events), `<label>-stdout.log` (app
console output), `<label>-config-report.txt` (rainbowgum only - the appender/logger dump
above).
