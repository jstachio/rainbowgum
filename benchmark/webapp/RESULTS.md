# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running (git revision, resolved framework versions, and the concrete appender/encoder
each app actually picked at runtime - not an assumption from reading config). See
`FINDINGS.md` for the *why* behind these numbers.

## Revision under test

- **RainbowGum core**: `main` @ `2e88f94` ("Remove the virtual-thread bypass from
  ThreadLocal-buffer appenders") plus the not-yet-merged
  `refactor/remove-composite-appender-lock` @ `1ec4305` layered on top - removes
  `CompositeLogAppender`'s now-pointless `ReentrantLock` (each component already has its
  own; nothing at the composite level needed protecting) and the `BaseComposite`
  interface it existed for, and adds a `toString()` override so the config-report
  endpoint below is legible for a composite route instead of printing an array identity
  hash.
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
  ]
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

**Both `file` and `console` write on every log call, in every scenario, including
GELF.** `CompositeLogAppender.append()` loops over every element of `appenders` and
calls each one directly, unconditionally - there's no scenario where only one component
of a composite route fires. So the "GELF" scenario is never GELF/JSON in isolation: it's
GELF-to-file plus plain-pattern-to-console together, same total I/O shape as the
default/VT scenarios, with only the file side's encoding format changing. Confirmed both
from this code path and directly from the captured config-report above (`file`'s encoder
is `GelfEncoder`, `console`'s stays `FormatterEncoder`, in the same `appenders` array).
This is symmetric across all three frameworks - Spring Boot's `logging.structured.format.file`
property is file-only by design, so Logback/Log4j2's console output is unaffected the
same way.

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
| logback-gelf-vt | 25,206.2 | 1.98 | 2.67 | 3.53 | 14.65 | 633.5 |
| log4j2-gelf-vt | 18,325.5 | 2.52 | 4.76 | 7.02 | 28.01 | 647.9 |
| rainbowgum-gelf-vt | 18,917.3 | 2.66 | 4.61 | 6.58 | 21.68 | 664.1 |

JFR `jdk.GCHeapSummary` event counts (per-app GC pressure, cheap proxy for allocation
rate under 50-way virtual-thread concurrency):

| scenario | logback | log4j2 | rainbowgum |
|---|---:|---:|---:|
| vt | 256 | 274 | 420 |
| gelf-vt | 652 | 490 | 412 |

GELF+VT combined (`STRUCTURED_FORMAT=gelf VIRTUAL_THREADS=true`) doesn't compound the two
individual regressions - all three land close to their plain-VT numbers (rainbowgum
actually edges past log4j2-vt in this combination, 18,917 vs 18,325).

Logback's GC event count roughly doubles under GELF+VT versus plain VT (256 -> 652),
while log4j2's rises more modestly (274 -> 490) and rainbowgum's is flat (420 -> 412).
**Open question, not yet explained**: checked whether this was the same
`String.getBytes()`/Latin1-fast-path story as `FINDINGS.md` and it is not - decompiling
Spring Boot 4.1's structured-logging support
(`org.springframework.boot.logging.structured.JsonWriterStructuredLogFormatter`/
`AppendableByteArray`) shows Logback's and Log4j2's GELF encoders both go through the
*same shared* Spring Boot JSON writer, which itself uses a `ThreadLocal`-cached
`CharsetEncoder`-based `AppendableByteArray` - not Logback's own bare-`getBytes()`
pattern-layout path at all. So whatever is costing Logback more GC pressure under GELF
specifically is a different mechanism (larger per-event byte count, `AppendableByteArray`
buffer-expansion behavior, JSON member-building allocation - not yet isolated). Flagging
rather than guessing further, per this project's practice of confirming rather than
assuming performance causes.

This run's default-scenario spread (log4j2 well ahead of both logback and rainbowgum) is
wider than earlier runs recorded in this file's history: this is a shared, noisy sandbox
machine, and run-to-run variance of this size has shown up before - treat single-run
absolute numbers cautiously, the GELF/VT relative pictures (which match prior runs) are
more trustworthy than this run's default-scenario absolute ranking.

## Reproducing

```
cd benchmark/webapp
./run-all.sh                                          # default (platform threads, pattern encoder)
STRUCTURED_FORMAT=gelf ./run-all.sh
VIRTUAL_THREADS=true ./run-all.sh
STRUCTURED_FORMAT=gelf VIRTUAL_THREADS=true ./run-all.sh  # combined
```

Results land in `results/` (gitignored): `results.csv` (all numeric results, appended
across runs), `<label>-jfr.txt` (GC/allocation events), `<label>-stdout.log` (app
console output), `<label>-config-report.txt` (rainbowgum only - the appender/logger dump
above).
