# Freeze benchmark: formatting message arguments before or after the async hand off

An async route freezes each event on the logging thread before handing it to the
publisher. These benchmarks compare three ways of freezing an SLF4J event that has
message arguments.

- **A, format when frozen** (`3316d6e7`, before C was merged): the event is formatted on the logging thread and
  frozen into a `FrozenLogEvent` holding the formatted message.
- **C, keep argument text** (`main` since `7bb826c8`, from
  `feature/deferred-formatting-immutable`): arguments that
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

The tables up to "Reading it" were measured on C's first commit, `14122fbb`. The two
commits after it, which copy the caller's argument array and keep argument semantics,
add about 22 ns to freezing MIXED_FOUR under C and E (33 to 55 ns) and nothing to the
other events. [Two routes](#two-routes) was measured on the final C and E.

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

## Two routes

Two async routes, each with one appender, are a more common setup than one route with
several appenders, for example the console and a file. The router freezes the event
once and hands the same frozen event to both routes. Under E both share one
`DeferredLogEvent`, so the second route appends the message the first formatted, unless
the two writer threads format it at the same moment.

`FreezeBenchmark.freezeForTwoRoutes` freezes the event, then freezes and formats it once
per route, on one thread. Measured in a second session, on `main` (`8fcf0125`) and on E
rebased onto it, with the same settings. Nanoseconds per event, lower is better.

| Events | Freeze: A | C | E | 1 route: A | C | E | 2 routes: A | C | E |
|---|---|---|---|---|---|---|---|---|---|
| STRINGS | 32.2 | **3.2** | 5.7 | 36.8 | **14.8** | 26.9 | 42.7 | **28.6** | 31.9 |
| NUMBERS | 46.2 | **3.2** | 5.7 | 51.8 | **28.8** | 29.3 | 57.8 | 66.4 | **47.4** |
| OBJECTS | **58.4** | 59.1 | 61.8 | **64.1** | 78.9 | 88.0 | **65.3 ± 7.2** | 107.2 | 91.7 |
| MIXED_FOUR | 85.0 | **60.7** | 62.7 | **84.2** | 101.3 | 107.5 | **92.3** | 165.0 ± 8.9 | 128.0 |

- With two routes C formats twice and falls behind A for everything but Strings, by up
  to 1.8x for MIXED_FOUR.
- E formats once. It is the cheapest for numbers, close to C for Strings, and 40% behind
  A for application objects and MIXED_FOUR. A still formats those straight into the
  message, while C and E first turn the objects into text and then format that text.
- On one thread the second route always finds the kept message, so E's numbers are its
  best case. Two writer threads can both format the event when they reach it together.
- The logging thread only pays the freeze, where C and E keep their lead for immutable
  arguments.

`AsyncRouteBenchmark` with two routes logs 0.6 to 2.3 million events per second for
every strategy and event type, with errors of 14 to 106%, so it can't rank them.

## Running

```
./mvnw -pl benchmark/rainbowgum-benchmark-jmh-freeze -am install -DskipTests
cd benchmark/rainbowgum-benchmark-jmh-freeze
../../mvnw dependency:build-classpath -Dmdep.outputFile=cp.txt
java -cp target/classes:$(cat cp.txt) org.openjdk.jmh.Main 'FreezeBenchmark|AsyncRouteBenchmark'
```

To compare strategies, build the benchmark once and run it against the `rainbowgum-core`
jar from each branch.
