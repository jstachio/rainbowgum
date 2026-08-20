# Webapp benchmark findings

Notes from setting up and running the Spring Boot 4 REST benchmark suite in this directory
(Logback vs Log4j2 vs RainbowGum, one app per backend, shared controller + MDC filter, load
driven by `rainbowgum-benchmark-webapp-driver`). See `run-all.sh` to reproduce.

## Finding: `rainbowgum-jansi`'s console output was ~9x slower under load

**Symptom:** in an early run, the RainbowGum app measured ~1750 req/s / p50 4.7ms, against
~17800-18800 req/s / p50 0.37-0.40ms for Logback and Log4j2 at the same concurrency (8).
RainbowGum's raw (non-Spring) microbenchmark (`benchmark/rainbowgum-benchmark-rainbowgum`)
has never shown anything like this gap, so the cause had to be specific to what the Spring
Boot integration pulls in, not the core logging engine.

**Root cause:** `rainbowgum-spring-boot4-starter` depends on the `rainbowgum` umbrella
artifact, which transitively pulls in `rainbowgum-jansi` (`org.jline:jansi-core`) at
runtime. That module wraps `System.out` in a jansi `AnsiOutputStream` for colored console
output. Confirmed via JFR (`jcmd <pid> JFR.dump`, then
`jfr print --events jdk.FileWrite`) that individual log writes were going through
`org.jline.jansi.io.AnsiOutputStream.write(int)` **one byte at a time**, each call costing
2.5-9ms:

```
jdk.FileWrite {
  duration = 8.74 ms
  bytesWritten = 1 byte
  eventThread = "http-nio-8080-exec-5"
  stackTrace = [
    java.io.FilterOutputStream.write(int) line: 89
    org.jline.jansi.io.AnsiOutputStream.write(int) line: 164
    java.io.FilterOutputStream.write(byte[], int, int) line: 139
    java.io.PrintStream.write(byte[], int, int) line: 536
    io.jstach.rainbowgum.LogOutput$AbstractOutputStreamOutput.write(...) line: 439
    ...
  ]
}
```

A single one of these blocking single-byte writes per request was enough to account for
the entire per-request latency gap. The raw microbenchmark never sees this because it
depends only on `rainbowgum-slf4j`, not the `rainbowgum` umbrella artifact, so
`rainbowgum-jansi` was never on its classpath there.

**Fix applied here:** excluded `io.jstach.rainbowgum:rainbowgum-jansi` from
`rainbowgum-spring-boot4-starter` in `rainbowgum-benchmark-webapp-rainbowgum/pom.xml`. This
also matches Adam's plan to stop pulling jansi in by default going forward, since it's a
native dependency. After the exclusion, RainbowGum lands in the same ballpark as Logback
and Log4j2 (see results below) - actually with the lowest RSS of the three.

**Follow-up worth considering:** independent of this benchmark, `AnsiOutputStream.write()`
lacking a bulk `write(byte[], int, int)` fast path (falling back to per-byte iteration) is a
real perf trap for any real app that pulls in the full `rainbowgum` artifact with console
output enabled - not just this benchmark. Since jansi is being dropped from the default set
of dependencies anyway, this is probably moot, but noting it here in case that plan changes.

**Is this fixed upstream?** No. Checked both the exact jar in use (`org.jline:jansi-core:4.3.1`,
also the latest on Maven Central, decompiled locally) and the current `master` of
[jline/jline3](https://github.com/jline/jline3/blob/master/jansi-core/src/main/java/org/jline/jansi/io/AnsiOutputStream.java)
on GitHub: `AnsiOutputStream` still only overrides `write(int)`, never `write(byte[], int, int)`,
so it inherits `FilterOutputStream`'s byte-at-a-time default unconditionally. It does now have
a proper `AnsiType` enum (`Native`/`VirtualTerminal`/`Emulation`/`Redirected`/`Unsupported`) that
correctly detects when a terminal understands ANSI natively - but that field is only consulted
in `uninstall()` (whether to emit a reset code), never to skip the per-byte parse even when the
type is `Native` and there's nothing to strip or emulate. No open upstream issue found tracking
this specifically (two related closed issues, #137 and #143 on `jline/jline3`, are about
Windows console attribute-call batching from 2017, not this).

**How do Logback/Log4j2 avoid this?** They don't use jansi at all for Spring Boot's default
colored console output - confirmed both apps' dependency trees have zero `org.jline`/
`org.fusesource.jansi` artifacts. Their `%clr` pattern converters go through Spring Boot's own
`org.springframework.boot.ansi.AnsiOutput` (decompiled and confirmed: `toString(Object...)`
just appends literal ANSI escape bytes into a `StringBuilder`), so the escape codes are just
ordinary characters in the formatted line by the time it reaches the (buffered) output stream -
no stream-level ANSI processor, no per-byte parsing, no overhead.

## Latest run (full-length, default settings)

`./run-all.sh` with defaults: 10s warmup / 30s measurement / 50 concurrency, single machine,
single trial per app (still not repeated-trial rigorous - treat as a first real data point,
not a final number). This run has the jansi exclusion fix from the finding above applied.

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 643,284  | 21,442.80 | 2.03   | 4.71   | 8.00   | 22.62  | 2.33    | 613.7 / 618.1 / 616.1 |
| log4j2     | 1,031,229| 34,374.30 | 1.29   | 2.71   | 4.88   | 21.13  | 1.45    | 639.1 / 645.5 / 642.9 |
| rainbowgum | 706,123  | 23,537.43 | 2.12   | 4.00   | 6.68   | 25.78  | 2.12    | 662.6 / 669.4 / 665.5 |

At real concurrency (50, vs the 8 used in the short validation run below), RainbowGum
outperforms Logback on throughput (23,537 vs 21,443 req/s) and p99 (6.68 vs 8.00ms), landing
between Logback and Log4j2 on every latency percentile - Log4j2 is clearly fastest of the
three here. RSS is highest for RainbowGum this time (665.5MB avg vs 616.1/642.9), the
opposite ordering from the short run below - worth another trial or two before reading much
into the memory ordering specifically, since a single sample at each concurrency level isn't
enough to call that a stable difference vs run-to-run/GC-timing noise.

## Finding: at ERROR level (mostly-noop logging), all three frameworks converge

Adam's hypothesis for Log4j2's ~50% throughput edge above: its own date-handling (rather
than going through `Instant`) and its lock strategy for the append path (`synchronized`
rather than a `Lock`, previously bad under virtual threads pre-pinning-fix, reportedly fixed
in current JDKs). To isolate whether the gap is in the *enabled* (format+write) path or the
*disabled* (level-check/dispatch) path, re-ran with `logging.level.root=ERROR`
(`LOG_LEVEL=ERROR ./run-all.sh` - see below), which turns every one of the controller's 5
INFO + 1 DEBUG statements into a no-op level check with nothing formatted or written.

| label      | requests  | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|----------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 2,440,995 | 81,366.50 | 0.52   | 0.91   | 2.35   | 7.42   | 0.61    | 663.5 / 665.2 / 664.5 |
| log4j2     | 2,434,370 | 81,145.67 | 0.52   | 0.91   | 2.42   | 7.57   | 0.62    | 643.8 / 645.6 / 644.9 |
| rainbowgum | 2,429,237 | 80,974.57 | 0.52   | 0.93   | 2.37   | 7.39   | 0.62    | 629.2 / 635.1 / 633.6 |

All three land within ~0.5% of each other on throughput and identically on p50 - i.e. noise,
not signal. That's a clean confirmation of the hypothesis above: whatever gives Log4j2 its
edge at INFO level is entirely in the *enabled* path (formatting a timestamp, building the
line, writing it), not in the cost of checking whether a level is enabled or dispatching
through each framework's SLF4J binding - those are already effectively equal. Worth actually
profiling the enabled path next (JFR execution samples, comparing time spent in date
formatting specifically) to confirm the date-handling guess rather than assume it.

Reproduce: `LOG_LEVEL=ERROR ./run-all.sh` (see `run-all.sh` for other env vars). Rows land in
the same `results/results.csv` as the default-level run, distinguished by an `-ERROR` label
suffix, so both scenarios accumulate in one file across runs rather than overwriting.

## Sanity check: is any framework doing less work than the others?

Before trusting throughput numbers, worth confirming none of the three is "winning" by
silently producing smaller/incomplete output. Ran `WARMUP_SECONDS=1 DURATION_SECONDS=3
CONCURRENCY=1 ./run-all.sh` (single client thread, so the actual `benchmark.log` content per
app is small enough to inspect directly) and diffed the three apps' log files:

- Every request produces exactly 5 `BenchController` lines in all three logs, and the 5
  message types (`received`/`validating`/`step=1`/`step=2`/`returning`) each appear exactly
  once per request with no drops or duplicates - counts divide evenly by 5 in every file.
- The computed values (`step1=3512882862`, `step2=3512882859`) are byte-identical across all
  three, as expected (deterministic given the same input).
- DEBUG line count is 0 in all three files - level filtering behaves identically everywhere.
- Average bytes/line: Logback 116.4, Log4j2 116.4, **RainbowGum 126.4** - RainbowGum's
  `%X{requestId}` renders as `requestId=2700` vs the other two's bare `2700`, so it's
  actually writing *more* bytes per line, not fewer. Whatever explains RainbowGum landing
  between Logback and Log4j2 in the full-length run above, it isn't doing less work.

No sign of any framework taking a shortcut. Numbers at concurrency 1 for reference (not
really informative on their own - too low concurrency to say much beyond "nothing's on
fire"): Logback 875.7 req/s, Log4j2 868.0 req/s, RainbowGum 765.0 req/s.

## Important caveat: the `%X{requestId}` pattern plays to RainbowGum's *weakest* MDC access pattern

Per Adam: RainbowGum's MDC isn't designed for repeated single-key lookup - it's designed
for the common production case of dumping the whole context to JSON in one pass. Verified
the three frameworks' actual backing structures and single-key lookup complexity directly
(bytecode/source, not just going by docs):

- **RainbowGum** (`core/.../KeyValues.java`, `AbstractArrayKeyValues`): a flat `String[]`
  (the class's own apiNote literally says "a simple single String array"). `getValueOrNull(key)`
  is a linear scan with `Objects.equals` per entry - **O(n)**.
- **Logback** (`LogbackMDCAdapter`): `ThreadLocal<Map<String,String>>`, a real `HashMap` -
  **O(1)** average per-key lookup.
- **Log4j2** (`SortedArrayStringMap`): `indexOfKey` calls `java.util.Arrays.binarySearch(...)`
  directly (confirmed in bytecode) - sorted array + binary search, **O(log n)**.

Our `%X{requestId}` pattern converter does exactly the access RainbowGum's array is *not*
optimized for: a single-key-by-name lookup, evaluated once per log line (5x per request).
That's the opposite of RainbowGum's intended sweet spot (iterate the whole array once, dump
every key to JSON - cheap for an array, no hashing/comparator overhead, and likely faster
than both alternatives for *that* access pattern).

**Why this hasn't skewed the numbers above (yet):** with only one MDC key currently in play,
the "array" being scanned has one entry - there's nothing to scan past, so the O(n) vs O(1)
vs O(log n) distinction is moot at n=1. The results above aren't actually exercising this
design tradeoff in either direction.

**Follow-ups worth running before drawing conclusions about MDC-related overhead:**
1. Add several more MDC keys (a handful of realistic context fields: tenant id, user id,
   trace id, etc.) with the same `%X{key}`-style text-pattern lookups, to see where/whether
   RainbowGum's linear scan becomes material as n grows relative to Logback's hashmap and
   Log4j2's binary search.
2. Compare against RainbowGum's actual designed-for case: a JSON/structured encoder
   (`rainbowgum-json`) that dumps the whole `KeyValues` array in one iteration, instead of
   the text-pattern single-key lookup - likely a very different, more favorable picture for
   RainbowGum, and arguably a fairer "real prod app" comparison than a Logback-style text
   pattern, given how much production logging is structured/JSON today.

Raw data: `results/results.csv` (gitignored - regenerated per run, not committed).

## Earlier run (short validation length)

`WARMUP_SECONDS=3 DURATION_SECONDS=8 CONCURRENCY=8`, single machine, single trial each - this
was purely to sanity-check the harness and the jansi fix before committing to a full-length
run; superseded by the run above for anything about relative performance at realistic load.

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 140670   | 17,583.75 | 0.41   | 0.76   | 1.16   | 11.74  | 0.45    | 574.7 / 593.1 / 584.3 |
| log4j2     | 143224   | 17,903.00 | 0.40   | 0.75   | 1.11   | 10.40  | 0.45    | 579.4 / 597.8 / 584.8 |
| rainbowgum | 124011   | 15,501.38 | 0.47   | 0.82   | 1.23   | 10.39  | 0.52    | 561.4 / 576.9 / 574.5 |

## GELF/JSON test: the picture reverses

Adam: the pattern-based comparison above outputs different amounts of text per framework
(RainbowGum's `%X{requestId}` renders longer than Logback/Log4j2's), which is a bigger
suspect than MDC lookup cost for the differences seen. Wanted a JSON comparison, GELF as
"probably the safest" common format, expecting no byte-for-byte match.

**Setup:** Spring Boot 4.1 has built-in structured logging support for Logback and Log4j2 -
`logging.structured.format.file=gelf` (confirmed the string literal `"gelf"` in
`CommonStructuredLogFormat.class`), no extra dependency needed. RainbowGum's Spring
integration has no equivalent property support yet, so added
`GelfSpringRainbowGumServiceProvider` (`rainbowgum-benchmark-webapp-rainbowgum`, registered
via `META-INF/spring.factories`) using the `SpringRainbowGumServiceProvider` SPI exactly as
designed - it checks the *same* `logging.structured.format.file` property and swaps in
`rainbowgum-json`'s `GelfEncoder` for file output when it's `gelf`, so one env var
(`STRUCTURED_FORMAT=gelf`) now toggles all three apps identically. Also had to align the
GELF `host` field (`logging.structured.gelf.host=benchmark-host`, same key for all three -
confirmed empirically since Spring Boot's own formatter omits `host` unless it's set,
despite GELF nominally requiring it).

**Content isn't byte-for-byte, as expected**, but isn't a smoking gun in either direction:
Logback/Log4j2 include `_process_pid`, `_process_thread_name`, `_service_version`,
`_log_logger`; RainbowGum includes `_time` (an ISO-8601 string *in addition to* the numeric
epoch `timestamp` both formats share - genuinely more work per line, not less) and
`_thread_id`. Net average line length: Logback/Log4j2 349.3 bytes, RainbowGum 331.2 bytes -
RainbowGum's shorter despite the extra timestamp field, because the other two's field names
(`_process_thread_name` etc.) are longer. Roughly a wash on "who's writing less."

**Results (full-length, default settings, same as the run above but GELF instead of the
text pattern):**

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 648,824  | 21,627.47 | 2.07   | 4.13   | 6.99   | 18.88  | 2.31    | 650.6 / 656.9 / 654.1 |
| log4j2     | 487,874  | 16,262.47 | 2.66   | 6.06   | 10.15  | 28.30  | 3.07    | 667.3 / 673.0 / 670.4 |
| rainbowgum | 450,671  | 15,022.37 | 3.54   | 6.17   | 10.33  | 25.96  | 3.33    | 670.1 / 675.2 / 671.7 |

**The ranking flips.** Log4j2 goes from clearly fastest at the text pattern (34,374 req/s)
to clearly *slowest but one* at GELF (16,262 req/s, a 53% drop) - while Logback barely moves
(21,443 -> 21,627 req/s, essentially flat) and RainbowGum drops moderately (23,537 -> 15,022,
-36%) to land just behind Log4j2. This lines up with Adam's suspicion that Log4j2's edge in
the text-pattern run was specifically about its date-handling/formatting fast path for that
one code path, not a general architectural advantage - it clearly doesn't carry over to the
JSON formatting path.

**Important caveat before reading too much into the JSON numbers themselves:** this measures
each framework through *Spring Boot's* structured-logging abstraction
(`StructuredLogFormatter`/`GraylogExtendedLogFormatStructuredLogFormatter`) for Logback and
Log4j2, not necessarily each framework's own most-optimized native JSON path - Log4j2 in
particular is known for its garbage-free `JsonTemplateLayout`, which this test doesn't
exercise (that would need bypassing Spring Boot's built-in structured logging and configuring
a native `log4j2.xml` layout directly). So this result says "Spring Boot's built-in GELF
formatter is much cheaper for Logback than for Log4j2," which is still a genuinely useful
data point, but isn't necessarily the ceiling of what Log4j2 can do with JSON.

No errors in any of the three apps' stdout during this run (checked via
`grep -ci "exception\|error"` on each `*-gelf-stdout.log` - all zero).

Reproduce: `STRUCTURED_FORMAT=gelf ./run-all.sh` (combine with `LOG_LEVEL=ERROR` too, for a
JSON-noop-case comparable to the ERROR-level finding above - not yet run).

## Virtual threads: Log4j2 goes from fastest to slowest

Adam's expectation going in: garbage-free techniques (like Log4j2's) rely on reusing
mutable state across calls (thread-local caches, buffers) to avoid allocation - a strategy
whose payoff depends on the *same thread* sticking around long enough to amortize the setup
cost across many calls. Virtual threads break that assumption: Tomcat with
`spring.threads.virtual.enabled=true` spins up a *new* virtual thread per request, so any
thread-local cache starts cold on every single request instead of staying warm across
thousands of requests on a small platform-thread pool. Adam's bet is this matters less (or
reverses) under virtual threads, and eventually under Valhalla - which is part of why
RainbowGum sticks with `Instant` rather than its own optimized/mutable clock type, betting
flattened-immutable will beat garbage-free-via-mutation once value types land.

**Setup:** added `VIRTUAL_THREADS=true` to `run-all.sh`, which passes
`--spring.threads.virtual.enabled=true` (Spring Boot 4.1's standard property, confirmed via
`spring-configuration-metadata.json` - `spring.threads.virtual.enabled`, default `false`) to
all three apps identically - pure Spring Boot/Tomcat property, no per-framework code needed.

**Verified virtual threads were actually active** (not just "the flag was silently ignored")
via a live `jcmd <pid> Thread.dump_to_file -format=json`: no `http-nio-8080-exec-N` platform
pool threads at all (present in every prior non-VT run), a `ForkJoinPool-1` container
present (the default virtual-thread carrier pool), and Tomcat's executor container showing
0 live threads between requests (expected for a virtual-thread-per-task executor, which has
no idle pooled workers to show in a snapshot).

**Results (full-length, default settings, text pattern + INFO, virtual threads on):**

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 721,231  | 24,041.03 | 2.07   | 2.85   | 3.50   | 13.63  | 2.08    | 635.7 / 642.2 / 638.9 |
| log4j2     | 573,879  | 19,129.30 | 2.43   | 5.11   | 7.81   | 22.34  | 2.61    | 629.8 / 633.8 / 631.1 |
| rainbowgum | 604,772  | 20,159.07 | 2.50   | 4.10   | 5.63   | 17.30  | 2.48    | 631.1 / 635.9 / 632.6 |

**vs the platform-thread run at the top of this file:**

| label      | platform req/s | virtual req/s | change |
|------------|----------------:|---------------:|-------:|
| logback    | 21,442.80       | 24,041.03      | **+12%** |
| log4j2     | 34,374.30       | 19,129.30      | **-44%** |
| rainbowgum | 23,537.43       | 20,159.07      | -14% |

Log4j2 goes from clearly fastest to clearly *slowest* of the three under virtual threads -
a dramatic reversal, not just "loses its edge" like the GELF case. Logback actually
*improves*. RainbowGum drops but lands ahead of Log4j2, closer to Logback. This is
consistent with (though doesn't yet prove) Adam's thread-local-cache-going-cold hypothesis -
Log4j2's optimizations for the platform-thread case may be actively counterproductive once
every request gets a fresh virtual thread with no warm cache to reuse.

**Reproducibility check:** Adam was surprised Logback held up so well and suspected virtual
threads might make results more chaotic run-to-run, so re-ran the same
`VIRTUAL_THREADS=true` scenario a second time:

| label      | run 1 req/s | run 2 req/s | delta |
|------------|------------:|------------:|------:|
| logback    | 24,041.03   | 24,840.30   | +3.3% |
| log4j2     | 19,129.30   | 19,057.03   | -0.4% |
| rainbowgum | 20,159.07   | 19,822.93   | -1.7% |

Stable, not noise - both runs land within ~3% of each other with the identical ranking
(logback > rainbowgum > log4j2) and the same latency shape. Whatever's driving Log4j2's
regression and Logback's improvement under virtual threads, it's a consistent effect, not a
one-off fluke from a single run. No errors in either run's stdout.

Claude's theories on *why* Logback specifically holds up well (unverified, offered for the
later profiling session to check, not conclusions):
1. If Logback's critical section (whatever serializes the actual write) is already short -
   formatting done outside the lock, similar to RainbowGum's `DefaultLogAppender` - then
   it's exactly the shape that benefits from how virtual-thread contention is handled versus
   platform-thread OS-level blocking, rather than being hurt by the carrier-pool model.
2. This may be the mirror image of the ERROR-level finding: Log4j2's enabled-path
   optimizations pay off big when their assumptions hold (platform thread, warm thread-local
   cache) and cost more than they save when violated (fresh virtual thread every request).
   Logback doing comparatively little clever machinery means there's less for the new
   threading model to break.
3. Some of the effect might not be logging-specific at all - virtual threads change how
   Tomcat dispatches connections (no bounded worker pool, cheaper context switches, no
   queueing once past pool capacity), which could lift whichever framework isn't
   independently fighting that change.

**Not yet done, per Adam - saving for later:** a micro-benchmark (not the full webapp) that
isolates specifically where Log4j2 is spending its time, plus JFR profiling of that
micro-benchmark, to actually confirm (rather than just correlate) that thread-local cache
cold-starts are the mechanism. This full-webapp run establishes *that* something changes
dramatically for Log4j2 under virtual threads; it doesn't yet establish *why* at the
mechanism level.

No errors in any of the three apps' stdout during this run.

Reproduce: `VIRTUAL_THREADS=true ./run-all.sh` (combine with `LOG_LEVEL`/`STRUCTURED_FORMAT`
for the fuller combinatoric matrix - not yet run).

## Notes for the later micro-benchmark + JFR session

Adam's theories on the mechanism behind the differences above (recorded here as notes for
that later session, not yet independently verified end-to-end):

- **Logback may be eager on formatting/encoding**, and may simply rely on the console
  already being synchronized rather than adding its own lock around the append path.
- **RainbowGum likely pays for two locks on the console path**: its own `AppenderLock`
  (a `ReentrantLock`) around the write, *and* the JDK's own `synchronized` on
  `PrintStream` methods once the write actually reaches `System.out`. Log4j2/Logback may
  avoid the first of these by relying on the second alone.
- **`LogAppender`'s buffer-reuse path trades allocation for contention.** RainbowGum's
  default path (`DefaultLogAppender`) allocates a fresh buffer per call and encodes
  *outside* the lock (explicitly: "the idea here is to have the virtual thread do the
  formatting outside of the lock" per its own code comment) - only the write itself is
  locked. A `ReuseBufferLogAppender` also exists in `core/.../LogAppender.java`, but reusing
  a single shared buffer means the *encode* step must move inside the lock too (the buffer
  itself isn't thread-safe), so it trades less GC pressure for more lock contention - and
  it isn't the one used by default. Thread-local buffers (reuse without shared-mutable-state
  contention) were floated as an alternative worth trying, on the suspicion Log4j2 already
  does something like this.
- **Async output is believed low-priority** and hasn't been tested here, on 12-factor-app
  grounds (logging should just be a synchronous write to stdout/a file; let the platform
  handle buffering/shipping) rather than a performance argument against it.

**Quick verification done so far (source-level, not profiling):** confirmed via
`core/src/main/java/io/jstach/rainbowgum/LogAppender.java` that `DefaultLogAppender` (not
`ReuseBufferLogAppender`) is what's actually in play for this benchmark, and confirmed
`StdOutOutput` (`LogOutput.java`) wraps `System.out` directly - so the "two locks" theory
does plausibly apply to this benchmark's *console* output path specifically. Worth noting:
RainbowGum's Spring integration keeps both `file` and `console` appenders active by default
(`Fallback[logging.route.default.appenders]=[file, console]`, seen earlier when debugging
the `logging.file.name` URI issue), so every log call in every run so far - including the
"file" comparisons - has also been paying for the console/stdout write and whatever locking
that involves. **Checked whether this is a RainbowGum-only handicap: it isn't.** Logback's
`*-stdout.log` from an earlier run has the identical 15,350 `BenchController` lines as its
file output - Spring Boot's default Logback/Log4j2 config also keeps console *and* file
appenders both active when `logging.file.name` is set, same as RainbowGum. So the double-
appender cost (and any locking that goes with it on the console side) is a level playing
field across all three frameworks in every run so far, not a variable unique to RainbowGum.

## Finding: RainbowGum's earlier edge over Logback was partly an unequal-durability artifact

Adam: Logback flushes on every event by default; RainbowGum's `LogAppender` doesn't ("sort
of a mistake" he's been meaning to document - flushing every event is safer but generally
not needed). Suspected this mattered for file output specifically (not console, which he
expects flushes every time regardless). Suggested testing with
`logging.appender.file.flags=immediate_flush`.

**Verified both defaults directly:**
- Logback's `OutputStreamAppender` constructor sets `immediateFlush = true` (confirmed in
  bytecode) - flushes after every single event, always, by default.
- RainbowGum's `DefaultLogAppender.immediateFlush` is `flags.contains(IMMEDIATE_FLUSH)` -
  `false` unless that `AppenderFlag` is explicitly set (`core/.../LogAppender.java`).
- The suggested property is exactly right: `LogProperties.APPENDER_FLAGS_PROPERTY` resolves
  to `logging.appender.{name}.flags`, and `AppenderFlag.parse` uppercases the value before
  matching the enum, so `immediate_flush` -> `IMMEDIATE_FLUSH` works as given.
- Aside: Logback's constructor also sets up its own `streamWriteLock` (a `ReentrantLock`) -
  so it isn't lock-free either, contrary to one framing of the earlier "two locks" theory;
  it flushes every time *and* locks.

**Setup:** added `RG_IMMEDIATE_FLUSH=true` to `run-all.sh` - RainbowGum-only (Logback/Log4j2
need no change, they already flush by default), passes
`--logging.appender.file.flags=immediate_flush` to just that app.

**Results (full-length, default settings otherwise - text pattern, INFO, platform threads):**

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 648,213  | 21,607.10 | 2.01   | 4.65   | 8.03   | 21.40  | 2.31    | 643.4 / 649.8 / 646.5 |
| log4j2     | 988,226  | 32,940.87 | 1.33   | 2.91   | 5.28   | 20.10  | 1.52    | 636.5 / 640.4 / 639.2 |
| rainbowgum | 539,330  | 17,977.67 | 2.91   | 5.20   | 8.65   | 22.85  | 2.78    | 655.1 / 658.9 / 656.5 |

(Logback/Log4j2 numbers here are just reruns under the unrelated `-flush` label suffix - the
flag only applies to RainbowGum - included for an apples-to-apples same-run comparison
rather than reusing numbers from a different run.)

**RainbowGum drops from 23,537 to 17,978 req/s (-24%) and falls behind Logback too, not just
Log4j2** - reversing the earlier platform-thread result where RainbowGum beat Logback
(23,537 vs 21,443). That earlier "win" looks like it was at least partly an artifact of
comparing unequal durability guarantees (buffered vs flush-every-line), not a clean
architectural advantage. With flush behavior equalized to match Logback's actual default,
RainbowGum is now the slowest of the three in this scenario.

One benign aside: a `java.io.IOException: Stream Closed` appeared in
`SpringApplicationShutdownHook` in both the quick and full runs - RainbowGum's file stream
gets closed before Spring's own shutdown-time WARN log call tries to flush through it. Pure
shutdown-ordering race, happens after all measurements complete, doesn't affect the numbers
above, but worth Adam knowing about independent of this benchmark.

**Not yet tried:** the same flag combined with virtual threads or GELF, or checking whether
Logback's `immediateFlush=false` (matching RainbowGum's default instead of the other way
around) closes the gap from the other direction.

Reproduce: `RG_IMMEDIATE_FLUSH=true ./run-all.sh`.

## First JFR profiling pass: Tomcat's own internal logging is the biggest surprise

Captured real `jdk.ExecutionSample` profiles for Logback and RainbowGum under identical
load (default settings, platform threads, text pattern, 40s at concurrency 50) via
`jcmd <pid> JFR.dump` mid-run, then `jfr print --events jdk.ExecutionSample --stack-depth 128`
and aggregated leaf/stack frames in Python. (Log4j2 was captured too per the original plan,
but Adam redirected to Logback vs RainbowGum specifically - more familiar with that
codebase, and closer in design to RainbowGum - so that's the comparison below; the Log4j2
capture was lost to a JFR-dump-timing mistake and not worth re-doing given the redirect.)

**The single biggest, cleanest difference found:** `org.apache.juli.logging.DirectJDKLog`
(Tomcat's own internal JUL-based logging facade, used for Tomcat's *own* diagnostics, not
application code) showed up in **566 of ~3,700 RainbowGum samples vs 6 of ~3,900 Logback
samples** - about 94x more. Full chain for a representative sample:

```
java.lang.Module.getLayer()
java.lang.StackTraceElement.isHashedInJavaBase(Module)
java.lang.StackTraceElement.computeFormat()
java.lang.StackTraceElement.of(StackTraceElement[])
java.lang.StackTraceElement.of(Object, int)
java.lang.Throwable.getOurStackTrace()
java.lang.Throwable.getStackTrace()
org.apache.juli.logging.DirectJDKLog.log(Level, String, Throwable)
org.apache.juli.logging.DirectJDKLog.trace(Object)
org.apache.tomcat.util.net.SocketWrapperBase.populateReadBuffer(ByteBuffer)
... (NIO socket read -> HTTP11 processing -> Tomcat worker thread)
```

Decompiled `DirectJDKLog.log()`: it calls `logger.isLoggable(level)` first and only pays
the expensive cost (`new Throwable().getStackTrace()`, to determine caller class/method for
the log record) when that check passes. So the finding is: **Tomcat's own internal
FINER/TRACE-level diagnostic logging (normally a no-op) is effectively "enabled" and doing
real work on every socket read, under RainbowGum's setup but not Logback's.** A related leaf,
`java.text.MessageFormat.subformat`, traces back to the same underlying cause via a
different mechanism than first guessed (corrected by Adam - not JUL's own `Formatter`): the
actual chain is `NioEndpoint$NioSocketWrapper.registerReadInterest()` ->
`StringManager.getString(key, args)` -> `MessageFormat.format(...)`. `StringManager` is
Tomcat's *own* i18n resource-bundle string builder, used to build a trace message's text
*before* it's ever handed to a logging call - Tomcat doesn't rely on the logging framework
to format anything. Tomcat's own call sites for this are normally guarded by a level check
before bothering to build the message at all, so seeing this actually execute means whatever
guards it believed tracing was enabled - same underlying mystery as the `DirectJDKLog`
stack-walk, just encountered via a different Tomcat code path.

**Ruled out:** RainbowGum's `JULConfigurator`, which sets the root `java.util.logging`
logger's level from `config.levelResolver().resolveLevel("")` (see the immediate-flush
section above for other `LogAppender` internals). Tested `logging.jul.level.disable=true`
(skips just that level-setting, keeps the JUL bridge installed) - `DirectJDKLog` samples
stayed the same (554 vs 566) and throughput didn't move. So it isn't the JUL root logger's
*level* specifically, at least not via that code path.

**Adam recalled a purpose-built fix already exists:** `rainbowgum-tomcat` (a module that
apparently predates this benchmark investigation, whose original motivation he'd forgotten
until this profiling surfaced it) - `RainbowGumTomcatLog` implements Tomcat's own
`org.apache.juli.logging.Log` facade directly, registered via `@ServiceProvider(Log.class)`
so Tomcat's own service-loader discovery uses it instead of falling back to `DirectJDKLog`/
real JUL. It routes straight through `LogRouter.global()` (a static/global accessor
specifically because Tomcat starts logging before Spring's context - and RainbowGum's real
config - is ready) with a proper `isEnabled()` gate and no stack-walking. Added it as a
dependency to `rainbowgum-benchmark-webapp-rainbowgum`.

**Result: mixed, and the full isolation test overturned the first read of it.**
- `DirectJDKLog` samples: 566 -> **0** with `rainbowgum-tomcat` added. The specific
  stack-walk cost is genuinely eliminated by it, confirmed.
- Throughput with `rainbowgum-tomcat` added: baseline ~23,570-23,824 req/s ->
  **21,377-21,547 req/s, reproducibly worse (~9-10%) across two separate runs.**

First read: assumed `rainbowgum-tomcat`'s own routing overhead must be more expensive than
`DirectJDKLog`'s occasional stack-walk. Adam asked to double check how Spring Boot actually
sets up Logback's JUL bridging, which overturned that:

- Decompiled `org.slf4j.bridge.SLF4JBridgeHandler.install()` (what Spring Boot's
  `LogbackLoggingSystem` uses): it only calls `Logger.addHandler(...)` on the JUL root
  logger - it **never touches the root logger's level**. So under Logback, JUL's root level
  stays at the JDK's own default (`INFO`), and Tomcat's FINER-level internal trace calls get
  correctly rejected for free, before `DirectJDKLog` does any real work.
- RainbowGum's `JULConfigurator` (see above) *does* explicitly set the JUL root level from
  `config.levelResolver().resolveLevel("")` - looked like the smoking gun.
- But testing `logging.jul.disable=true` (disables RainbowGum's JUL bridge *entirely* - no
  handler installed, no level ever touched, `main()`) **with `rainbowgum-tomcat` removed**
  for a clean isolation (my first attempt at this test still had `rainbowgum-tomcat` in the
  pom, which invalidated it - Tomcat wasn't touching JUL at all either way, so the flag
  couldn't have shown anything): throughput came back to baseline (**23,325 req/s**), but
  `DirectJDKLog` samples were **still there, basically unchanged (545)**.

So `DirectJDKLog`'s sample count doesn't actually track with throughput at all - it's ~545
whether RainbowGum's JUL bridge is fully enabled, fully disabled, and (apparently) whether
or not it's even RainbowGum's bridge causing it. Throughput only drops when
`rainbowgum-tomcat` is *added*. That means the real cost isn't "Tomcat's JUL-bridged calls
are expensive" (real, but apparently not the throughput-determining factor) - it's something
specific to routing Tomcat's own high-frequency internal log attempts through
`RainbowGumTomcatLog` into RainbowGum's *own* pipeline.

**Leading (unconfirmed) theory:** lock contention. `io.jstach.rainbowgum.LogRouter$Router.log`/
`GlobalLogRouter.route` accounted for ~590 of ~3,500 samples in the rainbowgum-tomcat run,
despite only 5 Tomcat-related lines ever appearing in `benchmark.log` (one-time INFO startup
lines, not the high-frequency socket-level chatter) - so Tomcat's very frequent internal log
attempts are reaching RainbowGum's router machinery constantly without writing anything.
Adam's own earlier "two locks" note is relevant here: `DefaultLogAppender.append()` takes a
shared `AppenderLock` (`ReentrantLock`) around every write - the *same* lock the
application's own real log calls (`BenchController`'s 5 statements/request) also take.
`DirectJDKLog`/real JUL is a completely separate, independent pipeline with its own handler
and no shared lock with RainbowGum at all - expensive per call, but never contends with
anything. `RainbowGumTomcatLog` funnels Tomcat's much-higher-frequency internal log
*attempts* into the *same* router/lock the application's real logging uses, so even if each
individual attempt is cheap, the aggregate contention on that shared lock between two very
different call-frequency sources could plausibly explain a net throughput loss despite the
per-call `DirectJDKLog` cost being eliminated. Not confirmed - would need lock-specific JFR
events (`jdk.JavaMonitorEnter`/`ReentrantLock` wait events) captured with a lower threshold
than the default "profile" settings use, to actually see contention on `AppenderLock`
directly rather than infer it.

**Still genuinely unresolved:** why Tomcat's internal FINER/TRACE logging passes its level
check at all under this setup (~545 `DirectJDKLog` samples represents real work happening,
regardless of whether it's the throughput bottleneck) - `logging.jul.disable=true` proves
it's not specifically RainbowGum's JUL bridge doing this, so the cause is something else
entirely (possibly just this JVM/Tomcat/Spring Boot combination's own default JUL
configuration, independent of the logging backend choice - worth checking whether Logback's
run shows the *same* effective JUL root level and Tomcat still stays quiet for a different
reason, e.g. per-logger rather than root-level configuration).

**Current state:** `rainbowgum-tomcat` is *not* a dependency of
`rainbowgum-benchmark-webapp-rainbowgum` (removed after this test), so the default numbers
elsewhere in this document reflect what most real users would actually have.

Reproduce: `jcmd <pid> JFR.dump filename=out.jfr` on a running benchmark app mid-load, then
`jfr print --events jdk.ExecutionSample --stack-depth 128 out.jfr`. When testing a flag or
dependency change, always confirm no stale app is still holding port 8080 from a previous
run first (`ps aux | grep rainbowgum-benchmark-webapp`) - a stale process silently
invalidates the next test's isolation, as happened once during this investigation.

## Isolating just the application's own logging work (excluding Tomcat's internal noise)

Filtered the same two JFR captures (Logback/RainbowGum, same run as above) down to only
samples whose stack contains `BenchController` - i.e. genuinely from the app's own 5 log
statements per request, not Tomcat's internal chatter. For the two runs compared (throughput
was similar: 881,803 Logback requests vs 952,943 RainbowGum requests over the same 40s):

**Logback: 1,017 of 3,916 total samples (26%) rooted in `BenchController`.**
**RainbowGum: 576 of 3,714 total samples (15.5%) rooted in `BenchController`.**

Normalized for request count, **RainbowGum's own logging pipeline is doing proportionally
less than half the sampled work per request that Logback's is** - a genuinely positive
signal for RainbowGum's core logging path specifically (separate from all the Tomcat/JUL
stuff above).

Top leaves within just the `BenchController`-rooted samples:

| Logback (1,017 samples) | RainbowGum (576 samples) |
|---|---|
| `FormattingConverter.write` + `PatternLayoutBase.writeLoopOnConverters`: 162 (converter dispatch) | `CompositeFormatter.format`: 62 (converter dispatch equivalent) |
| `BufferedOutputStream.flushBuffer()`: 71 (**the `immediateFlush=true` cost, directly visible**) | `FileOutputStream.traceWriteBytes`/`writeBytes`: 72 (fewer, bigger buffered writes instead) |
| HashMap/ConcurrentHashMap lookups: 135 | `HashMap.getNode`/`Objects.equals`: 33 |
| Date formatting (`DateConverter`, `DateTimeFormatterBuilder`, `DateTimePrintContext.adjust`): ~51 | Date formatting (`DateTimeFormatterBuilder`, `LocalDate.get0`): ~46 |
| `ColorConverter.transform`: 24 (console pattern still colors by default) | `ClrStaticFormatter.format`: 16 (same console-coloring cost) |
| `MessageFormatter.deeplyAppendParameter`: 15 (SLF4J's own lightweight `{}` substitution) | `SLF4JMessageFormatter.safeObjectAppend`: 22 (its own lightweight `{}` substitution) |
| `AbstractQueuedSynchronizer.acquire`: 14 (real contention on `streamWriteLock`) | `AbstractQueuedSynchronizer.acquire`: 12 (comparable contention on `AppenderLock`) |

Takeaways:
- **Date formatting costs are essentially the same magnitude between RainbowGum and
  Logback** - a real data point against "RainbowGum's `Instant` usage makes it slower,"
  at least relative to Logback specifically. (Log4j2 wasn't in this isolated comparison -
  the pivot to Logback vs RainbowGum happened before capturing it cleanly - so this doesn't
  speak to why Log4j2 was faster in the original text-pattern run.)
- **Lock contention is comparable too** (12 vs 14 samples) - the "two locks" theory doesn't
  show up as a large gap in this specific metric, at least at this concurrency/load level.
- **The standout difference is Logback's mandatory per-line flush** (`flushBuffer()`, 71
  samples, nothing analogous in RainbowGum's list) - directly corroborates the earlier
  `RG_IMMEDIATE_FLUSH` finding: this is a real, visible, first-order cost specific to
  Logback's `immediateFlush=true` default that RainbowGum simply doesn't pay by default.
- Both frameworks color their console output by default via cheap inline escape-code
  embedding (`ColorConverter`/`ClrStaticFormatter`) at comparable cost - neither is using a
  stream-wrapping approach here (RainbowGum's `rainbowgum-jansi`, which does exactly that
  expensively, is excluded from this benchmark app - see the jansi finding above).
- Confirms `java.text.MessageFormat` truly is Tomcat-internal-only noise, not part of either
  framework's real application-message formatting - both use their own lightweight `{}`
  parameter substitution at comparable cost.

## Rerun after the timestamp-caching/MDC/GELF fixes, with Tomcat's own JUL noise excluded

Rebased this branch onto `main` after several fixes landed from the separate micro-benchmark
investigation: millisecond-precision caching for `%d`/`ofISO()`/GELF's `_time` field
(previously recomputed a fresh `DateTimeFormatter` string on every single event, uncached),
`%X`/`%mdc` fixed to match Logback's actual output format, and the Spring Boot module split.
Reran the default (text pattern, `INFO`, platform threads) scenario with
`RG_IMMEDIATE_FLUSH=true` again to see where the gap to Logback stands now, plus a new
`EXCLUDE_JUL=true` toggle (silences Tomcat's own `org.apache.tomcat`/`catalina`/`coyote` JUL
loggers identically on all three apps - see `run-all.sh` for why: Tomcat's internal JUL
noise was found earlier to behave inconsistently between frameworks, not something this
benchmark is actually trying to compare).

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback (flush, JUL included) | 658,943 | 21,964.8 | 2.02 | 4.52 | 7.55 | 20.66 | 2.28 | - / - / 634.6 |
| log4j2 (flush, JUL included) | 1,003,565 | 33,452.2 | 1.32 | 2.84 | 4.96 | 20.10 | 1.49 | - / - / 636.5 |
| rainbowgum (flush, JUL included) | 599,215 | 19,973.8 | 2.58 | 4.69 | 7.72 | 21.26 | 2.50 | - / - / 678.2 |
| logback-flush-nojul | 644,030 | 21,467.7 | 2.06 | 4.64 | 7.71 | 21.48 | 2.33 | 649.8 / 656.2 / 653.1 |
| log4j2-flush-nojul | 1,015,999 | 33,866.6 | 1.31 | 2.78 | 4.91 | 23.88 | 1.48 | 670.3 / 675.5 / 673.9 |
| rainbowgum-flush-nojul | 625,618 | 20,853.9 | 2.45 | 4.51 | 7.42 | 21.99 | 2.40 | 629.1 / 638.4 / 634.1 |

**RainbowGum's throughput improved on both changes, independently:**
- The timestamp/MDC/GELF fixes alone (JUL still included, comparing against the pre-fix
  `RG_IMMEDIATE_FLUSH` baseline earlier in this file: 17,977.7 req/s) already accounted for
  most of the earlier +11.1% gain seen in the micro-benchmark investigation.
- Excluding Tomcat's JUL noise on top of that moved RainbowGum from 19,973.8 to 20,853.9
  req/s (+4.4%), while Logback and Log4j2's numbers moved by only ~1-2% (noise) - confirming
  the JUL noise really was disproportionately costing RainbowGum specifically, not a neutral
  factor affecting all three equally.
- **Net effect: RainbowGum's gap to Logback narrowed from ~17% (17,977.7 vs 21,607.1 in the
  original `RG_IMMEDIATE_FLUSH` finding) to ~2.9% (20,853.9 vs 21,467.7 here)** - almost
  entirely closed, without touching `Instant.now()` capture itself (the one piece explicitly
  left alone as a last resort per Adam - see the micro-benchmark FINDINGS.md).
- Log4j2 remains well ahead of both (33,866.6 req/s, ~62% higher than RainbowGum here) -
  unaffected by any of these fixes, a separate, not-yet-investigated gap.

Reproduce: `RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true ./run-all.sh`.

## GELF and virtual-threads rerun after the same fixes - a flush-confound lesson

Reran both the GELF and virtual-threads scenarios too, to check whether the timestamp/MDC/
GELF fixes plus `EXCLUDE_JUL` helped there as well. First attempt combined them with
`RG_IMMEDIATE_FLUSH=true` (matching the default-scenario rerun above) and looked like a
*regression* for RainbowGum in both cases:

| label | req/s (old baseline, no flush) | req/s (`EXCLUDE_JUL` + flush) | looks like |
|---|---:|---:|---|
| rainbowgum-gelf | 15,022.4 | 13,717.9 | -8.7% |
| rainbowgum-vt | 20,159.1 | 17,698.9 | -12.2% |

That's misleading, not a real regression: unlike the default text-pattern scenario (which
already had a prior `RG_IMMEDIATE_FLUSH` baseline to compare against cleanly), GELF and
virtual-threads had no such baseline - the only prior numbers used RainbowGum's *default*
(buffered, non-immediate-flush) behavior. `RG_IMMEDIATE_FLUSH` is a real, additive cost
specific to RainbowGum (Logback/Log4j2 already flush by default, so it's a no-op for them),
so comparing "old, unflushed RainbowGum" against "new, flushed RainbowGum" conflates two
independent changes and makes a genuine improvement look like a regression.

**Reran both without `RG_IMMEDIATE_FLUSH`** (`EXCLUDE_JUL=true` only, matching the old
baselines' flush settings exactly) for a clean, apples-to-apples comparison:

| label | old baseline | `EXCLUDE_JUL`, no flush | Δ |
|---|---:|---:|---:|
| rainbowgum-gelf | 15,022.4 | 16,173.1 | **+7.7%** |
| rainbowgum-vt | 20,159.1 | 20,869.3 | **+3.5%** |

Both are real, positive gains, consistent with the default-scenario rerun above:

**GELF** (`STRUCTURED_FORMAT=gelf EXCLUDE_JUL=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-gelf-nojul | 673,984 | 22,466.1 | 2.02 | 3.90 | 6.45 | 18.13 | 2.23 | 642.3 / 646.4 / 644.6 |
| log4j2-gelf-nojul | 504,235 | 16,807.8 | 2.58 | 5.83 | 9.73 | 28.11 | 2.97 | 622.7 / 628.9 / 625.6 |
| rainbowgum-gelf-nojul | 485,194 | 16,173.1 | 3.29 | 5.66 | 9.48 | 31.93 | 3.09 | 652.5 / 656.0 / 654.1 |

RainbowGum now nearly ties Log4j2 (16,173.1 vs 16,807.8, ~3.8% behind, down from ~8% behind
in the old GELF baseline) - the GELF `_time` caching fix specifically targeted this code
path, and it shows.

**Virtual threads** (`VIRTUAL_THREADS=true EXCLUDE_JUL=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-vt-nojul | 788,218 | 26,273.9 | 1.91 | 2.57 | 3.07 | 12.62 | 1.90 | 651.3 / 653.8 / 652.8 |
| log4j2-vt-nojul | 579,777 | 19,325.9 | 2.42 | 5.04 | 7.68 | 21.68 | 2.59 | 625.4 / 629.7 / 628.1 |
| rainbowgum-vt-nojul | 626,080 | 20,869.3 | 2.41 | 3.98 | 5.45 | 16.39 | 2.40 | 630.3 / 635.3 / 631.9 |

RainbowGum keeps its lead over Log4j2 under virtual threads (20,869.3 vs 19,325.9), same
ranking as the original virtual-threads finding (logback > rainbowgum > log4j2), with a
modest additional gain from the fixes.

**Takeaway for future reruns:** `RG_IMMEDIATE_FLUSH` and any fix/config comparison should
never be combined casually when the *baseline* being compared against didn't also use
`RG_IMMEDIATE_FLUSH` - always match flush settings on both sides of a before/after
comparison, or the flush cost (a real, first-order effect - see the original
`RG_IMMEDIATE_FLUSH` finding) will dominate and mask whatever else is being measured.

Reproduce: `STRUCTURED_FORMAT=gelf EXCLUDE_JUL=true ./run-all.sh` and
`VIRTUAL_THREADS=true EXCLUDE_JUL=true ./run-all.sh`.

## RainbowGum overtakes Logback after the independent-appender-lock fix

Rebased onto `main` again after a much bigger fix landed: `LogAppender.Appenders.asSingle()`
previously combined every appender on a route (here, "console" + "file", both active by
default) under one shared lock - so a console write and a file write for the same event, and
every concurrent request's appends across *both* outputs, all contended on that single lock.
Root-caused via Adam's suspicion that this was a design mistake, not intentional; confirmed
with a targeted single-app test showing +30-32% throughput before this was even wired up as a
permanent option (see the micro-benchmark session's notes). Independent per-appender locks
are now the default (`LogRouter.RouteFlag.SHARED_APPENDER_LOCK` opts back into the old shared
-lock behavior if ever needed).

Reran the same default scenario (text pattern, `INFO`, platform threads, `RG_IMMEDIATE_FLUSH`,
`EXCLUDE_JUL`) as the two reruns above, to see it land in the full webapp benchmark, not just
the isolated single-app test:

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-flush-nojul | 654,451 | 21,815.0 | 2.03 | 4.54 | 7.60 | 20.59 | 2.29 | 680.1 / 683.3 / 681.9 |
| log4j2-flush-nojul | 1,037,488 | 34,582.9 | 1.28 | 2.71 | 4.79 | 19.49 | 1.45 | 649.0 / 673.7 / 659.2 |
| rainbowgum-flush-nojul | 854,873 | 28,495.8 | 1.67 | 2.92 | 4.86 | 20.62 | 1.75 | 661.2 / 662.8 / 662.1 |

**RainbowGum: 20,853.9 -> 28,495.8 req/s (+36.6%)** - logback and log4j2 barely moved
(+1.6%/+2.1%, noise), confirming this is entirely the lock fix, not something else drifting.

**RainbowGum now clearly beats Logback** (28,495.8 vs 21,815.0, ~31% ahead - previously ~2.9%
*behind*) and has closed most of the remaining gap to Log4j2 (17.6% behind, down from 38.4%
behind). Log4j2 is still fastest here, but the gap that opened the whole investigation (17%
behind Logback, before any of this session's fixes) has fully inverted.

Reproduce: `RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true ./run-all.sh`.

## GELF and virtual-threads rerun after the independent-appender-lock fix

Same fix, same settings (`RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true`) as the default-scenario
rerun above, but for the GELF and virtual-threads scenarios this time - both console and file
appenders are active in every scenario this benchmark runs, so the lock fix should show up
everywhere, not just the default text-pattern case.

**GELF** (`STRUCTURED_FORMAT=gelf RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-gelf-flush-nojul | 699,399 | 23,313.3 | 1.95 | 3.68 | 6.17 | 19.46 | 2.14 | 651.2 / 659.0 / 655.6 |
| log4j2-gelf-flush-nojul | 502,632 | 16,754.4 | 2.60 | 5.82 | 9.72 | 27.66 | 2.98 | 653.9 / 667.0 / 657.1 |
| rainbowgum-gelf-flush-nojul | 700,236 | 23,341.2 | 2.00 | 3.71 | 6.24 | 20.83 | 2.14 | 665.8 / 672.4 / 668.8 |

**RainbowGum: 13,717.9 -> 23,341.2 req/s (+70.2%)** - logback/log4j2 essentially flat
(-2.6%/-0.4%, noise). RainbowGum now **ties Logback** (23,341.2 vs 23,313.3, within noise) and
is **39.3% ahead of Log4j2**. Previously RainbowGum was the slowest of the three here by a
wide margin (13,717.9, well behind both) - this is the largest swing of any scenario rerun so
far.

**Virtual threads** (`VIRTUAL_THREADS=true RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-vt-flush-nojul | 718,571 | 23,952.4 | 2.07 | 2.87 | 3.49 | 14.13 | 2.09 | 630.2 / 636.3 / 633.4 |
| log4j2-vt-flush-nojul | 569,894 | 18,996.5 | 2.46 | 5.13 | 7.76 | 22.92 | 2.63 | 625.7 / 628.6 / 627.5 |
| rainbowgum-vt-flush-nojul | 792,550 | 26,418.3 | 1.91 | 2.52 | 3.19 | 14.76 | 1.89 | 625.8 / 628.7 / 627.4 |

**RainbowGum: 17,698.9 -> 26,418.3 req/s (+49.3%)** - logback/log4j2 essentially flat
(-2.2%/-0.8%, noise). RainbowGum now **leads all three frameworks** under virtual threads
(10.3% ahead of Logback, 39.1% ahead of Log4j2) - previously it was the slowest of the three
in this scenario.

**Across every scenario this benchmark runs** (default, GELF, virtual-threads), RainbowGum
now either leads or is within noise of the fastest framework, having started the investigation
behind in all three. The independent-appender-lock fix is the single largest contributor of
anything found this session, well ahead of the timestamp-caching/MDC/GELF fixes individually.

Reproduce: `STRUCTURED_FORMAT=gelf RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true ./run-all.sh` and
`VIRTUAL_THREADS=true RG_IMMEDIATE_FLUSH=true EXCLUDE_JUL=true ./run-all.sh`.

## Rerun after jansi removal, rainbowgum-tomcat, and immediate-flush-by-default

Rebased onto `main` again after three more fixes landed:

- `rainbowgum-jansi` is no longer pulled in by the `rainbowgum` umbrella artifact at all (it
  was the root cause of a separate stdout-redirect-bypass bug traced back to jline's
  jansi-core fork building a full `Terminal` just to detect TTY-ness; ANSI support is now
  auto-detected in core via `AnsiSupport`/`System.console()` with no runtime stream
  wrapping). The `rainbowgum-jansi` exclusion in this app's `pom.xml` is gone - there is
  nothing to exclude any more.
- `LogAppender.AppenderFlag.IMMEDIATE_FLUSH` was renamed to `DISABLE_IMMEDIATE_FLUSH` and
  flushing on every append/batch is now the *default* appender behavior (opt-out, not
  opt-in). `RG_IMMEDIATE_FLUSH` is gone from `run-all.sh` - there is nothing left to toggle.
- `rainbowgum-tomcat` (a Tomcat `org.apache.juli.logging.Log` facade routed straight through
  RainbowGum's `LogRouter`, bypassing `java.util.logging` entirely for Tomcat's own internal
  logging) is now a real dependency of this app instead of the previous `EXCLUDE_JUL`
  workaround, which just silenced `org.apache.tomcat`/`catalina`/`coyote` at the Spring Boot
  `logging.level.*` property level for all three apps. `EXCLUDE_JUL` is gone from
  `run-all.sh`. Spring Boot does not pull `rainbowgum-tomcat` in on its own, hence this app
  depends on it explicitly. Tomcat's internal logging is now **active, not silenced**, for
  all three apps in every scenario below - a more realistic default-config comparison than
  the previous reruns.

Because both the silencing-vs-not and the flush-default change moved at once, and RainbowGum
additionally switched *how* Tomcat-internal logging is handled (rainbowgum-tomcat's direct
route vs the old JUL bridge), these numbers are not a clean single-variable comparison against
the `-flush-nojul` rows above - they're the new baseline going forward.

**Default** (text pattern, `INFO`, platform threads):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback | 662,999 | 22,100.0 | 2.01 | 4.48 | 7.49 | 23.48 | 2.26 | 644.7 / 657.4 / 651.3 |
| log4j2 | 1,005,963 | 33,532.1 | 1.33 | 2.77 | 4.90 | 18.83 | 1.49 | 650.9 / 658.0 / 656.6 |
| rainbowgum | 707,929 | 23,597.6 | 1.87 | 4.18 | 7.28 | 20.19 | 2.12 | 639.2 / 647.7 / 643.5 |

RainbowGum still beats Logback (+6.8%) and trails Log4j2 (-29.6%), but is down from the
previous (silenced-Tomcat-logging) 28,495.8 req/s - a **-17.2%** move. Logback and Log4j2 both
stayed flat (+1.3%/-3.0%, noise) between silenced and unsilenced Tomcat logging, which is the
tell: for them, Tomcat's own internal log volume barely registers either way. For RainbowGum
specifically it clearly does now that it's routed through `rainbowgum-tomcat` instead of
being turned off.

**GELF/JSON** (`STRUCTURED_FORMAT=gelf`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-gelf | 689,560 | 22,985.3 | 1.98 | 3.76 | 6.35 | 22.39 | 2.18 | 669.7 / 675.4 / 672.6 |
| log4j2-gelf | 508,596 | 16,953.2 | 2.57 | 5.73 | 9.51 | 24.30 | 2.95 | 624.0 / 636.4 / 627.1 |
| rainbowgum-gelf | 556,671 | 18,555.7 | 2.39 | 5.38 | 8.85 | 26.52 | 2.69 | 646.0 / 652.2 / 648.2 |

Same story, more pronounced: RainbowGum drops from 23,341.2 to 18,555.7 req/s (**-20.5%**),
while Logback/Log4j2 stay flat (-1.4%/+1.2%, noise). RainbowGum now **trails Logback**
(-19.3%, previously tied) though it still beats Log4j2 (+9.5%). This is the scenario most
worth profiling further - GELF encoding plus unsilenced Tomcat-internal logging plus
rainbowgum-tomcat's routing is the worst combination measured so far.

**Virtual threads** (`VIRTUAL_THREADS=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-vt | 782,644 | 26,088.1 | 1.92 | 2.59 | 3.07 | 13.17 | 1.92 | 642.4 / 648.9 / 645.9 |
| log4j2-vt | 578,751 | 19,291.7 | 2.41 | 5.05 | 7.69 | 23.13 | 2.59 | 638.2 / 649.0 / 641.2 |
| rainbowgum-vt | 836,082 | 27,869.4 | 1.81 | 2.40 | 2.79 | 13.32 | 1.79 | 621.4 / 623.6 / 622.7 |

Opposite direction here: RainbowGum *improves*, 26,418.3 -> 27,869.4 req/s (**+5.5%**), and
still **leads both frameworks** (+6.8% over Logback, +44.5% over Log4j2) - a bigger lead over
Log4j2 than the previous silenced-Tomcat-logging run. Logback also moved more than noise this
time (23,952.4 -> 26,088.1, +8.9%) which muddies a clean read on this one; worth a rerun to
confirm it's not just run-to-run variance.

**Net**: unsilencing Tomcat's own internal logging and routing RainbowGum's share of it
through `rainbowgum-tomcat` costs real throughput in the default and GELF scenarios (-17.2%,
-20.5%) but not in the virtual-threads scenario (+5.5%). RainbowGum still leads Logback in
default and virtual-threads, but now trails it in GELF. Given Logback/Log4j2 are essentially
unaffected by silencing-or-not, the earlier (unconfirmed) shared-lock-contention theory for
why `rainbowgum-tomcat` measured worse doesn't fully explain this - independent per-appender
locks are already the default and the regression is still there in two of three scenarios.
Next step if pursued further: JFR profile the GELF scenario specifically (worst case) to see
where the added time actually goes.

Reproduce: `./run-all.sh`, `STRUCTURED_FORMAT=gelf ./run-all.sh`, and
`VIRTUAL_THREADS=true ./run-all.sh`.

## Isolating the rainbowgum-tomcat regression: clean 3-way test, single app

The previous section's numbers came right after a rebase plus several config changes at
once (jansi removal, immediate-flush-by-default, EXCLUDE_JUL replaced by rainbowgum-tomcat),
raising a fair concern that the result could be an artifact of a messy history rather than a
real effect. Isolated it with a dedicated script, `run-tomcat-jul.sh`, that only runs
`rainbowgum-benchmark-webapp-rainbowgum` (no Logback/Log4j2, no JSON/GELF, no virtual
threads) in three configurations, varying nothing except how Tomcat's own internal logging
(catalina/coyote/tomcat packages) is handled:

- **jul** - default build (no `rainbowgum-tomcat` dependency), RainbowGum's JUL bridge
  installed and enabled as normal (unsilenced, INFO).
- **nojul** - same build, but JUL completely disabled: `--logging.jul.disable=true` (so
  RainbowGum never installs its JUL bridge handler) plus
  `-Djava.util.logging.config.file=jul-disabled.properties` (`handlers=` empty, `.level=OFF`)
  so JUL's own default `ConsoleHandler` doesn't pick up the slack and print uncoordinated
  output straight to stderr. This is the zero-Tomcat-internal-logging-cost floor.
- **tomcat** - built with `-Ptomcat` (adds the `rainbowgum-tomcat` dependency, now an opt-in
  Maven profile on this app rather than an unconditional dependency, specifically so this
  script can build it both ways), JUL left enabled as normal.

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| rainbowgum-jul | 833,561 | 27,785.4 | 1.70 | 3.08 | 5.22 | 19.57 | 1.80 | 661.6 / 665.1 / 663.7 |
| rainbowgum-nojul | 855,218 | 28,507.3 | 1.67 | 2.97 | 4.98 | 18.41 | 1.75 | 674.9 / 678.2 / 676.8 |
| rainbowgum-tomcat | 706,494 | 23,549.8 | 1.87 | 4.19 | 7.27 | 21.38 | 2.12 | 631.9 / 639.7 / 636.0 |

`jul` vs `nojul` are within ~2.6% of each other - within noise. Tomcat's own internal
JUL-routed logging volume, going through the normal bridge, barely registers either way.
Swapping in `rainbowgum-tomcat` instead drops throughput ~15-17% versus both
(-15.2% vs jul, -17.4% vs nojul) - and this number (23,549.8) lands almost exactly on the
previous section's mixed-scenario "rainbowgum" default result (23,597.6), confirming that
result was not a history artifact. The regression is real and reproduces in complete
isolation.

Adam's suspicion going in: Tomcat may call `java.util.logging.Logger` directly in a few
places rather than going exclusively through its own `org.apache.juli.logging.Log`
abstraction. If true, the `tomcat` scenario would pay for *both* paths at once -
`RainbowGumTomcatLog`'s own overhead for whatever does go through juli, plus RainbowGum's
JUL bridge overhead for whatever bypasses it and hits raw JUL directly (JUL is left enabled
in this scenario) - which would explain why `tomcat` measures worse than even the plain
`jul` baseline instead of just matching it. Not yet confirmed; next step if pursued is
checking whether `SystemLoggerQueueJULHandler.publish()` still fires during the `tomcat`
run (it shouldn't, if Tomcat's internal logging is fully going through
`RainbowGumTomcatLog` instead).

Reproduce: `./run-tomcat-jul.sh`.

### Follow-up: both leading theories ruled out by instrumentation

Checked Adam's "Tomcat calls raw JUL somewhere" theory directly: patched
`SystemLoggerQueueJULHandler.publish()` (in `rainbowgum-jdk`, reverted after - not
committed) to print the logger name and a stack trace the first time each distinct logger
was seen, rebuilt, and drove 200 requests against the `tomcat` config. **Zero hits.** No
raw `java.util.logging` calls reach RainbowGum's JUL bridge during request handling -
Tomcat's own `org.apache.juli.logging.Log` abstraction is not being bypassed. (It did
confirm the separate defaults-gap finding from above: `Http11NioProtocol`'s two
startup-time INFO lines print unsilenced, since RainbowGum has no equivalent to Logback/
Log4j2's baked-in `Http11NioProtocol=WARN` etc. - but that's two lines at boot, not
ongoing.)

Also profiled the `tomcat` config with `-XX:StartFlightRecording=settings=profile` under
the same load (correcting for a PID-tracking issue in this sandbox's shell where `$!`
after `(exec java ...) &` didn't reliably match the real process - several early throughput
numbers during this investigation were accidentally measuring a stale orphaned process
from a prior attempt still bound to nothing; using the PID actually holding port 8080 via
`ss -ltnp` instead of `$!` fixed it, and gave a consistent 23,315-23,761 req/s, matching
the isolated-test number). Neither `jdk.ExecutionSample` (1,501 samples) nor
`jdk.ObjectAllocationSample` (6,158 samples) contain **a single frame** in
`io.jstach.rainbowgum.tomcat` or `org.apache.juli` - Tomcat's internal logging, through
either mechanism, isn't invoked often enough during steady-state request handling to be
CPU- or allocation-visible at all. `jdk.JavaMonitorEnter` was 0 (RainbowGum's
`AppenderLock` is a `ReentrantLock`, which JFR's monitor events don't cover anyway - not
informative either way).

Net: both hypotheses for *why* `rainbowgum-tomcat` regresses throughput are now ruled out.
It isn't Tomcat calling raw JUL, and it isn't per-call cost in
`RainbowGumTomcatLog`/`TomcatLevelLog` - that code path is barely exercised during the
actual load window. The ~15-17% drop is real and reproduces consistently, but its cause
isn't visible in per-request CPU/allocation profiling, so it's likely something structural
(classpath/JIT-shape difference, GC behavior, or a startup/init-time cost that isn't
amortizing the way `-tomcat`'s design assumes) rather than a hot-path logging cost.
Unresolved - would need a cleaner (non-shared) benchmarking environment and probably a
GC/heap diff between configs to chase further.

### A third theory, also ruled out: reflective Log construction on every getLog() call

`RainbowGumTomcatLog`'s own javadoc already notes Tomcat's `LogFactory` invokes the
`RainbowGumTomcatLog(String)` constructor via reflection (not the no-arg one, despite the
ServiceLoader contract technically only requiring that one) whenever it discovers a custom
`org.apache.juli.logging.Log` implementation. Adam's follow-up theory: if Tomcat calls
`LogFactory.getLog(...)` frequently rather than caching the result (e.g. per-request
instead of once per class), that reflective construction - plus the real work the
constructor does (`levelResolver().resolveLevel(...)`, `isChangeable(...)`,
`route(...)`) - would be a real, continuous cost invisible to statistical sampling if each
call is individually fast (JFR's execution/allocation sampling can easily under-count
short, frequent operations spread across many threads).

Tested exhaustively rather than by sampling: added an `AtomicLong`-per-name counter
(`ConcurrentHashMap<String, LongAdder>`) directly in the constructor, dumped from a JVM
shutdown hook (reverted after, not committed). Result over the same 357,868-request/
15-second load window: **~76 total constructions, nearly all count 1** (max 5, for
`ApplicationFilterConfig`), and **every one of them during Tomcat startup** - `Connector`,
`NioEndpoint`, `Http11Processor`, `StandardEngine`, etc. **Zero constructions during the
load window itself.** `LogFactory` does cache per class/name as expected; it is not
re-invoking the reflective constructor per call or per request. The reflection tax is
real but one-time (~76 calls at boot) and not a plausible source of a sustained ~15%
throughput regression.

All three theories investigated so far - raw JUL bypass, per-call `RainbowGumTomcatLog`
cost, and repeated reflective construction - are now ruled out by direct instrumentation.
The regression itself remains real and reproducible; its cause is still unidentified.
