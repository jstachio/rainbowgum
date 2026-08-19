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

## Latest run

Short validation-length runs (`WARMUP_SECONDS=3 DURATION_SECONDS=8 CONCURRENCY=8`), single
machine, single trial each - useful for sanity-checking the harness and the jansi fix above,
**not** a final/rigorous benchmark result (no repeated trials, no longer duration, no higher
concurrency). Re-run with the defaults (10s warmup / 30s duration / 50 concurrency, or
higher) for anything meant to be load-bearing.

| label      | requests | req/s     | p50 ms | p90 ms | p99 ms | max ms | mean ms | RSS min/max/avg MB |
|------------|---------:|----------:|-------:|-------:|-------:|-------:|--------:|--------------------|
| logback    | 140670   | 17,583.75 | 0.41   | 0.76   | 1.16   | 11.74  | 0.45    | 574.7 / 593.1 / 584.3 |
| log4j2     | 143224   | 17,903.00 | 0.40   | 0.75   | 1.11   | 10.40  | 0.45    | 579.4 / 597.8 / 584.8 |
| rainbowgum | 124011   | 15,501.38 | 0.47   | 0.82   | 1.23   | 10.39  | 0.52    | 561.4 / 576.9 / 574.5 |

Raw data: `results/results.csv` (gitignored - regenerated per run, not committed).
