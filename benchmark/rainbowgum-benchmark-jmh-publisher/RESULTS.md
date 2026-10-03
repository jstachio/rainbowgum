# Async publisher benchmark: OpusAsyncPublisher vs BlockingQueueAsyncLogPublisher

`AsyncPublisherBenchmark` logs one event per operation through a real `RainbowGum` route
whose only appender writes to an output that discards the bytes, so the publisher's hand
off and contention dominate. Both publishers use a 1024 event buffer. The blocking queue
publisher is the registry's default `async` scheme.

JMH 1.37, 2 forks, 3 x 2s warmup, 5 x 2s measurement, Temurin 27, aarch64 with 20 cores
(10 Cortex-X925 and 10 Cortex-A725), one session.

| Logging threads | BlockingQueueAsyncLogPublisher | OpusAsyncPublisher |
|---|---|---|
| 1 | 7.8M ± 5.9M events/s | 8.5M ± 4.8M events/s |
| 4 | 3.3M ± 1.1M events/s | 3.4M ± 1.2M events/s |
| 16 | 0.39M ± 0.08M events/s | 0.43M ± 0.07M events/s |

## Reading it

- The two are equivalent within the error margins; Opus has the higher mean in all three.
  Its design goals were shutdown and failure correctness, not speed, and it does not cost
  any throughput.
- Both lose about 95% of their throughput going from 1 to 16 logging threads, because
  every logging thread takes the same lock to enqueue. If many threads logging at once
  matters, a lock free multiple producer queue is the next step for either design.
- The single thread error margins are large because producers outrun the consumer, fill
  the queue, and then alternate between waiting and being woken.

## Running

```
./mvnw -pl benchmark/rainbowgum-benchmark-jmh-publisher -am install -DskipTests
cd benchmark/rainbowgum-benchmark-jmh-publisher
../../mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) org.openjdk.jmh.Main AsyncPublisherBenchmark -t 4
```
