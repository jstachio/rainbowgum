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

**Not yet done, per Adam - saving for later:** a micro-benchmark (not the full webapp) that
isolates specifically where Log4j2 is spending its time, plus JFR profiling of that
micro-benchmark, to actually confirm (rather than just correlate) that thread-local cache
cold-starts are the mechanism. This full-webapp run establishes *that* something changes
dramatically for Log4j2 under virtual threads; it doesn't yet establish *why* at the
mechanism level.

No errors in any of the three apps' stdout during this run.

Reproduce: `VIRTUAL_THREADS=true ./run-all.sh` (combine with `LOG_LEVEL`/`STRUCTURED_FORMAT`
for the fuller combinatoric matrix - not yet run).
