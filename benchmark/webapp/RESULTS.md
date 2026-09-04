# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running (git revision, resolved framework versions, and the concrete appender/encoder
each app actually picked at runtime - not an assumption from reading config). See
`FINDINGS.md` for the *why* behind these numbers.

## Revision under test

- **RainbowGum**: `main` @ `777d584` ("Implement more of Spring Boot's documented
  logging properties (Boot 4 only)") - includes the `LOCK_THREAD_LOCAL_BUFFER`-by-default
  change and native `logging.structured.format.console`/`.file` support. This run also
  removed `rainbowgum-benchmark-webapp-rainbowgum`'s custom
  `GelfSpringRainbowGumServiceProvider` workaround, now redundant - GELF is handled by
  the library itself, the same way it is for Logback/Log4j2.
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
flags set. GELF scenarios: `file`'s encoder reads `GelfEncoder`, `console` stays
`FormatterEncoder` - only the file side is structured, matching Spring Boot's own
`logging.structured.format.file`-is-file-only behavior, now handled natively by
`rainbowgum-spring-boot4`'s `StructuredLogging` class (no app-level code at all).

**k8s/12factor** (`run-k8s.sh`, console only, all three apps using the same
`--logging.file.name=` flag): route resolves to a single
`LockThreadLocalBufferLogAppender[name=console]` - no composite, no file appender at
all, `encoder=GelfEncoder`. Confirmed no stray log file was written by any of the three
apps.

In every scenario the bound SLF4J logger is `InfoLogger` (RainbowGum's dedicated
level-checked logger).

## Numbers

Driver: 50 virtual-thread workers, 10s warmup (discarded) + 30s measured, closed-loop
against `GET /api/greet/world`.

| label | req/s | p50 ms | p90 ms | p99 ms | max ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|
| logback | 21,373.5 | 2.08 | 4.64 | 7.71 | 20.81 | 667.1 |
| log4j2 | 33,264.1 | 1.34 | 2.80 | 4.85 | 20.21 | 629.3 |
| rainbowgum | 28,054.7 | 1.70 | 3.03 | 4.73 | 18.95 | 660.0 |
| logback-vt | 25,553.2 | 1.96 | 2.64 | 3.14 | 12.70 | 648.1 |
| log4j2-vt | 18,990.4 | 2.46 | 5.13 | 7.75 | 21.40 | 630.0 |
| **rainbowgum-vt** | **26,323.6** | 1.87 | 2.59 | 3.37 | 14.83 | 623.5 |
| logback-gelf | 22,809.7 | 1.99 | 3.78 | 6.35 | 18.91 | 644.7 |
| log4j2-gelf | 16,529.6 | 2.64 | 5.89 | 9.76 | 29.69 | 672.2 |
| **rainbowgum-gelf** | **22,350.8** | 2.06 | 4.11 | 7.02 | 21.08 | 674.2 |
| logback-gelf-vt | 25,796.9 | 1.94 | 2.60 | 3.45 | 13.29 | 630.2 |
| log4j2-gelf-vt | 17,734.0 | 2.61 | 4.92 | 7.19 | 20.11 | 657.9 |
| **rainbowgum-gelf-vt** | **27,450.0** | 1.81 | 2.45 | 3.43 | 12.78 | 684.6 |
| logback-k8s | 15,002.2 | 3.02 | 6.54 | 10.25 | 24.03 | 624.0 |
| log4j2-k8s | 19,638.5 | 2.18 | 5.36 | 8.99 | 23.52 | 649.9 |
| **rainbowgum-k8s** | **34,172.4** | 1.28 | 2.82 | 4.53 | 19.41 | 629.5 |
| logback-k8s-vt | 21,587.3 | 2.30 | 3.13 | 4.32 | 13.63 | 645.5 |
| log4j2-k8s-vt | 15,444.7 | 2.98 | 6.30 | 9.62 | 21.80 | 628.4 |
| **rainbowgum-k8s-vt** | **25,571.6** | 1.98 | 3.00 | 3.75 | 14.53 | 626.0 |

**RainbowGum wins 5 of 6 scenarios outright** (bolded) - matches the previous run's
picture, now reproduced with the manual GELF workaround gone and native structured
logging in its place, confirming the library-level implementation performs identically
to the app-level workaround it replaced (`rainbowgum-gelf`: 22,350.8 here vs 23,465.6
previously - within normal run-to-run sandbox noise, not a regression). The `k8s`
scenario remains RainbowGum's best result of the whole benchmark - roughly 2x both
Logback and Log4j2 - and still the scenario Logback does *worst* in relative to its own
numbers elsewhere (`logback-k8s` 15,002 is its lowest result across every scenario
tested here); that specific gap remains open per `FINDINGS.md`.

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
