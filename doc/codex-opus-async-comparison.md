# Codex and Opus async publisher comparison

This compares the unchanged `CodexAsyncPublisher` from commit `a1a55a97` with
`OpusAsyncPublisher` from commit `35ddc625940af95fb0e4256877cdd9761a8bcf23`.
The latter includes the queued metric, so both implementations pay for that
metric in the comparison. Neither publisher was modified for this work.

The Opus source is loaded from that exact Git revision and compiled alongside
the Codex branch's core. It is not copied into core or selected as the default.
Its existing JMH results are not compared numerically with the earlier Codex
results: they use different workloads, appender settings, and measurement methods.
The original Opus results document was also committed before the queued gauge was
added. The measurements here include that later change.

## Design and behavior

Both implementations use a bounded buffer, a nonfair `ReentrantLock`, conditions,
and a single daemon output worker. Both append batches outside the queue lock,
preserve insertion order, clear consumed event references, and avoid interrupting
the worker during normal close. Neither is a lock free design.

| Area | Codex | Opus |
| --- | --- | --- |
| Batch transfer | Swaps two array references under the lock; clears the consumed batch outside it. | Copies each event from a ring into a batch array and clears ring slots while holding the lock. |
| Producer wakeups | Wakes one producer; producers pass the wakeup along while space remains. | Wakes all waiting producers after removing a batch. |
| Event storage | Two arrays of capacity N, plus events held by blocked callers. | Ring and batch arrays of capacity N, plus events held by blocked callers. |
| Queued gauge | Retained at construction; updated under the queue lock. | Retained at start; includes any events accepted before start. Updated under the queue lock. |
| Before start | Rejects calls with `IllegalStateException`. | Buffers up to capacity, then drops and counts overflow. |
| Shutdown admission | Preserves calls admitted under the lock before close, even if they are waiting for space. | Preserves events already enqueued; wakes waiting producers and drops their events. |
| Calls after close | Throws `IllegalStateException`. | Counts drops and reports one alert for that reason. |
| Interrupted producers | Interruptible lock acquisition can reject an event even when space is available; preserves the flag and alerts. | Acquires the lock without interruption; interruption causes a drop only when waiting for space. Preserves the flag. |
| Drop reporting | Interrupted submissions and pending events discarded after worker failure are not counted in `events.dropped`. Alerts are not deduplicated. | Counts normal rejection reasons in `events.dropped` and limits alerts to once per reason per publisher. |
| Worker logging recursively | Always rejects it. | Accepts it when there is room; drops it when full. |
| Uncaught appender `Error` | Alerts, terminates the worker, releases blocked producers, and closes the appender. | Catches `Throwable` around each batch, alerts, and continues consuming. |
| Repeated start | Does nothing while already running. | Throws on any second start. |
| Repeated close after timeout | Waits again for termination, up to the timeout. | Returns immediately once state is closing or terminated. |
| Named module access | Builder is in the exported core package. | Class is in the unexported `io.jstach.rainbowgum.publisher` package; ordinary module consumers cannot import it without an export override. Classpath use works. |

The two designs have approximately the same bounded array storage. Swapping arrays
reduces Codex's work while holding the queue lock, but the benchmarks do not isolate
that change from wakeup policy, producer bookkeeping, or interrupt handling. A
measured difference cannot be attributed solely to array copying.

The shutdown policies use different definitions of accepted work. Opus releases
waiting callers promptly when shutdown starts, with observable drops. Codex keeps
their events eligible for delivery, but those callers can remain blocked while
the output is stuck, even after the closer times out. This is a policy choice,
not evidence by itself that one implementation is correct and the other is not.

Ordinary appender exceptions are already handled by Rainbow Gum's appenders. The
worker failure distinction above concerns failures escaping that handling, such
as `AssertionError`. Continuing can preserve later events; stopping avoids
continuing after a potentially unrecoverable failure. Neither publisher's own
handling of an escaping `Error` provides complete failed-event accounting.

## Behavior checks and review findings

The comparison runner executes a small probe through the same public factories,
using real appenders and observable list outputs. It records the following:

| Probe | Codex | Opus |
| --- | --- | --- |
| Output `start()` throws, then publisher `close()` is called | Output closed once. | Output never closed. |
| Producer enters with its interrupt flag set, with free queue space | No event delivered; flag preserved; one alert; dropped metric remains zero. | Event delivered; flag preserved; no alert or drop. |
| Close while one event is being written, one is queued, and a producer waits with a third | All three delivered; no drop. | First two delivered; waiting event counted as one drop. |

The startup cleanup observation is a concrete Opus defect: `start()` sets state
to terminated on appender startup failure but does not close the appender, and
the subsequent `close()` returns immediately. Outputs can allocate resources
before failing startup. Code review also shows `Thread.start()` is outside its
failure cleanup block; a failure to create the native thread leaves a running
state with no consumer. That second case was not fault-injected in this probe.

Codex's uncounted interrupted submission is also a concrete gap. In particular,
an already-interrupted caller loses a log even when there is free space. Opus's
behavior in that situation is more useful for logging during cancellation.
Codex should also account for pending events discarded after fatal worker failure.

Opus accepts recursive worker logging if there is room. That prevents a full-queue
deadlock, but does not prevent an output that logs on every write from generating
an endless sequence of events while the publisher remains running. Codex rejects
all such reentry, at the cost of throwing into the output's call path. This is a
source-level observation, not a sustained recursion benchmark.

The probe is
[`AsyncPublisherBehaviorProbe`](../benchmark/rainbowgum-benchmark-rainbowgum/src/main/java/io/jstach/rainbowgum/rainbowgum/AsyncPublisherBehaviorProbe.java).
The reviewed Opus code is pinned
[here](https://github.com/jstachio/rainbowgum/blob/35ddc625940af95fb0e4256877cdd9761a8bcf23/core/src/main/java/io/jstach/rainbowgum/publisher/OpusAsyncPublisher.java).

## Common benchmark

```sh
git fetch origin
benchmark/rainbowgum-benchmark-rainbowgum/run-async.sh \
    target/codex-opus-comparison 1000000 3 7 3 1024 \
    35ddc625940af95fb0e4256877cdd9761a8bcf23
```

The optional seventh argument selects the Opus source revision. Without it, the
runner retains the original blocking queue versus Codex comparison. The source
and compiled Opus classes stay in the result directory. Reflection selects its
public factory before timing starts; no reflective calls occur in the logging
path. All three publishers use their ten second default close timeout.

The same process measures all three implementations, rotating their order per
iteration and fork. Each condition gets three warmup runs and seven measured
runs per fork, across three fresh JVMs. Each run delivers one million events,
with capacity 1024. There are 21 measured runs per condition and 756 in total.

The workload covers one, four, and sixteen platform or virtual producers, with
message only and TTLL formatting. All use the same `REUSE_BUFFER` appender, UTF-8
encoder, counting output, and enabled queued metric. Timing includes complete
draining and output closure. Every run validates delivery count, encoded byte
count, output closure, zero queued events, and absence of alerts.
All producers finish before timed shutdown begins, so Opus's shutdown drop policy
cannot improve its score by skipping blocked producers' events.

These are direct publisher calls with prebuilt immutable events and a fixed
timestamp. They exclude logger lookup, event allocation, caller lookup, and clock
reads. The output counts bytes instead of performing storage I/O. The machine is
a shared Linux aarch64 host with twenty logical CPUs across Cortex-X925 and
Cortex-A725 cores, running Temurin 27+35 with `-Xms512m -Xmx512m`. CPU affinity is
not assigned. These results do not establish application or disk throughput.
Each fork runs all implementations through a common call site, so JIT type
profiles can include more than one implementation. These workload results are
not isolated single-implementation JMH measurements.

## Results

Measured on 2026-10-03. Throughput is the median of 21 run rates per condition,
in millions of delivered events per second. Ratios above 1 favor Codex. No
statistical confidence bounds are claimed.

| Threads | Format | Producers | Blocking (M/s) | Codex (M/s) | Opus (M/s) | Codex / Opus |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| platform | message | 1 | 6.34 | 17.12 | 9.48 | 1.81 |
| platform | message | 4 | 5.07 | 6.01 | 3.52 | 1.71 |
| platform | message | 16 | 0.57 | 0.74 | 0.65 | 1.13 |
| platform | ttll | 1 | 12.32 | 12.65 | 11.79 | 1.07 |
| platform | ttll | 4 | 4.42 | 6.40 | 4.80 | 1.33 |
| platform | ttll | 16 | 1.13 | 1.39 | 1.01 | 1.38 |
| virtual | message | 1 | 6.51 | 17.72 | 9.45 | 1.88 |
| virtual | message | 4 | 8.00 | 11.60 | 9.69 | 1.20 |
| virtual | message | 16 | 7.44 | 9.27 | 7.74 | 1.20 |
| virtual | ttll | 1 | 12.83 | 13.06 | 11.81 | 1.11 |
| virtual | ttll | 4 | 7.38 | 10.15 | 8.89 | 1.14 |
| virtual | ttll | 16 | 7.22 | 8.48 | 8.16 | 1.04 |

Codex's aggregate median is higher in all twelve conditions. The strongest
repeatable difference is message only formatting with one producer: 1.81 times
Opus for platform threads and 1.88 times for virtual threads. With four or sixteen
virtual producers and message only formatting, the advantage is about 20%.
With sixteen virtual producers and TTLL, the aggregate difference is only 4%;
that is too small to treat as a decisive production advantage from this experiment.

The per fork medians make the uncertainty visible. For four platform producers
with message only formatting, Codex's fork medians range from 3.83 to 11.43 million
events/s. For sixteen platform producers with message only formatting, Opus is
slightly faster in the second fork despite Codex's higher aggregate median.

| Threads | Format | Producers | Codex fork medians (M/s) | Opus fork medians (M/s) |
| --- | --- | ---: | --- | --- |
| platform | message | 1 | 16.59, 18.37, 20.68 | 8.37, 9.65, 9.48 |
| platform | message | 4 | 11.43, 6.01, 3.83 | 4.03, 3.52, 3.08 |
| platform | message | 16 | 0.74, 0.70, 0.77 | 0.65, 0.72, 0.61 |
| platform | ttll | 1 | 11.88, 12.51, 14.47 | 11.79, 10.80, 14.34 |
| platform | ttll | 4 | 5.51, 6.80, 6.40 | 4.80, 4.11, 5.42 |
| platform | ttll | 16 | 1.85, 1.39, 1.16 | 0.68, 1.07, 0.69 |
| virtual | message | 1 | 20.03, 14.33, 17.87 | 8.57, 8.68, 10.37 |
| virtual | message | 4 | 11.56, 11.64, 11.60 | 9.71, 9.39, 9.69 |
| virtual | message | 16 | 9.27, 8.93, 9.31 | 7.93, 7.47, 7.58 |
| virtual | ttll | 1 | 12.85, 12.95, 14.88 | 11.09, 11.59, 13.86 |
| virtual | ttll | 4 | 9.97, 10.46, 9.94 | 8.89, 9.25, 8.78 |
| virtual | ttll | 16 | 8.74, 8.34, 8.48 | 8.34, 7.93, 7.72 |

The following table gives medians of the 21 runs' sampled producer call p99s.
Sampling selects one random position per complete block of 1024 calls, roughly
one thousand samples per run. This measures time inside `log(...)`, including
waiting for queue space. It does not measure enqueue-to-output latency. Rare
long stalls can lie beyond this percentile, even when they materially affect
overall throughput; this table does not bound worst case latency.

| Threads | Format | Producers | Codex p99 (µs) | Opus p99 (µs) |
| --- | --- | ---: | ---: | ---: |
| platform | message | 1 | 0.59 | 4.61 |
| platform | message | 4 | 18.05 | 23.94 |
| platform | message | 16 | 0.40 | 0.50 |
| platform | ttll | 1 | 0.10 | 0.11 |
| platform | ttll | 4 | 21.18 | 25.92 |
| platform | ttll | 16 | 0.74 | 1.04 |
| virtual | message | 1 | 0.90 | 3.34 |
| virtual | message | 4 | 22.19 | 29.50 |
| virtual | message | 16 | 85.76 | 119.26 |
| virtual | ttll | 1 | 0.10 | 0.19 |
| virtual | ttll | 4 | 18.93 | 23.97 |
| virtual | ttll | 16 | 78.05 | 92.91 |

Codex's sampled p99 is lower than Opus's in each aggregate condition here. That
does not contradict the earlier finding that Codex can have higher contended
call latency than the blocking queue publisher: those are different comparisons.

[All 756 measured runs](../benchmark/rainbowgum-benchmark-rainbowgum/codex-opus-results.csv)
include the fresh blocking queue control. The
[behavior probe output](../benchmark/rainbowgum-benchmark-rainbowgum/codex-opus-behavior.txt)
records the lifecycle observations. Warmups are excluded from the CSV. The full
Maven verification passed after adding the comparison harness. Both publisher
implementations remain unchanged.

## Recommendation

Keep the Codex array-swap approach as the next candidate to develop, based on
these throughput results and its smaller critical section. Carry over Opus's
counted-drop reporting and alerts limited to once per rejection reason, and
reconsider rejecting already-interrupted callers when no waiting is necessary.
Fix startup cleanup before adopting Opus's lifecycle implementation.

Explicitly choose the shutdown admission and worker failure policies before
combining code: preserving already-waiting producers versus releasing them with
a counted drop, and stopping after an escaping `Error` versus continuing. Neither
is just a performance optimization. Validate the chosen design with representative
outputs and application workloads before replacing the default publisher.


Agent: Codex (GPT-6).
