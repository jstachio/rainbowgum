# Parallel batch encoding: not worth it

Question: an asynchronous publisher hands an appender a batch of events, and today the
appender encodes and writes them one at a time on that single thread. Would encoding the
batch in parallel (a work stealing `ForkJoinPool`) and then handing the output the whole
pre-encoded batch be faster?

Answer: no. On a 20 core machine it is 2 to 4 times **slower** in every configuration
measured, even at 1024 event batches.

## Setup

- Experimental API on this branch: `LogOutput.write(LogEvent[] events, Buffer[] buffers,
  int count)` (writes a batch whose events are already encoded) and an appender flag,
  `AppenderFlag.PARALLEL_ENCODE`, that encodes batches on the common `ForkJoinPool` into one
  buffer per event and then calls that method. `ParallelEncodeTest` (in
  `test/rainbowgum-test-core`) checks the output is identical to sequential encoding.
- Real appender (`REUSE_BUFFER`), real `FileOutput` with its default 8KB
  `BufferedOutputStream`, one flush per batch, writing to `/dev/null` so the buffered write
  path and its write syscalls are measured but disk speed is not.
- Events: a formatted message and three key values. Encoders: a typical text pattern
  (timestamp, thread, level, logger, message, key values) and ECS JSON.
- JMH 1.37, 2 forks, 3 x 2s warmup, 5 x 2s measurement, single benchmark thread (as the
  publisher's worker), Temurin 27, aarch64 with 20 cores (10 Cortex-X925 and 10
  Cortex-A725). All numbers are from one session.

## Results

`ParallelEncodeBenchmark` (one operation is one batch, converted to events per second):

| Encoder | Batch | Sequential | `PARALLEL_ENCODE` |
|---|---|---|---|
| pattern | 64 | 7.6M events/s | 2.5M events/s |
| pattern | 1024 | 7.6M events/s | 2.0M events/s |
| json | 64 | 3.5M events/s | 1.8M events/s |
| json | 1024 | 3.3M events/s | 1.5M events/s |

`EncodeBreakdownBenchmark` (1024 event batch, time per batch, no appender):

| | pattern | json |
|---|---|---|
| Encode only, sequential | 127 us | 279 us |
| Encode only, parallel (4 chunks of 256) | 194 us | 351 us |
| Encode and write, sequential (first table) | 134 us | 308 us |
| Encode in parallel (4 chunks) then write | 403 us | 607 us |

## Why

1. **Encoding is over 90% of the sequential cost** (the write phase is about 8 us of 134 for
   pattern), so in principle there was a lot to gain.
2. **Parallel encoding alone is slower than sequential**: 4 threads take 194 us for what
   one thread does in 127. Each batch is only a few hundred microseconds of work, so waking
   parked pool workers and scheduling them every batch costs more than it saves. The large
   error margins of the parallel runs fit that.
3. **Writing data encoded on other cores is far slower**: about 210 us per batch after
   parallel encoding versus about 8 us when each event was just encoded into the same hot
   buffer, because every encoded event has to be pulled across cores into the writer's
   cache.
4. The write itself is inherently serial (one output), so it cannot be spread out.

Caveat: half of this machine's cores are slower Cortex-A725 efficiency cores, so some
parallel chunks may run there. A homogeneous machine could narrow the gap somewhat, but the
wake-up cost and the cross-core transfer in the write phase remain.

Parallel encoding could only pay off for encoders far more expensive per event than these
(roughly 120 to 280 ns), and typical log events are not.

## Related: standard out flushes every write

`System.out` is `new PrintStream(new BufferedOutputStream(fd, 128), autoFlush = true)`
(checked in Temurin 27's `java.lang.System`), and an autoflushing `PrintStream` flushes on
every `write(byte[])`. The console output writes through it, so it makes a write syscall
per event even when an asynchronous publisher hands it batches.

## Recommendation

Do not merge `PARALLEL_ENCODE`. The pre-encoded batch method on `LogOutput` is only useful
if something produces pre-encoded batches, so it does not justify itself on its own either.

## Running

```
./mvnw -pl benchmark/rainbowgum-benchmark-jmh-encode -am install -DskipTests
cd benchmark/rainbowgum-benchmark-jmh-encode
../../mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) org.openjdk.jmh.Main
```
