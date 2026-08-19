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
