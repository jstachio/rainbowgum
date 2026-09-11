# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running (git revision, resolved framework versions, and the concrete appender/encoder
each app actually picked at runtime - not an assumption from reading config). See
`FINDINGS.md` for the *why* behind these numbers.

## Revision under test

- **RainbowGum**: `main` @ `c9855f45` ("Bump logback 1.6.3 (defunct benchmark)") -
  substantially later than the previous run (`777d584`): includes the SLF4J
  `EventCreator`/`LogEventFactory` unification (with per-record cached
  `System.Logger.Level` dispatch in the generated `LevelLogger`s),
  `LocationAwareLogger`/`LoggingEventAware` caller-info support, the JUL module split,
  and various pattern-keyword additions - none of which touch the hot appender/encoder
  path exercised here, so this run is primarily a regression check that nothing along
  the way changed the numbers, not a targeted investigation of new code.
- **Benchmark harness**: this branch (`webapp-0-10-0`), rebased onto the `main` revision
  above (one real conflict, in `benchmark/pom.xml`'s module list - resolved keeping this
  branch's own `legacy`/`webapp` split, unrelated to the rebase itself).
- **JDK**: Temurin 26.0.2 (`openjdk version "26.0.2"`).
- **Logback/Log4j2 versions**: resolved via `mvn dependency:tree` against each app's
  actual build, i.e. whatever Spring Boot 4.1.0's BOM pins - `ch.qos.logback:logback-classic:1.5.34`
  (Central's current release is 1.6.3), `org.apache.logging.log4j:log4j-core:2.25.4`
  (Central's current release is 3.0.0-beta3, still beta) - unchanged from the previous
  run, Spring Boot's BOM still pins the same versions. Not yet resolved: whether to pin a
  newer Logback for comparison purposes independent of Spring Boot's BOM, and whether
  it's even usable under Spring Boot 4.

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
| logback | 20,955.7 | 2.09 | 4.81 | 8.13 | 21.76 | 685.2 |
| log4j2 | 33,271.8 | 1.33 | 2.83 | 5.09 | 20.06 | 687.7 |
| rainbowgum | 28,208.6 | 1.68 | 3.01 | 5.01 | 20.58 | 638.6 |
| logback-vt | 25,761.3 | 1.95 | 2.62 | 3.11 | 13.28 | 640.8 |
| log4j2-vt | 18,778.6 | 2.48 | 5.17 | 7.87 | 22.07 | 644.5 |
| **rainbowgum-vt** | **26,005.6** | 1.89 | 2.61 | 3.55 | 13.45 | 633.8 |
| logback-gelf | 21,301.1 | 2.10 | 4.19 | 7.07 | 21.66 | 655.9 |
| log4j2-gelf | 16,445.9 | 2.63 | 6.01 | 10.06 | 26.06 | 640.8 |
| **rainbowgum-gelf** | **21,434.8** | 2.10 | 4.40 | 7.84 | 19.96 | 645.6 |
| logback-gelf-vt | 24,624.4 | 2.02 | 2.75 | 3.76 | 13.84 | 664.2 |
| log4j2-gelf-vt | 17,823.1 | 2.60 | 4.89 | 7.16 | 21.99 | 655.9 |
| **rainbowgum-gelf-vt** | **26,599.7** | 1.83 | 2.54 | 4.42 | 13.92 | 621.0 |
| logback-k8s | 16,493.1 | 2.69 | 6.27 | 10.19 | 23.01 | 647.0 |
| log4j2-k8s | 19,022.3 | 2.25 | 5.58 | 9.48 | 24.70 | 627.1 |
| **rainbowgum-k8s** | **34,340.6** | 1.27 | 2.79 | 4.41 | 17.87 | 622.6 |
| logback-k8s-vt | 22,370.6 | 2.23 | 3.01 | 3.85 | 14.80 | 623.2 |
| log4j2-k8s-vt | 15,843.6 | 2.91 | 6.14 | 9.40 | 23.15 | 617.8 |
| **rainbowgum-k8s-vt** | **25,569.5** | 1.98 | 3.00 | 3.74 | 13.62 | 685.8 |

**RainbowGum wins 5 of 6 scenarios outright** (bolded) - unchanged from every previous
run despite ~130 commits of unrelated `main` history landing in between (SLF4J facade
unification, JUL module split, pattern-keyword additions - see "Revision under test"
above). Every number moved by only a few percent from the last run, well within normal
run-to-run sandbox noise, confirming no regression crept in along the way
(`rainbowgum-gelf`: 21,434.8 here vs 22,350.8 previously; `rainbowgum-k8s`: 34,340.6 vs
34,172.4). The `k8s` scenario remains RainbowGum's best result of the whole benchmark -
roughly 1.8-2x both Logback and Log4j2 - and still the scenario Logback does *worst* in
relative to its own numbers elsewhere (`logback-k8s` 16,493 is its lowest result across
every scenario tested here); that specific gap remains open per `FINDINGS.md`.

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

**Between scenarios, run `./mvnw clean` (from the repo root) first.** Each script's own
"Building..." step is a scoped `-am install` (not `clean install`) - fine as the very
first build, but `rainbowgum-apt`'s moditect step fails with "File ... is already
modular" on any later non-clean build in a separate Maven invocation (the jar it left
behind already has `module-info.class` injected; a rebuild's `jar:jar` reuses it as-is,
and moditect refuses to inject into an already-modular jar). A `clean` between scripts
avoids it; it only affects consecutive scripts sharing this checkout, not a single fresh
run.
