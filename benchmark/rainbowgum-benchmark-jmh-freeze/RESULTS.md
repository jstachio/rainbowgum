# Freeze benchmark: formatting message arguments before or after the async hand off

An async route freezes each event on the logging thread before handing it to the
publisher. These benchmarks compare three ways of freezing an SLF4J event that has
message arguments.

- **A, format when frozen** (`main`): the event is formatted on the logging thread and
  frozen into a `FrozenLogEvent` holding the formatted message.
- **C, keep argument text** (`feature/deferred-formatting-immutable`): arguments that
  are not known immutable values (String, boxed primitives, BigDecimal, UUID, java.time
  and a few others) are replaced by their text, and the event keeps its class. Every
  appender formats it.
- **E, keep argument text and format once** (`feature/deferred-formatting-cached`): as
  C, wrapped in a `DeferredLogEvent` that keeps the message the first appender formats
  and appends it for the rest.

Two earlier strategies were dropped after a first run. B, which turned every non String
argument into text, was never better than C. D, which recorded at creation whether the
arguments were already text, was no better than B.

Each event has an SLF4J message with these arguments:

| Events | Arguments |
|---|---|
| STRINGS | two Strings |
| NUMBERS | an Integer and a Long |
| OBJECTS | two application objects whose `toString()` builds a String |
| MIXED_FOUR | a String, a UUID, an Integer and an application object, as an array |

JMH 1.37, 2 forks, 3 x 2s warmup, 5 x 2s measurement, Temurin 27, aarch64, pinned to the
10 Cortex-X925 cores, one session. Errors on `FreezeBenchmark` are under 5% except where
noted.

## On the logging thread

`FreezeBenchmark.freeze` creates and freezes an event, which is what a logging thread
does for an async route. Nanoseconds per event, lower is better.

| Events | A | C | E |
|---|---|---|---|
| STRINGS | 34.0 | 3.2 | 5.7 |
| NUMBERS | 45.7 | 3.2 | 5.8 |
| OBJECTS | 58.8 | 50.9 | 54.7 |
| MIXED_FOUR | 84.5 | 35.8 | 39.3 |

## Total work for one event

The freeze plus the formatting the appenders do. Nanoseconds per event, lower is better.

| Events | 1 appender: A | C | E | 3 appenders: A | C | E |
|---|---|---|---|---|---|---|
| STRINGS | 37.2 | **14.7** | 26.9 | 44.2 ± 7.8 | 43.7 | **37.0** |
| NUMBERS | 52.1 | **27.1** | 43.6 | 64.0 | 90.7 | **52.9** |
| OBJECTS | **65.2** | 69.9 | 80.3 | **77.2** | 115.2 | 89.3 |
| MIXED_FOUR | 88.0 | **85.8** | 90.5 | **97.0** | 197.8 ± 10.8 | 111.0 |

Formatting without freezing, as a synchronous route does, is the same for all three:
14.8, 29 to 31, 52 to 53 and 61 ns.

## Through a route

`AsyncRouteBenchmark` logs through a `RainbowGum` route with a 1024 event async
publisher and appenders that discard their output. Millions of events per second, higher
is better.

| Events | 1 appender: A | C | E | 3 appenders: A | C | E |
|---|---|---|---|---|---|---|
| STRINGS | 2.1 ± 0.8 | 3.2 ± 1.7 | 13.6 ± 1.9 | 10.6 ± 2.7 | 6.0 ± 1.9 | 7.8 ± 0.9 |
| NUMBERS | 2.1 ± 1.4 | 11.1 ± 11.2 | 8.6 ± 7.3 | 4.5 ± 3.7 | 5.6 ± 2.2 | 6.6 ± 3.1 |
| OBJECTS | 1.6 ± 0.6 | 1.9 ± 1.2 | 2.1 ± 1.4 | 2.2 ± 0.9 | 6.5 ± 1.3 | 8.0 ± 0.4 |
| MIXED_FOUR | 1.7 ± 0.8 | 11.9 ± 0.2 | 8.8 ± 1.6 | 1.4 ± 0.7 | 3.9 ± 0.4 | 4.5 ± 1.1 |

## Reading it

- Both C and E take most of the formatting off the logging thread. Events whose
  arguments are all immutable values cost 3 to 6 ns to freeze instead of 34 to 46 ns.
  E's extra 2.5 ns is the wrapper.
- Application objects gain little, because their `toString()` still has to run on the
  logging thread before the event can be handed off.
- With one appender C does the least work, except for application objects. E pays for
  copying the message it keeps.
- With three appenders C formats three times and falls behind A by up to 2x. E formats
  once, beats A for immutable arguments, and stays within 14 to 16% of A otherwise. A
  remains cheapest overall for application objects, since it turns them straight into
  the formatted message instead of first into argument text.
- The route numbers swing between runs because a single producer fills the queue, then
  alternates between waiting and being woken. Some disagree with the tables above, such
  as STRINGS with three appenders, so they are too noisy to rank strategies on their
  own.

## Running

```
./mvnw -pl benchmark/rainbowgum-benchmark-jmh-freeze -am install -DskipTests
cd benchmark/rainbowgum-benchmark-jmh-freeze
../../mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) org.openjdk.jmh.Main 'FreezeBenchmark|AsyncRouteBenchmark'
```

To compare strategies, build the benchmark once and run it against the `rainbowgum-core`
jar from each branch.
