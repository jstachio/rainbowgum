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

## Rerun after Spring Boot's replaceable-logger/JUL fixes - regression unaffected

Rebased onto `main` after three more fixes landed (session summary, not individual
commits - all from the SLF4J-facing side, not `rainbowgum-tomcat` itself):

- `RainbowGum.onGlobalChange`: SLF4J's `RainbowGumLoggerFactory` no longer gets stuck
  forever routing `getLogger(...)` calls through Spring's pre-boot bootstrap gum once
  the real one loads.
- `ReplaceableLogger.setEventHandler`/`RouteChangePublisher`: a `ReplaceableLogger`
  obtained *during* Spring's pre-boot phase (queued/bootstrap router) now gets its
  actual dispatch target rebound to the real router on swap too, not just its reported
  level - previously such a logger kept silently writing into the now-abandoned
  `QueueEventsRouter`'s queue forever.
- `rainbowgum-jul` restored as a normal (non-excluded) dependency of both Spring Boot
  starters, so `rainbowgum-jul` is present again alongside `rainbowgum-tomcat` in this
  benchmark (confirmed via `dependency:tree -Ptomcat`) - previously it had been
  excluded.

The working theory going in: if the benchmark app's own `BenchController` logger (or
some other early-obtained SLF4J logger) was getting stuck writing into the abandoned
queue, that could plausibly explain a structural, not-CPU-visible throughput drop -
matching how the `rainbowgum-tomcat` regression wasn't visible in JFR execution/
allocation sampling in the investigation above. Worth checking directly, since "in
theory dispatching should be very fast now" was the hypothesis.

**Default** (text pattern, `INFO`, platform threads):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback | 659,434 | 21,981.1 | 2.02 | 4.50 | 7.48 | 21.28 | 2.27 | 676.1 / 680.7 / 678.7 |
| log4j2 | 1,025,053 | 34,168.4 | 1.32 | 2.70 | 4.62 | 18.64 | 1.46 | 652.1 / 664.2 / 659.9 |
| rainbowgum | 696,268 | 23,208.9 | 1.90 | 4.26 | 7.32 | 23.17 | 2.15 | 626.4 / 632.8 / 630.1 |

vs. the previous default-scenario row (707,929 / 23,597.6): **-1.6%**, within noise.
Logback -0.5%, Log4j2 +1.9%, also noise.

**GELF/JSON** (`STRUCTURED_FORMAT=gelf`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-gelf | 703,357 | 23,445.2 | 1.94 | 3.64 | 6.11 | 19.61 | 2.13 | 659.1 / 668.7 / 664.2 |
| log4j2-gelf | 500,655 | 16,688.5 | 2.61 | 5.83 | 9.68 | 24.40 | 3.00 | 643.7 / 653.0 / 646.9 |
| rainbowgum-gelf | 523,209 | 17,440.3 | 2.56 | 5.73 | 9.25 | 24.36 | 2.87 | 624.1 / 626.2 / 625.1 |

vs. previous (556,671 / 18,555.7): **-6.0%** - a bit more than the default scenario's
noise band, but RainbowGum still sits between Logback (-25.6%) and Log4j2 (+4.5%), same
ordering as before. Not investigated further; flagged in case it's not noise.

**Virtual threads** (`VIRTUAL_THREADS=true`):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| logback-vt | 757,635 | 25,254.5 | 1.99 | 2.67 | 3.18 | 14.13 | 1.98 | 627.5 / 632.1 / 629.9 |
| log4j2-vt | 570,298 | 19,009.9 | 2.46 | 5.12 | 7.77 | 23.95 | 2.63 | 627.9 / 636.2 / 630.6 |
| rainbowgum-vt | 845,714 | 28,190.5 | 1.79 | 2.38 | 2.79 | 13.40 | 1.77 | 640.8 / 648.0 / 644.8 |

vs. previous (836,082 / 27,869.4): **+1.2%**, noise. RainbowGum still **leads both**
frameworks here (+11.6% over Logback, +48.3% over Log4j2).

**Isolated 3-way regression test** (`./run-tomcat-jul.sh`, RainbowGum only):

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| rainbowgum-jul | 837,095 | 27,903.2 | 1.70 | 3.05 | 5.11 | 18.10 | 1.79 | 668.7 / 671.7 / 670.5 |
| rainbowgum-nojul | 846,481 | 28,216.0 | 1.69 | 3.00 | 5.06 | 19.00 | 1.77 | 681.2 / 687.0 / 685.1 |
| rainbowgum-tomcat | 709,197 | 23,639.9 | 1.86 | 4.17 | 7.21 | 21.22 | 2.11 | 656.3 / 660.6 / 658.4 |

vs. previous (27,785.4 / 28,507.3 / 23,549.8): all three within **+0.4% / -1.0% / +0.4%**
- essentially identical numbers. The `rainbowgum-tomcat` regression is **completely
unaffected** by the Spring Boot fixes.

**Conclusion**: the working theory did not pan out. The pre-boot-stuck-in-queue bug
these fixes address is real, but the benchmark app's own logging apparently doesn't
trigger it in a way that shows up here - most likely because `@RestController`-scoped
beans like `BenchController` get their SLF4J logger field initialized well after Spring's
`LoggingSystem`/real RainbowGum has already loaded, not during the pre-boot window. The
`rainbowgum-tomcat` regression remains real, reproducible, and unexplained; all three
prior theories (raw JUL bypass, per-call `RainbowGumTomcatLog` cost, repeated reflective
construction) are still ruled out. Next step if pursued further is still the same as
before: a GC/heap diff between `nojul` and `tomcat` configs, since the drop isn't visible
in CPU/allocation sampling.

Reproduce: `./run-all.sh`, `STRUCTURED_FORMAT=gelf ./run-all.sh`,
`VIRTUAL_THREADS=true ./run-all.sh`, and `./run-tomcat-jul.sh`.

### A fourth theory tested: is it RainbowGumTomcatLog's own work at all?

Checked the dependency-mismatch theory first (Adam's suspicion that Maven's dependency
mediation rules might pull a different Tomcat version into the `rainbowgum-tomcat`
scenario than what Spring Boot's own BOM manages): `mvn dependency:tree -Ptomcat
-Dverbose` shows `rainbowgum-tomcat`'s own `tomcat-juli:11.0.25` dependency
(`provided`+`optional`, needed only to compile `RainbowGumTomcatLog implements
org.apache.juli.logging.Log`) never appears in the resolved tree at all - Maven
correctly excludes `provided`/`optional` dependencies from transitive propagation.
Confirmed directly against the built artifact too: `unzip -l` on the packaged fat jar's
`BOOT-INF/lib/` shows only `tomcat-embed-core-11.0.22.jar` (Spring Boot's managed
version) in every scenario, no `tomcat-juli` jar anywhere. Byte-diffed
`org/apache/juli/logging/{Log,LogFactory,DirectJDKLog,LogConfigurationException}.class`
between `tomcat-embed-core:11.0.22` and `tomcat-juli:11.0.25` directly - identical,
zero bytes different. Ruled out: there is no dependency-mediation version mismatch:
all three scenarios (`jul`/`nojul`/`tomcat`) run the exact same Tomcat classes.

Then tested whether the regression is inside `RainbowGumTomcatLog`'s own logic at all,
by temporarily replacing it with an absolute no-op: a new `NoopTomcatLog implements
org.apache.juli.logging.Log` directly (bypassing `ForwardingTomcatLog`/
`TomcatLevelLog`/`ChangeableRainbowGumTomcatLog` entirely), every method body empty,
`is*Enabled()` all returning `false`, registered via its own `@ServiceProvider(Log.class)`
with `RainbowGumTomcatLog`'s own annotation temporarily removed so only the no-op is
discovered via `META-INF/services/org.apache.juli.logging.Log` (confirmed via `unzip
-p` on the built jar). Reverted after, not committed.

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul | 837,095 | 27,903.2 | 1.70 | 3.05 | 5.11 | 18.10 | 1.79 | 670.5 |
| rainbowgum-nojul | 846,481 | 28,216.0 | 1.69 | 3.00 | 5.06 | 19.00 | 1.77 | 685.1 |
| rainbowgum-tomcat (real) | 709,197 | 23,639.9 | 1.86 | 4.17 | 7.21 | 21.22 | 2.11 | 658.4 |
| **rainbowgum-tomcat-noop** | 690,074 | **23,002.5** | 1.91 | 4.34 | 7.42 | 24.54 | 2.17 | 672.6 |

The no-op is not faster than the real implementation - if anything marginally slower
(within noise of each other), and both sit ~18-21% below the `jul`/`nojul` baseline.
**Ruled out: the regression is not inside `RainbowGumTomcatLog`'s own work** (route/
level resolution, dispatch, `ChangeableRainbowGumTomcatLog`/`TomcatLevelLog` construction
- none of it, since the no-op does none of it either). Whatever costs ~15-20%
throughput is structural to simply having *any* custom `org.apache.juli.logging.Log`
registered via `ServiceLoader` in place of Tomcat's own built-in `DirectJDKLog`, not a
property of what that custom implementation actually does.

This narrows the remaining candidates considerably: something about Tomcat's
`LogFactory` behaving differently once it has discovered *any* non-`DirectJDKLog`
implementation - worth checking whether `LogFactory`'s own internal discovery/caching
path (not just construction) takes a different, non-cached branch once a custom `Log`
is registered, and/or a JIT/class-loading-shape difference between the two `Log`
implementation classes actually in play (`DirectJDKLog` vs whichever custom one is
registered) that isn't specific to either custom implementation's logic. Not yet tested.

Reproduce: temporarily add a `NoopTomcatLog` as above, remove
`RainbowGumTomcatLog`'s `@ServiceProvider` annotation, `mvn ... install -Ptomcat
-Dmaven.javadoc.skip=true`, run manually (see `run-tomcat-jul.sh`'s `run_one` for the
exact java invocation - note the PID-tracking gotcha documented above: use `ss -ltnp`
to find the real bound PID, not `$!`).

### Confirming the reflection mechanism precisely, and a fifth data point: tomcat + JUL disabled

Decompiled `org.apache.juli.logging.LogFactory` directly (`javap -c`) to pin down exactly
what Adam recalled: its no-arg constructor does `ServiceLoader.load(Log.class)` once and,
if any implementation is found, reflectively caches its `String`-arg
`Constructor<? extends Log>` in a `discoveredLogConstructor` field (this is the one-time,
boot-only cost the earlier reflective-construction theory measured - confirmed, ~76
calls). But `getInstance(String)` itself is more pointed than "reflection is used
somewhere": it does **no caching of its own** -

```java
public Log getInstance(String name) {
    return discoveredLogConstructor == null
        ? DirectJDKLog.getInstance(name)              // static factory, no reflection
        : discoveredLogConstructor.newInstance(name);  // reflective, every single call
}
```

So the reflective path runs on *every* `LogFactory.getInstance(...)` call whenever any
custom `Log` is registered - full stop, regardless of implementation. It only measured as
~76 total calls because Tomcat's own internal classes cache the returned `Log` in a
`private static final` field rather than re-calling `getInstance()` per use - i.e. the
same conclusion as before, just now pinned to the actual mechanism rather than inferred
from a call counter. Also checked for a hidden fast path: grepped every `.class` file in
`tomcat-embed-core` for references to `DirectJDKLog` by name - only `LogFactory` and
`DirectJDKLog` itself reference it anywhere. No other Tomcat class (`NioEndpoint`,
`Http11Processor`, etc.) special-cases `DirectJDKLog`, so there's no secret
`instanceof DirectJDKLog` fast path elsewhere in Tomcat being bypassed by a custom `Log`.

Then tested Adam's next question directly: with the *real* `RainbowGumTomcatLog` active
(not the no-op), what happens if JUL is also fully disabled (`--logging.jul.disable=true`
+ `jul-disabled.properties`, same flags as the `nojul` scenario)?

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul | 837,095 | 27,903.2 | 1.70 | 3.05 | 5.11 | 18.10 | 1.79 | 670.5 |
| rainbowgum-nojul | 846,481 | 28,216.0 | 1.69 | 3.00 | 5.06 | 19.00 | 1.77 | 685.1 |
| rainbowgum-tomcat (JUL enabled) | 709,197 | 23,639.9 | 1.86 | 4.17 | 7.21 | 21.22 | 2.11 | 658.4 |
| rainbowgum-tomcat-noop (JUL enabled) | 690,074 | 23,002.5 | 1.91 | 4.34 | 7.42 | 24.54 | 2.17 | 672.6 |
| **rainbowgum-tomcat (JUL disabled)** | 753,041 | **25,101.4** | 1.77 | 3.86 | 6.68 | 17.52 | 1.99 | 662.0 |

Disabling JUL alongside the real `rainbowgum-tomcat` facade recovers **+6.2%**
(23,639.9 -> 25,101.4) - JUL's own installed-but-mostly-idle presence (its handler sits
on the root logger even though nothing calls raw `java.util.logging` during steady
state, per the earlier ruled-out-bypass finding) does cost something real. But that only
closes about a third of the gap: **-10.9%** still remains versus the `jul`/`nojul`
baseline, with `rainbowgum-tomcat` as the only thing left varying.

**Net across this whole investigation**: the regression is not a Tomcat version
mismatch, not per-call cost in `RainbowGumTomcatLog`'s own logic (a no-op reproduces
it), not repeated reflective construction (one-time, ~76 calls, now confirmed exactly
why), not a hidden `DirectJDKLog`-specific fast path elsewhere in Tomcat, and only
~1/3 explained by JUL's own overhead. What's left, structurally: something about a
custom `Log` implementation being active *at all* - independent of what it does,
JUL's state, or Tomcat's version - that costs ~11% even in the best case tested so
far. Still unidentified; the GC/heap-diff angle from the earlier section remains
the most promising unexplored lead.

Reproduce: same as above, add `--logging.jul.disable=true
-Djava.util.logging.config.file=$(pwd)/rainbowgum-benchmark-webapp-rainbowgum/jul-disabled.properties`
to the real (non-no-op) `-Ptomcat` build's run command.

## REUSE_BUFFER (platform threads, single targeted test) - worse than threadlocals, as expected

Separate from the `rainbowgum-tomcat` investigation (set aside for later). Adam's
question: what does `LogAppender.AppenderFlag.REUSE_BUFFER` do to platform-thread
throughput? It makes an appender allocate one buffer and reuse it across every
`append()` call, "protected by the appenders locking" (`ReuseBufferLogAppender`, whose
own source comment says "trading lock contention for less GC") - specifically via
`lock.tryLock()`, not `lock.lock()`, so a caller that loses the race returns immediately
without appending anything, rather than blocking. Adam's expectation going in: probably
worse than a per-thread (`ThreadLocal`) reuse strategy like Log4j2's, since every
concurrent caller now serializes on one shared buffer instead of each thread having its
own.

Enabled via `--logging.appender.file.flags=REUSE_BUFFER` (the file appender is named
`file` - `LogAppender.FILE_APPENDER_NAME`, auto-selected once `logging.file.name` is
set). Built *without* `-Ptomcat` so this compares cleanly against the `jul`/`nojul`
baseline rather than being muddied by the still-open regression above.

First attempt was invalid and worth recording as a lesson: forgot
`-Dlogging.file.name=./benchmark.log` (the RainbowGum app needs it as a `-D` flag, not
an `application.properties` entry - see the comment in that file) when running the JVM
manually rather than through `run.sh`. Without it the app falls back to console-only
output, so the `file` appender - and the flag set on it - was never actually exercised;
that run measured plain console logging via shell redirection instead and produced a
misleadingly fast 36,176.1 req/s that has nothing to do with `REUSE_BUFFER`. Rerun
correctly with the file output flag included:

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul (baseline) | 837,095 | 27,903.2 | 1.70 | 3.05 | 5.11 | 18.10 | 1.79 | 670.5 |
| rainbowgum-nojul (baseline) | 846,481 | 28,216.0 | 1.69 | 3.00 | 5.06 | 19.00 | 1.77 | 685.1 |
| **rainbowgum-reusebuffer** | 572,082 | **19,069.4** | 2.35 | 5.25 | 8.61 | 25.78 | 2.62 | 643.6 |

**-32.0% versus the jul baseline** (27,903.2 -> 19,069.4) - confirms Adam's expectation,
and by a wide margin: this isn't "a bit behind Log4j2's threadlocal approach", it's a
substantial regression versus RainbowGum's own default (independent-lock, no shared
buffer) appender.

Checked whether `tryLock()`'s drop-on-contention semantics were skewing this number by
skipping real work rather than genuinely costing throughput: `benchmark.log`'s
`returning response` line (logged exactly once per request, request-scoped) appears
749,447 times, matching the observed request-ID range for the full run (startup +
10s warmup + 30s measurement); total request-scoped lines (`received request` +
`processing business logic` x2 + `returning response`) is 2,997,788, dividing out to
almost exactly 4.0 lines/request as designed. No meaningful drop rate - the regression
is genuine lock-contention cost among the 50 concurrent platform threads all serializing
on the one shared buffer, not an artifact of skipped work.

Reproduce: `mvn ... install -Dmaven.javadoc.skip=true` (no `-Ptomcat`), then run the app
manually with `--logging.appender.file.flags=REUSE_BUFFER` added to the normal
`run.sh`-equivalent invocation (see `run-tomcat-jul.sh`'s `run_one` for the full command
shape, including the file-logging `-D` flags).

## THREAD_LOCAL_BUFFER (platform threads, single targeted test) - a wash, within noise of the default

Follow-up to REUSE_BUFFER above. Per Adam's request, added `LogAppender.AppenderFlag
.THREAD_LOCAL_BUFFER` (new `ThreadLocalBufferLogAppender` in `core`, branch
`feature/threadlocal-buffer-appender`, merged into this branch to build against it):
each thread gets its own reusable `ThreadLocal`-cached encoder buffer, encoding happens
outside the appender's lock exactly like `DefaultLogAppender` (no serialization there,
unlike `REUSE_BUFFER`), and the lock is only held for the final `output.write(...)` -
the design log4j2's own per-thread reuse is presumably closer to. Expectation going in
(Adam's and mine): should recover most or all of `REUSE_BUFFER`'s loss versus the
default appender, since the lock-contention-on-encode problem is gone.

Same setup as the `REUSE_BUFFER` test: built *without* `-Ptomcat`, 50 platform threads,
10s warmup / 30s measurement, via `--logging.appender.file.flags=THREAD_LOCAL_BUFFER`.

| label | requests | req/s | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul (baseline) | 837,095 | 27,903.2 | 1.70 | 3.05 | 5.11 | 18.10 | 1.79 | 670.5 |
| rainbowgum-nojul (baseline) | 846,481 | 28,216.0 | 1.69 | 3.00 | 5.06 | 19.00 | 1.77 | 685.1 |
| **rainbowgum-threadlocalbuffer** | 830,104 | **27,670.1** | 1.72 | 3.09 | 5.17 | 17.61 | 1.81 | 666.2 |

**Within noise of the baseline** (-0.8% vs. jul, -1.9% vs. nojul - both well inside the
run-to-run variance seen elsewhere in this file). So the hypothesis is half confirmed:
`THREAD_LOCAL_BUFFER` does fully recover `REUSE_BUFFER`'s -32.0% loss (as expected,
since the lock no longer guards encoding), but it does not measurably *beat*
`DefaultLogAppender`'s baseline the way reused-buffer designs sometimes promise to.
Sanity-checked GC event count from the JFR recording (`jdk.GCHeapSummary`): 496 events
here vs. 506/516 for the jul/nojul baselines - same order, no meaningful allocation win
either. Plausible explanation: `DefaultLogAppender`'s per-event buffer (a
`StringBuilder`-backed `Buffer`) is cheap to allocate/collect (young-gen, short-lived)
relative to everything else a request already does (Jackson JSON serialization, Tomcat's
own NIO path, MDC array copies), so removing that one allocation doesn't move the needle
on this workload. `THREAD_LOCAL_BUFFER` may still matter more on outputs/encoders with
heavier buffer setup cost than a `StringBuilder`, or under higher sustained throughput
than this endpoint produces - not tested here.

Reproduce: same as the `REUSE_BUFFER` reproduce steps above, substituting
`THREAD_LOCAL_BUFFER` for `REUSE_BUFFER` in the flag value.

## DirectByteBufferEncoder / rainbowgum-nio (platform threads) - underperforms in all three appender pairings tested

Adam's `feature/nio-direct-bytebuffer-encoder` branch (experimental `rainbowgum-nio`
module, `DirectByteBufferEncoder`/`DirectByteBufferBuffer`): formats into a reused
`StringBuilder` like the standard path, then encodes directly into a `ByteBuffer` via a
`CharsetEncoder` on drain, skipping the intermediate `String`/`byte[]` allocation
`LogOutput.write(LogEvent, String)`'s default does - the same technique Log4j2's
garbage-free logging uses. It was 73 commits behind `main` (2 of its 3 commits had
already landed separately and rebased away cleanly with no conflicts; only the module
itself needed replaying) - rebased, force-pushed. Adam's hypothesis going in: Log4j2's
edge over RainbowGum is likely this NIO/garbage-free write path plus its mutable
garbage-free timestamp, rather than anything about buffer-reuse strategy (which the
`THREAD_LOCAL_BUFFER` test above already showed doesn't move the needle by itself).

**Wiring it up**: the encoder is opt-in only via `.encoder(DirectByteBufferEncoder.of(formatter))`
- no URI scheme, no default wiring, no property support - so it doesn't plug into this
Spring Boot app's property-driven config out of the box. Added a small
`NioEncoderConfigurator` (`RainbowGumServiceProvider.Configurator`, registered via a
hand-written `META-INF/services` file - the module doesn't use the `@ServiceProvider`
annotation processor and adding it just for one class wasn't worth the extra `pom.xml`
wiring) that registers an `nio` URI scheme wrapping `DirectByteBufferEncoder` around a
`PatternCompiler`-compiled formatter of the same `FILE_LOG_PATTERN` system property
Spring's own file encoder uses. An explicit `logging.appender.file.encoder` property
always wins over Spring's `OutputType`-based default (confirmed by reading
`LogAppenderRegistry.resolveEncoder`'s `encoderProperty.or(...)` fallback), so
`--logging.appender.file.encoder=nio` opts a run in without touching Spring's own
encoder wiring or needing it to run in any particular order relative to Spring's
`RainbowGumLoggingSystemFactory.initialize()`.

**A structural catch found while wiring it up, relevant to interpreting the numbers
below**: `DirectByteBufferBuffer`'s actual `CharsetEncoder` work happens in
`drain(LogOutput, LogEvent)`, which every current appender (`DefaultLogAppender`,
`ReuseBufferLogAppender`, `ThreadLocalBufferLogAppender`) calls from *inside* its lock,
via `output.write(event, buffer)`. Only the `StringBuilder` formatting step
(`encoder.encode(event, buffer)`, i.e. `doEncode`) happens outside the lock for
`DefaultLogAppender`/`ThreadLocalBufferLogAppender` - the actual char-to-byte encoding
this module exists to speed up is *not* one of the things "encode outside the lock"
currently moves out, for any appender. This isn't a bug (correctness is fine, buffers
stay thread-confined) but it does mean none of the three pairings below get the full
"do the expensive part concurrently, only serialize the write" benefit the appender
flags otherwise promise.

Tested three pairings, same setup as `REUSE_BUFFER`/`THREAD_LOCAL_BUFFER` above (50
platform threads, no `-Ptomcat`, 10s warmup / 30s measurement):

| label | requests | req/s | p50 ms | p99 ms | max ms | RSS avg MB | GC events |
|---|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul (baseline) | 837,095 | 27,903.2 | 1.70 | 5.11 | 18.10 | 670.5 | 506 |
| rainbowgum-nojul (baseline) | 846,481 | 28,216.0 | 1.69 | 5.06 | 19.00 | 685.1 | 516 |
| **nio (default appender, fresh buffer/event)** | 649,851 | **21,661.7** | 2.13 | 7.22 | 25.24 | 677.1 | 628 |
| **nio + REUSE_BUFFER** | 499,161 | **16,638.7** | 2.68 | 9.72 | 23.71 | 657.5 | (not captured) |
| **nio + THREAD_LOCAL_BUFFER** | 708,210 | **23,607.0** | 1.95 | 6.65 | 21.76 | 670.2 | 426 |

All three lose to the baseline: **-22.4%**, **-40.4%**, and **-15.4%** respectively.

- **`nio` alone (no reuse flag)** is worst-case-per-event: `DefaultLogAppender` allocates
  a fresh `Buffer` every call, and `DirectByteBufferBuffer`'s constructor always
  allocates a full `initialByteCapacity` (8192-byte, i.e. 8KB) backing array regardless
  of how short the actual message is - far bigger than the `byte[]` the default
  `String.getBytes(UTF_8)` path would produce for these short log lines. GC events went
  *up* versus baseline (628 vs. 506/516), the opposite of the intended effect, confirming
  this reads as an allocation regression rather than noise.
- **`nio` + `REUSE_BUFFER`** is worst overall: stacks the structural issue above (the
  `CharsetEncoder` work happening under lock) on top of `REUSE_BUFFER`'s own already-
  confirmed lock-contention regression (both `encoder.encode()` *and* `drain()` happen
  inside the lock for this appender), so both costs compound.
- **`nio` + `THREAD_LOCAL_BUFFER`** is the best of the three and *does* deliver the
  reduced-GC benefit the module promises - 426 GC events, fewer than either baseline -
  but that reduction doesn't translate into a throughput win: still -15.4% below
  baseline. This is the most informative result of the three: it isolates that the
  bottleneck for this workload/appender combination is lock-held time (now inflated by
  `CharsetEncoder.encode()` running inside it), not GC pressure - so a change that only
  reduces garbage without also getting the encode step outside the lock doesn't pay off
  here, no matter how little garbage it produces.

**On Adam's hypothesis**: this doesn't support "the ByteBuffer write path alone would
close the Log4j2 gap" - at least not this implementation, in this benchmark, paired with
any of RainbowGum's three current appenders. If Log4j2's garbage-free path really is
part of its edge, it likely also needs the encode step to happen genuinely outside any
appender-held lock (e.g. Log4j2's synchronous file appenders typically hold their own
per-appender lock scoped tighter around just the write, or use a design where a single
owning thread never contends at all) - a real fix here would mean moving the
`CharsetEncoder` work from `drain()` into `encode()`/`doEncode()` so it participates in
the same "outside the lock" window `DefaultLogAppender`/`ThreadLocalBufferLogAppender`
already give the `StringBuilder` formatting step, which wasn't attempted here (out of
scope for a first benchmark pass - flagging as the obvious next step for this
experimental module). The garbage-free *timestamp* half of Adam's hypothesis is
untested by this session - would need a separate, isolated experiment.

Reproduce: rebase `feature/nio-direct-bytebuffer-encoder` onto current `main` (only the
`rainbowgum-nio` module commit needs replaying; the other two already land separately),
merge into this branch, add `NioEncoderConfigurator` +
`META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider` +
`rainbowgum-nio`/`rainbowgum-pattern` compile deps to
`rainbowgum-benchmark-webapp-rainbowgum`'s `pom.xml` (all committed on this branch), then
run with `--logging.appender.file.encoder=nio` (add `--logging.appender.file.flags=...`
for the paired variants) - same JVM flags as the `REUSE_BUFFER`/`THREAD_LOCAL_BUFFER`
reproduce steps above.

## SYNCHRONIZED_THREAD_LOCAL_BUFFER (platform threads) - a genuine win, +9% over baseline

Adam's theory, from a prior deep dive into Log4j2's source: its garbage-free appenders
transfer their buffer to the output inside a plain `synchronized` block rather than a
`java.util.concurrent` lock, and Adam's own past microbenchmarking of `synchronized` vs.
`ReentrantLock` had "surprisingly results" - strong enough that it shaped the existing
`AppenderLock` abstraction's design in anticipation of trying this. Asked to actually try
it: add a new appender that uses `synchronized` instead of `AppenderLock`.

This could not be done as a new `AppenderLock` subclass - Java has no way to acquire a
monitor in one method call (`lock()`) and release it in another (`unlock()`) the way
every existing `AppenderLock` implementation's callers assume; `synchronized` is
lexically block-scoped only. So `LogAppender.AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER`
dispatches to a new `SynchronizedThreadLocalBufferLogAppender` (`core`, same branch as
`THREAD_LOCAL_BUFFER`) that extends `AbstractLogAppender` directly (not `LockLogAppender`)
and wraps its critical sections in literal `synchronized (monitor)` blocks. Otherwise
identical to `THREAD_LOCAL_BUFFER`: per-thread reused buffer, formatting happens outside
the block, only the final `output.write(...)` is inside it. `REENTRY_DROP`/`REENTRY_LOG`
are not supported (ignored) since they depend on `AppenderLock`'s `ReentrantLock`-based
same-thread reentrancy check, which has no `synchronized` equivalent wired up here.

Same setup as the other single-flag tests (50 platform threads, no `-Ptomcat`), via
`--logging.appender.file.flags=SYNCHRONIZED_THREAD_LOCAL_BUFFER`. Ran twice back to back
given how different the result looked from every other appender-flag experiment so far:

| label | requests | req/s | p50 ms | p99 ms | max ms | RSS avg MB | GC events |
|---|---:|---:|---:|---:|---:|---:|---:|
| rainbowgum-jul (baseline) | 837,095 | 27,903.2 | 1.70 | 5.11 | 18.10 | 670.5 | 506 |
| rainbowgum-nojul (baseline) | 846,481 | 28,216.0 | 1.69 | 5.06 | 19.00 | 685.1 | 516 |
| **sync-tlb (run 1)** | 920,091 | **30,669.7** | 1.57 | 4.18 | 19.22 | 667.6 | 540 |
| **sync-tlb (run 2)** | 903,731 | **30,124.4** | 1.60 | 4.24 | 23.00 | 675.1 | (not captured) |

**+9.9% / +8.7%** over the jul/nojul baselines on run 1, **+7.9% / +6.8%** on run 2 -
reproducible, not noise (compare to `THREAD_LOCAL_BUFFER`'s own within-noise -0.8%/-1.9%
result with the identical buffer strategy and everything else held constant - the only
variable that changed is `AppenderLock`/`ReentrantLock` vs. a plain `synchronized`
block). p50/p99 both improved alongside throughput, not just req/s in isolation. Line
counts in `benchmark.log` scale proportionally with the higher throughput as usual - no
sign of dropped events. This is the first appender-flag experiment this session that
beats the default, and by a comfortable margin.

**Also tried the `nio` encoder paired with this lock strategy** (`--logging.appender.file.encoder=nio
--logging.appender.file.flags=SYNCHRONIZED_THREAD_LOCAL_BUFFER`), since Log4j2's actual
design combines both ideas (garbage-free encode *and* a synchronized transfer) and this
pairing is structurally the closest match to what Adam described:

| label | req/s | vs. baseline |
|---|---:|---:|
| nio + THREAD_LOCAL_BUFFER (ReentrantLock, from above) | 23,607.0 | -15.4% |
| **nio + SYNCHRONIZED_THREAD_LOCAL_BUFFER** | **26,157.1** | -6.3% |

Switching the lock recovers a further 9 points versus the `ReentrantLock` version
(confirms `synchronized` is winning independent of what's inside the critical section -
consistent with the plain-encoder result above), but still doesn't fully close the gap
to the plain baseline - the `nio` encoder's own overhead (the `CharsetEncoder`/`ByteBuffer`
machinery, still invoked from inside the lock either way since `drain()`'s structural
issue documented above is unrelated to which lock is used) costs more than
`synchronized` alone recovers here.

**Conclusion**: Adam's hypothesis about `synchronized` is confirmed for RainbowGum's
appender-locking specifically, independent of buffer/encoder strategy - `synchronized`
beats `AppenderLock`'s `ReentrantLock` by a real, reproducible margin (~7-10%) on this
workload with 50 contending platform threads. Combined with the `nio` encoder it's still
net-negative only because the encoder itself has its own separate, larger cost, not
because `synchronized` failed to help there too. Given this is a genuine win and not
just a wash, worth discussing whether `SYNCHRONIZED_THREAD_LOCAL_BUFFER` (or a
`synchronized`-based variant of the plain `DefaultLogAppender` path, not yet built) is a
candidate to become RainbowGum's actual default rather than staying an opt-in flag - not
decided here, flagging it for that conversation. Untested: virtual threads (the
`THREAD_LOCAL_BUFFER` per-thread-buffer caveat about high thread cardinality still
applies), and the garbage-free-timestamp half of Adam's original Log4j2 hypothesis is
still completely separate from this result and remains untested.

Reproduce: same as `THREAD_LOCAL_BUFFER` above, substituting
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` for the flag value (add `--logging.appender.file.encoder=nio`
for the paired variant).

## SYNCHRONIZED_THREAD_LOCAL_BUFFER under virtual threads - still wins, but the margin shrinks a lot

Adam's follow-up: `synchronized` historically pinned the carrier platform thread when
called from a virtual thread (sometimes *appearing* faster locally while being bad at
scale), a problem later JVMs fixed (JEP 491, finalized JDK 24) - but he suspected a
regular `java.util.concurrent` lock might still be preferable under virtual threads even
with the fix. This machine runs Temurin 26.0.2 (`java -version`), well past the fix, so
the question was whether that theoretical advantage actually shows up here.

**Setup**: same single-app harness, `--spring.threads.virtual.enabled=true` added (the
same property `run-all.sh`'s `VIRTUAL_THREADS` toggle uses, confirmed active in this repo
previously - see the virtual-threads section earlier in this file for how that was
verified independently of any logging code). Ran a fresh VT baseline (no appender flags)
plus `THREAD_LOCAL_BUFFER` and `SYNCHRONIZED_THREAD_LOCAL_BUFFER`, twice each for the two
flag variants given how close the numbers turned out to be:

| label | requests | req/s | p50 ms | p99 ms | max ms |
|---|---:|---:|---:|---:|---:|
| vt-baseline (no flags) | 771,607 | 25,720.2 | 1.93 | 3.43 | 13.25 |
| vt + THREAD_LOCAL_BUFFER (run 1) | 804,043 | 26,801.4 | 1.88 | 3.19 | 14.42 |
| vt + THREAD_LOCAL_BUFFER (run 2) | 782,990 | 26,099.7 | 1.91 | 3.26 | 13.47 |
| vt + SYNCHRONIZED_THREAD_LOCAL_BUFFER (run 1) | 821,975 | 27,399.2 | 1.83 | 3.20 | 15.30 |
| vt + SYNCHRONIZED_THREAD_LOCAL_BUFFER (run 2) | 810,476 | 27,015.9 | 1.85 | 3.26 | 14.96 |

Averaging each pair: `THREAD_LOCAL_BUFFER` ~26,450.6 req/s (+2.8% over vt-baseline),
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` ~27,207.6 req/s (+5.8% over vt-baseline, **+2.9% over
`THREAD_LOCAL_BUFFER`**) - and every `synchronized` run beat every `ReentrantLock` run
with no overlap (27,015.9 to 27,399.2 vs. 26,099.7 to 26,801.4), so the ordering itself
looks real even though the gap is much smaller than on platform threads. **Compare to the
platform-thread result**: there `synchronized` beat `THREAD_LOCAL_BUFFER` by ~9-10
points (30,124-30,669 vs. 27,670.1); here it's ~+2.9 points. The advantage didn't
reverse, but it shrank by roughly two thirds - exactly the direction Adam's intuition
predicted, even though the fix keeps it from going negative on this JDK.

**Checked for actual pinning directly** rather than inferring it from throughput alone:
reran the `SYNCHRONIZED_THREAD_LOCAL_BUFFER` + virtual-threads scenario with
`jdk.VirtualThreadPinned` explicitly force-enabled in the JFR recording
(`-XX:StartFlightRecording=...,jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=0ms`
- this event is *not* part of the default `profile` settings preset, so the earlier runs'
JFR files show a misleading `0/0` for it that reflects "not collected," not "zero
occurred"; had to force it on to get a real answer). With it explicitly enabled and a
0ms threshold (so even a single nanosecond of pinning would be captured): **zero
`jdk.VirtualThreadPinned` events over the full 30s/50-concurrency run**, while
throughput held steady with the flag on (27,015.9 req/s, consistent with the other run).
Confirms JEP 491 is doing its job for this specific `synchronized (monitor) { ... }`
appender - no carrier-thread pinning is happening on this JDK, so the smaller VT margin
above is not "pinning is silently costing something" - it's a genuinely different,
smaller win under virtual threads for other reasons (most plausibly: virtual-thread
scheduling/park-unpark overhead already dominates more of the total latency budget under
VT than the lock primitive itself does, leaving less headroom for the lock choice to
matter either way - consistent with `THREAD_LOCAL_BUFFER` itself also showing a small
positive margin here despite being a wash on platform threads).

**Conclusion**: on a JVM with the pinning fix (JDK 24+, this machine is on 26), plain
`synchronized` remains at least as good as `AppenderLock`'s `ReentrantLock` under virtual
threads too - it doesn't flip negative, just shrinks from a clear win to a modest one.
Older JVMs (pre-JDK 24) were not tested here and per Adam's description would likely tell
a very different story given the historical pinning behavior; if RainbowGum needs to
support those, that would be a real constraint on making `synchronized`-based appenders
the default rather than an opt-in flag.

Reproduce: add `--spring.threads.virtual.enabled=true` to any of the
`THREAD_LOCAL_BUFFER`/`SYNCHRONIZED_THREAD_LOCAL_BUFFER` reproduce steps above. For the
pinning check specifically, add `jdk.VirtualThreadPinned#enabled=true,jdk.VirtualThreadPinned#threshold=0ms`
to the `-XX:StartFlightRecording` settings list and `jfr print --events
jdk.VirtualThreadPinned target/app.jfr` after the run.

## Deep dive: what Log4j2 actually does for file/console (code reading, no benchmark run)

Adam's "bigger buffer" hypothesis for Log4j2's remaining platform-thread edge, and a
precise look at its locking, using `log4j-core` 2.25.4 (the exact version this session's
benchmarks pulled in via `spring-boot-starter-log4j2`) - no code changed, no benchmark
run, just reading `log4j-core`'s source/bytecode and Spring Boot's packaged default
config.

**Buffer size - the hypothesis doesn't hold for what was actually benchmarked.**
Spring Boot 4.1's shipped `log4j2-file.xml` (packaged inside `spring-boot-4.1.0.jar`)
wires up a plain `<RollingFile>`, i.e. `RollingFileAppender`, whose
`DEFAULT_BUFFER_SIZE = 8192` with `bufferedIO=true` by default - **the same 8KB
RainbowGum's `FileOutputBuilder.DEFAULT_BUFFER_SIZE` and Logback both already use.**
There *is* a real 256KB buffer in Log4j2 (`RandomAccessFileManager.DEFAULT_BUFFER_SIZE =
256 * 1024`), but that belongs to `RandomAccessFileAppender`/`RollingRandomAccessFileAppender`
(the `<RollingRandomAccessFile>` XML element) - not what Spring Boot's default config
uses, so it isn't a factor in any of this session's numbers.

**Locking - this is the real answer, and it's more precise than "uses `synchronized`
instead of a `Lock`."** `OutputStreamManager` does use `synchronized` (`write(...)`,
`flush()`, `writeBytes(ByteBuffer)`), but the actual trick lives one layer up, in
`TextEncoderHelper`/`StringBuilderEncoder`, whose own doc comments say it outright -
*"[TextEncoderHelper] Attempts to postpone synchronizing on the destination as long as
possible to minimize lock contention"* and *"[StringBuilderEncoder] uses ThreadLocals to
avoid locking as much as possible."* The actual pipeline
(`AbstractOutputStreamAppender.directEncodeEvent` -> `layout.encode(event, manager)`):

1. Pattern formatting writes into a **per-thread `StringBuilder`**
   (`AbstractStringLayout`'s own `ThreadLocal<StringBuilder>`) - no lock.
2. The char-to-byte transcoding - the actual `CharsetEncoder` work - runs against a
   **per-thread scratch `CharBuffer`(2048 chars)/`ByteBuffer`(8192 bytes)**, entirely
   outside any lock.
3. Only the already-encoded bytes get copied from that thread-local scratch buffer into
   the shared destination buffer, and **that copy is the only thing wrapped in
   `synchronized(destination)`** - for a typical short log line (well under 2048
   chars/8192 bytes) this is essentially just a `System.arraycopy`-scale critical
   section, nothing more.

This is precisely the structural gap this session already found in RainbowGum's `nio`
module: `DirectByteBufferBuffer.drain()` (see the `DirectByteBufferEncoder` section
above) does its `CharsetEncoder` work *inside* the appender's lock, where Log4j2 does
that same work *outside* and locks only the final byte copy. It's a concrete,
independent-of-lock-type target (this would matter even with `ReentrantLock`, and
matters on top of whatever `SYNCHRONIZED_THREAD_LOCAL_BUFFER` itself buys) for actually
closing the remaining platform-thread gap to Log4j2, separate from both the buffer-size
question (ruled out above) and the `synchronized`-vs-`ReentrantLock` question (already
answered by the `SYNCHRONIZED_THREAD_LOCAL_BUFFER` results above).

**Timestamp** (brief, lower priority): `FixedDateFormat.format()`
(`org.apache.logging.log4j.core.util.datetime.FixedDateFormat`) caches the date portion
as a `char[]`, copied via `System.arraycopy` each call, and only recomputes the
time-of-day portion (hour/min/sec/millis) fresh via direct integer division/modulo
written straight into the destination `char[]` - no `Instant`/`Date`/`DateTimeFormatter`
allocated per event at all. Source comments flag the method as profiling-sensitive
("Profiling showed this method is important to log4j performance. Modify with care!").
Still untested here whether this actually moves the needle on RainbowGum's own numbers -
this section is a code-reading exercise only, no benchmark was run against it.

## Fixing DirectByteBufferBuffer's lock-scope gap - closes the entire nio gap

Direct follow-up to the deep dive above. Moved `DirectByteBufferBuffer`'s
`CharsetEncoder` work (the actual char-to-byte transcoding) out of `drain()` and into a
new `encodeToByteBuffer()`, called from `DirectByteBufferEncoder.doEncode()` right after
formatting - i.e. the exact fix the deep dive identified: matching Log4j2's structure,
where the transcoding happens against a scratch buffer *before* any lock is involved, and
only the already-encoded bytes get copied in under a lock. (`core`,
`feature/nio-direct-bytebuffer-encoder`, merged `feature/threadlocal-buffer-appender`
in first since the javadoc now needs to reference `THREAD_LOCAL_BUFFER`/
`SYNCHRONIZED_THREAD_LOCAL_BUFFER`.) `REUSE_BUFFER` is unaffected by this fix - it
acquires its lock before calling the encoder at all, so the transcoding stays serialized
there regardless of where the code lives.

Reran all three `nio` pairings from the earlier `nio` section, same setup (50 platform
threads, no `-Ptomcat`):

| label | requests | req/s | before fix | vs. baseline |
|---|---:|---:|---:|---:|
| rainbowgum-jul (baseline) | 837,095 | 27,903.2 | - | - |
| **nio (default appender, fresh buffer/event)** | 740,382 | **24,679.4** | 21,661.7 (+13.9%) | -11.6% |
| **nio + THREAD_LOCAL_BUFFER** | 832,916 | **27,763.9** | 23,607.0 (+17.6%) | **-0.5%** |
| **nio + SYNCHRONIZED_THREAD_LOCAL_BUFFER** | 902,605 | **30,086.8** | 26,157.1 (+15.0%) | **+7.8%** |

Every pairing improved, and the two buffer-reuse pairings essentially closed the entire
gap to their plain-TTLL equivalents: `nio + THREAD_LOCAL_BUFFER` (27,763.9) now lands
right on top of plain `THREAD_LOCAL_BUFFER`'s own 27,670.1, and `nio +
SYNCHRONIZED_THREAD_LOCAL_BUFFER` (30,086.8) lands right on top of plain
`SYNCHRONIZED_THREAD_LOCAL_BUFFER`'s ~30,124-30,669 average - both within normal
run-to-run noise of their non-`nio` counterparts rather than showing any residual
"garbage-free encoding costs something" penalty. That confirms the entire prior nio
regression, for these two pairings, was the lock-scope bug - not the `CharsetEncoder`
machinery being inherently slower than `String.getBytes(UTF_8)` for short ASCII/UTF-8
log lines.

**"nio alone" (no buffer-reuse flag, `DefaultLogAppender`) improved too but is still
behind baseline** (-11.6%, down from -22.4%) - expected, since `DefaultLogAppender`
still allocates a *fresh* `DirectByteBufferBuffer` (with its full 8KB backing
`ByteBuffer`) per event regardless of this fix; that's a separate, still-unfixed
allocation problem (confirmed again via GC events: 710 this run, still elevated versus
the 506-516 baseline range). The lock-scope fix and the no-reuse allocation problem are
independent issues - this result cleanly separates them: fixing lock scope alone
recovered about half the gap for the no-reuse case, while completely closing it for
both buffer-reuse pairings.

**Bottom line**: `nio + SYNCHRONIZED_THREAD_LOCAL_BUFFER` is now the fastest
configuration measured anywhere in this file for the plain-text/platform-thread
scenario - garbage-free encoding *and* the synchronized lock win, stacked, with no
remaining penalty for either. Given `THREAD_LOCAL_BUFFER`'s own per-thread-buffer
caveat, virtual threads were not retested here for the `nio`-paired variants - would be
the natural next check.

Reproduce: same as the earlier `nio` pairings above - the fix already lives in
`feature/nio-direct-bytebuffer-encoder`/this branch, no extra flags needed versus the
original reproduce steps.

## The byte-buffer encoding is now automatic (folded into core) - plain default regresses, needs both appenders flagged to recover

Adam decided the whole `rainbowgum-nio` module should fold into `core` as the *default*
behavior of `FormatterEncoder` (what `LogEncoder.of(LogFormatter)` returns), dispatching
on the output's `WriteMethod` hint rather than requiring opt-in wiring - merged into
`main` directly (branch deleted). Reran the webapp benchmark to see what changed
automatically now that no app code/properties are doing anything special.

**Important wiring note first**: this broke the build here - `rainbowgum-nio` no longer
exists as a module, so `NioEncoderConfigurator` (added earlier in this file's history)
and its `pom.xml` dependency were now dead code referencing a deleted artifact. Removed
both; the `nio` encoder scheme is also just redundant now. Also hit a stale-build trap
worth recording: after deleting the class, `mvn install -DskipTests` alone left the old
`META-INF/services` entry baked into the *already-repackaged* Spring Boot fat jar
(Maven's incremental compile/repackage didn't purge it), so the app failed at startup
with `ServiceConfigurationError: ... NioEncoderConfigurator not found` even though the
source was gone. Fixed by `rm -rf` on that module's `target/` before rebuilding.

**The plain default (no appender flags at all) is now a regression**, not a wash:

| label | requests | req/s | GC events |
|---|---:|---:|---:|
| rainbowgum-jul (old baseline, pre-fold) | 837,095 | 27,903.2 | 506 |
| rainbowgum-nojul (old baseline, pre-fold) | 846,481 | 28,216.0 | 516 |
| **new plain default (no flags)** | 793,511 | **26,450.4** | **1008** |

**-5.2% / -6.3%**, and GC events nearly doubled versus the old baseline. Root cause:
`FileOutput` (`BYTE_BUFFER`) and the console/stdout base (`BYTES`) *both* now get
`DirectByteBufferBuffer` by default, and Spring's own console encoder is built via the
same `LogEncoder.of(LogFormatter)` factory this change modified - so it's not just file
that changed, console did too. With no buffer-reuse flag set on either appender,
`DefaultLogAppender` allocates a *fresh* `DirectByteBufferBuffer` (with its full 8KB
backing `ByteBuffer`) per event, on *both* outputs now instead of just one - literally
doubling the allocation regression the earlier `nio`-alone test already showed
(710 GC events, file only) rather than fixing it.

**Recovering the win requires the buffer-reuse flag on *both* appenders** - the
`logging.appender.{name}.flags` property is per-appender-name, so
`--logging.appender.file.flags=X` alone (the flag value used throughout this file's
single-appender tests) only fixes file; console still allocates fresh:

| label | requests | req/s | vs. old baseline | GC events |
|---|---:|---:|---:|---:|
| **THREAD_LOCAL_BUFFER on file + console** | 852,766 | **28,425.5** | **+1.9% / +0.7%** | (not captured) |
| **SYNCHRONIZED_THREAD_LOCAL_BUFFER on file + console** | 905,925 | **30,197.5** | **+8.2% / +7.0%** | **508** |

Both recover fully once applied to both appenders - the `SYNCHRONIZED_THREAD_LOCAL_BUFFER`
pairing lands right back in the old baseline's GC-event range (508 vs. 506/516) and
matches the best numbers seen anywhere in this file, consistent with every earlier
`SYNCHRONIZED_THREAD_LOCAL_BUFFER` result once the allocation problem is actually fixed
on every output paying it.

**This has a real implication for Adam's stated plan** ("add synchronized as the
default, or sniff pre-JDK-24 and fall back to ReentrantLock in that case"): switching
only the *lock primitive* to `synchronized` by default, without *also* defaulting to a
buffer-reuse appender strategy (`THREAD_LOCAL_BUFFER` or the `SYNCHRONIZED_` variant)
on every appender the byte-buffer encoder now silently applies to, would ship the
allocation regression above by default - worse than before this line of work started,
for anyone who doesn't explicitly set appender flags. The encoder change and the
appender-default change are coupled now in a way they weren't before: making
`FormatterEncoder` byte-buffer-aware everywhere effectively obligates a buffer-reuse
appender default too, not just a lock-primitive default, or most users get the -5%/-6%
regression with no offsetting win.

Reproduce: `./run-all.sh`-equivalent single-app harness as used throughout this file
(50 platform threads, no `-Ptomcat`); for the "both appenders" tests add
`--logging.appender.console.flags=<FLAG>` alongside the existing
`--logging.appender.file.flags=<FLAG>` argument.

## Smart appender defaults - the plain default is fixed, no flags needed

Direct fix for the regression above. Adam's stated goal: out of the box, RainbowGum
should be as fast or faster than the other Spring Boot logging frameworks - so the
buffer-reuse win needed to become the *default*, not something a user has to discover
and opt into via `logging.appender.{name}.flags`. `DirectLogAppender#defaultAppender`
(`core`, `feature/smart-appender-defaults`, merged into this branch) now picks
`SynchronizedThreadLocalBufferLogAppender` on JDK 24+ or `ThreadLocalBufferLogAppender`
below that (sniffed once via `Runtime.version().feature()`) for *any* appender with no
buffer-strategy flag set - `DefaultLogAppender` is now only reachable via
`REENTRY_DROP`/`REENTRY_LOG` (which neither `ThreadLocalBuffer` appender supports), not
directly requestable itself. Both `ThreadLocalBuffer` appenders also became
virtual-thread aware: a call from a
virtual thread now bypasses the `ThreadLocal` and uses a fresh buffer for that one event
(checked per-call via `Thread.isVirtual()`, not sniffed once, since there's no reliable
JVM-level "is this app using virtual threads" signal and workloads can mix platform/
virtual threads on the same appender).

Reran the plain no-flags default (same setup as the regression above), twice given how
consequential a default-behavior change this is:

| label | requests | req/s | vs. old baseline | GC events |
|---|---:|---:|---:|---:|
| rainbowgum-jul (old baseline) | 837,095 | 27,903.2 | - | 506 |
| rainbowgum-nojul (old baseline) | 846,481 | 28,216.0 | - | 516 |
| plain default before this fix | 793,511 | 26,450.4 | -5.2% / -6.3% | 1008 |
| **plain default, run 1 (after fix)** | 887,578 | **29,585.9** | **+6.0% / +4.9%** | 488 |
| **plain default, run 2 (after fix)** | 914,008 | **30,466.9** | **+9.2% / +8.0%** | (not captured) |

No flags, no properties, nothing app-specific - just the merged default. GC events back
in (and slightly under) the old baseline's 506-516 range, confirming the fresh-buffer-per-
event problem is actually gone rather than just moved around. This lands right alongside
the manually-flagged `SYNCHRONIZED_THREAD_LOCAL_BUFFER`-on-both-appenders result from the
regression section above (30,197.5) - as expected, since that's exactly what the new
default now does automatically.

Reproduce: merge `feature/smart-appender-defaults`, run the plain single-app harness with
no `--logging.appender.*.flags` arguments at all.

## Full three-way rerun on current main - GELF and virtual threads, no tomcat, no adjustments

First rerun of the full `run-all.sh` three-way comparison (logback/log4j2/rainbowgum, not
the single-app isolation harness used throughout this file) since `main` picked up
everything from this session - the byte-buffer encoder folded into core, smart appender
defaults, `AppenderLock` removed. Per Adam: JUL should stay on (the default, no flags
needed), no `-Ptomcat`, and the three apps' config should need no special adjustment
relative to each other.

**Two real bugs found and fixed before any numbers came out of this:**

1. `run-all.sh`'s build step had `-Ptomcat` baked in unconditionally, at odds with every
   other test in this file (which deliberately excludes `rainbowgum-tomcat`, still an
   open, paused regression) and with this rerun's explicit ask. Removed it - the build
   line no longer passes `-Ptomcat` at all.
2. `rainbowgum-benchmark-webapp-logback` failed to start entirely with a
   `NoSuchMethodError` on `LoggerContext.initCollisionMaps()` - a classic
   logback-classic/logback-core version mismatch. Cause: `benchmark/pom.xml` (the shared
   parent for both the old micro-benchmarks and the new `benchmark/webapp` tree) pinned
   `logback-classic` to a stale `1.5.12` directly in its own `dependencyManagement`, which
   silently overrides whatever paired version Spring Boot 4.1's BOM would otherwise manage
   for `logback-classic`/`logback-core` in the webapp app specifically - stale pin
   `1.5.12` vs. Spring's own `logback-core:1.5.34`. (This is exactly what the
   `ch.qos.logback-logback-classic-1.6.3` Dependabot PR - deliberately excluded from the
   recent build-tool-dependency bump commits as "not build tooling" - was trying to fix,
   just not in the way that actually resolves it here.) Fixed by moving the
   `logback-classic`/`log4j-slf4j2-impl` version pins down into the two old
   micro-benchmark modules that actually need an explicit version (they had no version of
   their own, relying entirely on the shared parent's pin) and removing them from the
   shared parent entirely, so `benchmark/webapp`'s Spring Boot apps are free to use
   whatever paired versions Spring's own BOM manages.

**GELF (platform threads, structured JSON logging via `STRUCTURED_FORMAT=gelf`):**

| label | requests | req/s | p50 ms | p99 ms | max ms | RSS avg MB | GC events |
|---|---:|---:|---:|---:|---:|---:|---:|
| logback-gelf | 671,571 | 22,385.7 | 2.03 | 6.47 | 19.52 | 665.4 | 506 |
| log4j2-gelf | 496,268 | 16,542.3 | 2.63 | 9.79 | 28.63 | 624.2 | 332 |
| **rainbowgum-gelf** | 737,614 | **24,587.1** | 1.89 | 6.11 | 21.12 | 699.9 | 416 |

**RainbowGum wins outright** - +9.8% over logback, +48.6% over log4j2 - with no flags, no
properties, nothing app-specific beyond the existing `GelfSpringRainbowGumServiceProvider`
(which every prior GELF test in this file already needed). GC events land between the
other two despite the highest throughput, so this isn't won by skipping work. This is
squarely the "out of the box at least as fast as the alternatives" goal from earlier in
this file, now holding up in the actual three-way harness rather than just the
single-app-vs-historical-baseline comparisons used to develop it.

**Virtual threads (`VIRTUAL_THREADS=true`, same otherwise):**

| label | requests | req/s | p50 ms | p99 ms | max ms | RSS avg MB | GC events |
|---|---:|---:|---:|---:|---:|---:|---:|
| logback-vt | 763,947 | 25,464.9 | 1.97 | 3.16 | 13.02 | 636.4 | 246 |
| log4j2-vt | 566,391 | 18,879.7 | 2.47 | 7.92 | 18.61 | 630.2 | 272 |
| **rainbowgum-vt** | 559,853 | **18,661.8** | 2.69 | 6.64 | 22.95 | 637.8 | **730** |

**Not a win here** - roughly tied with log4j2 (-1.2%) but -26.7% behind logback. GC events
are the standout: 730, nearly 3x log4j2's 272 and logback's 246. This is the
virtual-thread fallback path in `ThreadLocalBufferLogAppender`/
`SynchronizedThreadLocalBufferLogAppender` doing exactly what it was designed to do -
`Thread.isVirtual()` bypasses the `ThreadLocal` and allocates a fresh buffer for that one
event - but Spring Boot's virtual-thread mode hands every request its own new virtual
thread, so under real load that fresh-allocation path fires on *every single request, on
both the file and console appenders* (the same "both appenders now pay the allocation
cost, not just one" dynamic from the plain-default regression earlier in this file,
except here it's inherent to the VT fallback design rather than a bug to fix - reusing
the buffer across the tiny number of events one short-lived virtual thread logs was never
going to pay off, per the flag's own javadoc, but apparently *not* reusing it costs more
under this specific real workload than the earlier isolated VT test - THREAD_LOCAL_BUFFER
alone, file appender only - suggested).

**Net for this rerun**: platform-thread workloads (GELF here, the plain default and every
flag test earlier in this file) are in a good place - RainbowGum wins or ties without any
tuning. Virtual threads remain a genuine open gap the smart defaults have not closed;
unlike the platform-thread story this isn't something a JDK-version sniff or a lock
primitive choice can fix - it needs either a smarter VT-aware buffer strategy (something
between "full per-thread reuse" and "fresh allocation every time," e.g. a small bounded
pool) or a separate look at why logback in particular pulls so far ahead specifically
under virtual threads (log4j2 does not, so it isn't purely a "any framework doing real
buffering loses under VT" story).

Reproduce: `STRUCTURED_FORMAT=gelf ./run-all.sh` and `VIRTUAL_THREADS=true ./run-all.sh`
from `benchmark/webapp/`, after the `run-all.sh`/`benchmark/pom.xml` fixes above.
