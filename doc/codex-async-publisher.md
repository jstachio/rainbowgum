# CodexAsyncPublisher experiment

This document records the original experiment and its measurements. The successor,
`BatchSwapAsyncLogPublisher`, now provides the default `async` publisher, with counted
drops and delivery for interrupted callers when space is available. The historical
results and behavior descriptions below refer to the original revisions. The current
benchmark runner labels the successor `BATCH_SWAP` and selects the old blocking queue
baseline explicitly. Check out the documented historical revision to reproduce the
original comparison.

For the subsequent comparison with Opus, including a common benchmark and
lifecycle findings, see [Codex and Opus comparison](codex-opus-async-comparison.md).

`CodexAsyncPublisher` is an experimental publisher selected through its builder.
It does not replace the default async publisher or register a new URI scheme.
This implementation was written from the publisher, appender, event, lifecycle,
and metrics contracts without opening the blocking queue publisher implementation
or its tests during this task. The comparison instantiates the existing publisher
through `LogPublisher.PublisherFactory.ofAsync(...)` and checks its runtime class.

## Design

* Producers append to a fixed array under one `ReentrantLock`. They wait on a
  condition when that array is full.
* One worker exchanges the pending array with an empty array under the same lock,
  then appends the entire batch outside the lock. Exchanging arrays avoids copying
  individual event references while producers wait for the lock.
* The worker wakes one waiting producer. Producers pass the wakeup along while
  space remains, including when a waiting call is interrupted. This avoids waking
  every producer at once to compete for the same available space.
* The worker clears consumed references before reusing the array. Two arrays each
  hold up to `bufferSize` references: one pending batch and one being written.
  Blocked callers also retain their own events until their calls complete.
* A retained `events.queued` gauge counts the pending batch. It is updated under
  the queue lock and excludes events already handed to the appender.

Insertion order is preserved, including each producer's event order. Admission
and shutdown share the queue lock. Calls already waiting for space when shutdown
begins may finish; later calls are rejected. This also lets the worker distinguish
an empty queue from completed shutdown when producers are still waiting.

The appender starts before events are accepted. Only the worker writes to and
closes a started appender. Close waits up to its configured timeout and reports
an alert if delivery has not finished. It never interrupts the worker's output
operation: the worker continues draining and closes the appender afterward.
An interrupted producer reports an alert and restores its interrupt status.
Recursive logging from the worker is rejected to prevent a full queue from
deadlocking its own consumer.

This is a blocking design, with no busy spinning, external queue dependency,
unbounded event storage, or promise of fairness between competing producers.

## Usage

```java
var publisher = CodexAsyncPublisher.builder()
    .bufferSize(1024)
    .shutdownTimeout(Duration.ofSeconds(10))
    .build();

var gum = RainbowGum.builder()
    .route(r -> r.publisher(publisher)
        .appender("console", a -> a.output(LogOutput.ofStandardOut())))
    .build()
    .start();
```

Builder options are programmatic. The defaults are 1024 pending events and a
ten second shutdown wait. The regular router freezes events before passing them
to the publisher. Direct callers of `publisher.log(...)` must provide frozen
events themselves.

## Validation

The tests exercise real appenders and outputs, with platform and virtual producers
and capacities of 1, 17, and 1024. They check complete delivery, per producer order,
queue metrics, appender startup and closure, startup failure, recursive logging,
and a fatal output failure with a blocked producer.

Shutdown cases cover normal draining, timeout, an interrupted producer, an
interrupted closer, and a closer that enters with its interrupt flag already set.
These cases fill the queue and park sixteen admitted producers before closing.

The full `./mvnw --fail-at-end verify` build and the core Checker Framework,
Error Prone, and NullAway checks pass on JDK 27. The new publisher tests also pass
on Temurin 21.0.12 with the build cache disabled to ensure execution on that JVM.

## Benchmark method

Run from the repository root:

```sh
benchmark/rainbowgum-benchmark-rainbowgum/run-async.sh \
    target/codex-async-results 1000000 3 7 3 1024
```

Arguments are the result directory, events per run, warmup runs, measured runs,
fresh JVM forks, and queue capacity. Each fork alternates which implementation
runs first. The matrix covers one, four, and sixteen platform or virtual
producers, using either message only formatting or the standard TTLL formatter.

Both publishers use the same `REUSE_BUFFER` appender, UTF-8 encoder, counting
output, capacity, and enabled queue metric. The workload uses prebuilt immutable
events, so it measures publishing and delivery rather than logger lookup, event
creation, caller lookup, or clock reads. The TTLL timestamp is fixed.

Timing begins when producers are released and ends after the publisher drains
and closes the output. Every run checks the delivered event count, encoded byte
count, output closure, zero remaining queued events, and absence of alerts.
Producer call latency is sampled once at a randomly chosen position in each
complete block of 1024 calls. Those timings include waiting for queue space.

This is a standalone workload benchmark, not JMH. The output counts bytes instead
of writing to disk or a terminal. Results therefore do not establish application
logging throughput or storage performance. The host is shared, uses heterogeneous
ARM cores, and has no CPU affinity assigned to the benchmark.

## Results

Measured on 2026-10-03 with Temurin 27+35, Linux 6.17 on aarch64, twenty
logical CPUs (Cortex-X925 and Cortex-A725), and `-Xms512m -Xmx512m`. The baseline
is main commit `1939087a`. Queue capacity is 1024. Each condition has three warmup
runs and seven measured runs per JVM, across three JVM forks: 21 measured runs
per condition, with one million events in each run.

The throughput table reports the median of those 21 individual run rates.
A ratio above 1 favors Codex. These are measured ratios, not confidence bounds.

| Producers | Format | Threads | Blocking queue (M events/s) | Codex (M events/s) | Codex / blocking |
| ---: | --- | --- | ---: | ---: | ---: |
| 1 | message | platform | 6.23 | 18.02 | 2.89 |
| 4 | message | platform | 3.96 | 4.38 | 1.11 |
| 16 | message | platform | 0.61 | 0.72 | 1.17 |
| 1 | ttll | platform | 11.88 | 12.59 | 1.06 |
| 4 | ttll | platform | 5.01 | 5.41 | 1.08 |
| 16 | ttll | platform | 1.44 | 1.39 | 0.97 |
| 1 | message | virtual | 6.87 | 14.07 | 2.05 |
| 4 | message | virtual | 7.99 | 11.38 | 1.42 |
| 16 | message | virtual | 7.35 | 9.11 | 1.24 |
| 1 | ttll | virtual | 11.60 | 12.40 | 1.07 |
| 4 | ttll | virtual | 6.94 | 9.26 | 1.33 |
| 16 | ttll | virtual | 7.00 | 8.75 | 1.25 |

The clearest gains are message only formatting with one producer and the
contended virtual thread cases. Platform thread results vary substantially:
for four producers with message only formatting, the baseline's per fork medians
were 2.53, 3.94, and 6.02 million events/s, while Codex's were 4.38, 3.90, and
4.71. The aggregate 1.11 ratio should not be read as a reliable platform thread
advantage. Sixteen platform producers with TTLL are slightly slower in the
aggregate, with substantial variation in both implementations.

The following numbers are medians of each run's sampled producer call p99.
They describe the time spent in `log(...)`, including any queue wait, rather than
the time from enqueue to output delivery. There are roughly one thousand samples
per run. Rare long stalls can fall outside this percentile; these numbers are
not worst case latency bounds.

| Producers | Format | Threads | Blocking p99 (µs) | Codex p99 (µs) |
| ---: | --- | --- | ---: | ---: |
| 1 | message | platform | 3.23 | 0.91 |
| 4 | message | platform | 18.11 | 20.88 |
| 16 | message | platform | 0.43 | 0.46 |
| 1 | ttll | platform | 0.14 | 0.11 |
| 4 | ttll | platform | 11.33 | 30.67 |
| 16 | ttll | platform | 0.50 | 0.82 |
| 1 | message | virtual | 2.06 | 1.18 |
| 4 | message | virtual | 18.11 | 24.48 |
| 16 | message | virtual | 56.50 | 75.95 |
| 1 | ttll | virtual | 0.21 | 0.10 |
| 4 | ttll | virtual | 15.57 | 19.12 |
| 16 | ttll | virtual | 56.16 | 96.85 |

Codex generally exchanges higher throughput for worse contended producer call
latency. For example, sixteen virtual producers with TTLL improve throughput
from 7.00 to 8.75 million events/s while sampled call p99 increases from 56.16 to
96.85 microseconds. This experiment does not justify replacing the default
publisher without testing representative application workloads and real outputs.

[All 504 measured runs](../benchmark/rainbowgum-benchmark-rainbowgum/codex-async-results.csv)
include the fork, workload, elapsed time, throughput, sampled latency, byte count,
and flush count. Warmups are excluded. The
[runner](../benchmark/rainbowgum-benchmark-rainbowgum/run-async.sh) produces the
per fork CSV and JVM metadata files needed to repeat the comparison.


Agent: Codex (GPT-6).
