# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running (git revision, resolved framework versions, and the concrete appender/encoder
each app actually picked at runtime - not an assumption from reading config). See
`FINDINGS.md` for the *why* behind these numbers.

## Revision under test

- **RainbowGum**: `main` @ `83b00f6` ("Make LOCK_THREAD_LOCAL_BUFFER (ReentrantLock) the
  default appender") - the first full rerun against a real merged `main`, no branch
  overlay needed.
- **Benchmark harness**: this branch (`feature/webapp-benchmark`), rebased onto the same
  `main` revision above.
- **JDK**: Temurin 26.0.2 (`openjdk version "26.0.2"`).
- **Logback/Log4j2 versions**: resolved via `mvn dependency:tree` against each app's
  actual build, i.e. whatever Spring Boot 4.1.0's BOM pins - `ch.qos.logback:logback-classic:1.5.34`
  (Central's current release is 1.6.3), `org.apache.logging.log4j:log4j-core:2.25.4`
  (Central's current release is 3.0.0-beta3, still beta). Not yet resolved: whether to
  pin a newer Logback for comparison purposes independent of Spring Boot's BOM, and
  whether it's even usable under Spring Boot 4.

## Confirmed appender selection

`GET /api/config-report` on `rainbowgum-benchmark-webapp-rainbowgum`
(`ConfigReportController`) walks `RainbowGum.of().config().serviceRegistry().find(LogAppender.class)`
and prints each one's `toString()`, plus the bound SLF4J `Logger` implementation.
Captured automatically by `run-all.sh`/`run-k8s.sh` into `results/<label>-config-report.txt`
for every `rainbowgum` run.

**default/gelf/vt/gelf-vt** (`run-all.sh`, always console + file): route resolves to a
`CompositeLogAppender` of two `LockThreadLocalBufferLogAppender`s (`file`, `console`),
`flags=[]` - confirms the plain default is genuinely `LOCK_THREAD_LOCAL_BUFFER` with no
flags set, not an opt-in. GELF scenarios: `file`'s encoder reads `GelfEncoder`, `console`
stays `FormatterEncoder` - only the file side is structured, matching
`GelfSpringRainbowGumServiceProvider` only setting `OutputType.FILE`'s encoder for
`logging.structured.format.file=gelf`.

**k8s/12factor** (`run-k8s.sh`, console only): route resolves to a single
`LockThreadLocalBufferLogAppender[name=console]` - no composite, no file appender at
all, `encoder=GelfEncoder` - confirms `logging.appenders=console` genuinely excludes the
file appender rather than just hiding it, and `logging.structured.format.console=gelf`
(the new property `GelfSpringRainbowGumServiceProvider` now also reads) took effect.
Verified no stray `benchmark.log` was written by any of the three apps during this
scenario.

In every scenario the bound SLF4J logger is `InfoLogger` (RainbowGum's dedicated
level-checked logger).

## Numbers

Driver: 50 virtual-thread workers, 10s warmup (discarded) + 30s measured, closed-loop
against `GET /api/greet/world`.

| label | req/s | p50 ms | p90 ms | p99 ms | max ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|
| logback | 22,084.4 | 2.01 | 4.47 | 7.49 | 20.53 | 626.5 |
| log4j2 | 33,892.7 | 1.32 | 2.75 | 4.79 | 20.72 | 658.8 |
| rainbowgum | 27,947.0 | 1.71 | 3.06 | 4.76 | 20.84 | 657.1 |
| logback-vt | 25,928.8 | 1.93 | 2.60 | 3.11 | 13.12 | 631.8 |
| log4j2-vt | 19,448.2 | 2.41 | 5.02 | 7.60 | 22.13 | 640.1 |
| **rainbowgum-vt** | **27,565.7** | 1.80 | 2.44 | 3.26 | 17.56 | 645.9 |
| logback-gelf | 21,682.3 | 2.10 | 3.96 | 6.59 | 19.45 | 668.2 |
| log4j2-gelf | 16,963.1 | 2.57 | 5.71 | 9.48 | 23.10 | 699.7 |
| **rainbowgum-gelf** | **23,465.6** | 1.98 | 3.83 | 6.35 | 19.32 | 664.9 |
| logback-gelf-vt | 25,567.9 | 1.96 | 2.63 | 3.54 | 12.95 | 632.0 |
| log4j2-gelf-vt | 17,949.6 | 2.58 | 4.86 | 7.09 | 20.04 | 634.4 |
| **rainbowgum-gelf-vt** | **26,180.1** | 1.89 | 2.59 | 3.51 | 13.09 | 676.0 |
| logback-k8s | 15,760.2 | 2.87 | 6.19 | 9.70 | 26.04 | 649.2 |
| log4j2-k8s | 17,870.8 | 2.43 | 5.70 | 9.42 | 24.47 | 662.0 |
| **rainbowgum-k8s** | **34,379.8** | 1.27 | 2.82 | 4.52 | 18.08 | 670.1 |
| logback-k8s-vt | 22,448.9 | 2.22 | 3.00 | 3.73 | 14.22 | 650.4 |
| log4j2-k8s-vt | 16,100.3 | 2.87 | 6.04 | 9.23 | 23.14 | 627.0 |
| **rainbowgum-k8s-vt** | **25,320.4** | 2.00 | 3.05 | 3.80 | 14.13 | 621.0 |

**RainbowGum wins 5 of 6 scenarios outright** (bolded), the exception being plain
platform-thread default where Log4j2's `synchronized`+per-thread-scratch-buffer strategy
(see `FINDINGS.md`) still leads. The `k8s` scenario (GELF-to-console-only, no file) is
RainbowGum's best result of the whole benchmark - nearly 2x both Logback and Log4j2 -
and also the scenario Logback does *worst* in relative to its own other numbers
(`logback-k8s` 15,760 is its lowest result across every scenario tested here, well below
even `logback-gelf` at 21,682, despite `k8s` having strictly less I/O - one file write
fewer per event than `gelf`). Not yet investigated why console-only specifically costs
Logback more than console+file GELF does; noted here rather than guessed at.

## Reproducing

```
cd benchmark/webapp
./run-all.sh                                              # default (platform threads, pattern encoder)
STRUCTURED_FORMAT=gelf ./run-all.sh
VIRTUAL_THREADS=true ./run-all.sh
STRUCTURED_FORMAT=gelf VIRTUAL_THREADS=true ./run-all.sh   # combined
./run-k8s.sh                                              # GELF-to-console-only, no file - both PLATFORM and VT in one run
```

Results land in `results/` (gitignored): `results.csv` (all numeric results, appended
across runs), `<label>-jfr.txt` (GC/allocation events), `<label>-stdout.log` (app
console output), `<label>-config-report.txt` (rainbowgum only - the appender/logger dump
above).
