# Results

Benchmark numbers from an actual run, paired with a confirmed reading of what was
running. See `FINDINGS.md` for the *why* behind these numbers, and for why this
benchmark deliberately does not use `logstash-logback-encoder`.

## Revision under test

- **RainbowGum**: `main` @ `579b71bf`.
- **JDK**: Temurin 27 (`openjdk version "27"`).
- **Micronaut**: `5.1.5` platform / `5.1.15` core (see `pom.xml`), `micronaut-runtime=netty`.
- **Logback**: `1.5.37` (resolved via the shaded jar's actual contents, not assumed from a
  BOM), Logback's own `ch.qos.logback.classic.encoder.JsonEncoder` (`logback.xml`), not
  `logstash-logback-encoder`.
- **RainbowGum encoder**: `io.jstach.rainbowgum.json.encoder.LogbackJsonEncoder`, which
  deliberately mirrors Logback's own `JsonEncoder` field-for-field (see `FINDINGS.md` for
  the one confirmed schema difference).

## Confirmed configuration

`GET /api/config-report` on `rainbowgum-benchmark-micronaut-rainbowgum`
(`ReportController`, using the real `LogReporter`) confirms the route resolves to a
single `LockThreadLocalBufferLogAppender` (`console`) with `Encoder: LogbackJsonEncoder`,
not the pattern encoder `PatternConfigurator` would otherwise install by default. See
`results/rainbowgum-config-report.txt` and `FINDINGS.md`'s system-properties bug for why
this needed a `-D` flag in `run.sh` rather than the usual classpath `logging.properties`
file.

**Threading confirmed, not assumed**: `BenchController`'s response body includes the
actual thread name that handled the request. Every request in this run, for both apps,
was handled directly on a Netty event-loop thread (`default-eventLoopGroup-2-N`), not
offloaded to a separate blocking/IO thread pool. See `FINDINGS.md` for what this means
and does not mean.

## Numbers

Driver: 50 virtual-thread workers, 10s warmup (discarded) + 30s measured, closed-loop
against `GET /api/greet/world`. Two full interleaved runs (never comparing across
sessions: see `results/results-run1.csv` for the first), both shown since they agree
closely:

### Run 1

| label | req/s | p50 ms | p90 ms | p99 ms | max ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|
| logback | 22,926.8 | 1.86 | 4.33 | 7.21 | 19.10 | 586.4 |
| rainbowgum | 30,964.9 | 1.35 | 3.27 | 5.66 | 23.20 | 600.1 |

### Run 2

| label | req/s | p50 ms | p90 ms | p99 ms | max ms | RSS avg MB |
|---|---:|---:|---:|---:|---:|---:|
| logback | 23,136.8 | 1.84 | 4.29 | 7.11 | 20.14 | 593.3 |
| rainbowgum | 30,921.0 | 1.35 | 3.27 | 5.65 | 18.71 | 585.7 |

RainbowGum leads throughput by **+33-35%** and every latency percentile, both runs
agreeing within noise. RSS is a wash: within ~15 MB either direction across both runs,
not a meaningful difference at this sample size.

## Not yet done

- GraalVM native-image comparison (this pass is HotSpot only, per the explicit scope of
  this first benchmark).
- Virtual threads / other threading configurations (this pass deliberately uses whatever
  Micronaut does out of the box, no overrides).
- A third framework (Log4j2) was deliberately left out of this first pass, unlike the
  Spring webapp benchmark's three-way comparison.
