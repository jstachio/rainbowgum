# OTLP Export Benchmark Results

Rainbow Gum's `rainbowgum-otlp` output compared with the OpenTelemetry Logback and Log4j2
appenders, which hand events to the OpenTelemetry SDK for export. Every framework sends
to the same OpenTelemetry Collector, which accepts the records and discards them.

Measured on 2026-10-13.

## Versions

| Component | Version |
| --- | --- |
| Rainbow Gum | 1.0.0-SNAPSHOT, main at `6a8b4174` (after "Cut OTLP output allocation by about 70%") |
| Logback | 1.6.5 |
| Log4j2 | 2.26.1 (through `log4j-slf4j2-impl`) |
| OpenTelemetry appenders | 2.32.0-alpha (`opentelemetry-logback-appender-1.0`, `opentelemetry-log4j-appender-2.17`) |
| OpenTelemetry SDK and exporter | 1.66.0, from `opentelemetry-instrumentation-bom-alpha` 2.32.0-alpha |
| OpenTelemetry Collector | 0.162.0 (`otel/opentelemetry-collector`, `nop` exporter) |
| SLF4J | 2.0.20 |
| JDK | Temurin 27+35, `-Xms512m -Xmx512m`, default GC |
| Machine | 20 cores (10 Cortex-X925, 10 Cortex-A725), 128 GB, Linux 6.17; collector in Docker on host networking |

## What is measured

Each framework runs in its own JVM, one at a time. Four platform threads log 200,000 events
in total (50,000 warmup events first, measured the same way and discarded):

```java
MDC.put("requestId", "req-" + id);
MDC.put("user", "ada");
log.info("Order {} shipped to warehouse {}", i, id);
```

Every framework sends the same attributes: `thread.name`, `thread.id`, and both MDC keys.
The OpenTelemetry appenders are set to `captureExperimentalAttributes=true` and to
capture all MDC keys to match what Rainbow Gum sends by default. All three send OTLP/HTTP
protobuf with the JDK HTTP client (the OpenTelemetry exporter's OkHttp sender is
excluded). Rainbow Gum uses the async publisher, as its documentation recommends for the
OTLP output.

| Column | Meaning |
| --- | --- |
| Delivered | Records the collector accepted, from its `otelcol_receiver_accepted_log_records` metric. |
| Produce | Time the logging threads spent in the logging calls, and calls per second. |
| Deliver | Time from the first call until the collector accepted the last record, and records per second. |
| CPU | Process CPU time over the delivery, all threads included. |
| Allocated | Bytes allocated by all threads over the delivery. |

Each configuration ran 3 times, interleaved (all frameworks once, then all again). The
tables show the median run; the raw rows are at the end.

## Defaults

Each framework as configured out of the box.

| Framework | Delivered | Produce | Deliver | CPU | Allocated |
| --- | ---: | ---: | ---: | ---: | ---: |
| **Rainbow Gum** | **200,000 (100%)** | 942 ms (212k/s) | 967 ms (207k/s) | 1410 ms | 300 MB |
| Logback | 14,336 (7.2%) | 128 ms (1.57M/s) | | | |
| Log4j2 | 19,456 (9.7%) | 168 ms (1.19M/s) | | | |

The OpenTelemetry SDK's `BatchLogRecordProcessor` holds at most 2,048 records by default
and drops the rest when its queue is full. Nothing is logged when it drops records.
Rainbow Gum's async publisher has a bounded buffer too, but makes logging threads wait
when it is full, so its logging calls are only as fast as the export.

## No records dropped

The SDK's queue raised to 1,000,000 (`setMaxQueueSize`) so Logback and Log4j2 deliver
every record. Rainbow Gum is unchanged from the defaults run.

| Framework | Delivered | Produce | Deliver | CPU | Allocated |
| --- | ---: | ---: | ---: | ---: | ---: |
| **Rainbow Gum** | 200,000 | 942 ms (212k/s) | **967 ms (207k/s)** | **1410 ms** | 300 MB |
| Logback | 200,000 | 169 ms (1.18M/s) | 1041 ms (192k/s) | 2080 ms | **263 MB** |
| Log4j2 | 200,000 | 214 ms (934k/s) | 1133 ms (177k/s) | 2240 ms | 716 MB |

* **Delivery:** Log4j2 takes 17% longer than Rainbow Gum and Logback 8% longer; the export over HTTP sets the
  pace. Rainbow Gum is fastest in every run.
* **CPU:** Rainbow Gum uses 32% less than Logback and 37% less than Log4j2.
* **Allocation:** Rainbow Gum allocates about 1.5 KB per event, 14% more than Logback and
  less than half of Log4j2. Before `6a8b4174` it allocated about 5 KB per event (1002 MB
  for the same run).
* **Logging calls:** the OpenTelemetry appenders return sooner because the SDK queues
  every record in memory; with a queue big enough not to drop, that queue is bounded only
  by its setting.

## Caveats

* One machine, three runs per configuration, a 512 MB heap, and 200,000 events: a quick
  run, not a long soak. The spread between runs was within 5% for delivery and 13% for
  CPU.
* CPU includes the JIT compiler and the benchmark's own polling of the collector metrics
  (every 20 ms), the same for every framework.
* Half the cores are efficiency cores, which adds noise to single runs.
* Only OTLP/HTTP protobuf is measured: not gRPC, the OkHttp sender, gzip, or the OTLP/JSON
  encoder.

## Reproducing

From the repository root, install Rainbow Gum, then run the benchmark (it starts the
collector in Docker if nothing answers on port 8888):

```bash
./mvnw -q install -DskipTests
cd benchmark/otlp
./run.sh
JAVA_OPTS=-Dbench.otel.maxQueueSize=1000000 ./run.sh logback log4j2
```

`EVENTS`, `WARMUP`, and `THREADS` change the run size. Results are appended to
`results/results.csv`.

## Raw results

`framework,threads,expected,delivered,produce_ms,produce_ops_s,deliver_ms,deliver_ops_s,cpu_ms,alloc_mb,gc_count,gc_ms`

Defaults:

```
rainbowgum,4,200000,200000,974.0,205344,999.0,200209,1410,300.0,1,3
logback,4,200000,17920,127.1,1573347,140.4,127652,1000,217.1,1,3
log4j2,4,200000,19456,167.8,1192243,182.1,106824,1150,661.2,3,13
rainbowgum,4,200000,200000,941.5,212420,966.7,206884,1300,299.9,1,4
logback,4,200000,14336,127.5,1568400,141.9,101027,840,215.8,1,4
log4j2,4,200000,17920,168.1,1189479,182.8,98030,1370,661.1,3,13
rainbowgum,4,200000,200000,927.2,215697,953.3,209789,1410,299.9,1,4
logback,4,200000,14336,117.2,1707204,132.4,108255,860,221.0,1,3
log4j2,4,200000,19456,170.1,1175704,186.1,104532,1150,657.4,3,12
```

No records dropped (`-Dbench.otel.maxQueueSize=1000000`):

```
logback,4,200000,200000,176.8,1131233,1025.4,195055,2080,267.4,1,19
log4j2,4,200000,200000,225.8,885739,1110.8,180055,2440,711.6,3,33
logback,4,200000,200000,169.3,1181226,1054.4,189684,2200,262.6,1,18
log4j2,4,200000,200000,214.1,934077,1140.5,175355,2240,716.5,3,34
logback,4,200000,200000,168.7,1185615,1041.0,192116,1940,262.4,1,18
log4j2,4,200000,200000,167.7,1192835,1132.5,176608,2180,715.9,3,32
```
