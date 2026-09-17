# JSON encoder microbenchmark: Rainbow Gum vs Logback

Triggered by `benchmark/native`'s `0-11-2-RESULTS.md` finding that Logback's own
built-in `ch.qos.logback.classic.encoder.JsonEncoder` substantially outperformed both
the third-party `logstash-logback-encoder` *and*, in that same full HTTP benchmark,
looked competitive with Rainbow Gum's own GELF encoder (a different schema, so not a
clean comparison). Adam's hunch: maybe Rainbow Gum's JSON encoding pays for escaping
field names it doesn't need to (`JsonBuffer._writeStartField` escapes every field name
through the same path used for values, added in commit `1f2bb502` to close a real
security hole - an MDC-derived key containing a quote could otherwise forge sibling
JSON fields). Logback's own encoder doesn't: decompiling
`JsonEncoder.appenderMember(StringBuilder, String, String)` shows the field-name
argument written via a plain, unconditional `StringBuilder.append(String)` - only
values go through its own `jsonEscape`.

This module isolates just the two encoders - same schema
(`LogbackJsonEncoder` is a same-field-set reimplementation of Logback's own encoder,
see its own javadoc), same representative event (message + one MDC entry, no
throwable), no HTTP server/appender/locking in the loop at all.

## Result: Rainbow Gum's own encoder wins by ~3.7x, not the other way around

```
Benchmark                         Mode  Cnt        Score        Error  Units
JsonEncoderBenchmark.logback     thrpt   10  1155779.543 ± 125765.858  ops/s
JsonEncoderBenchmark.rainbowGum  thrpt   10  4297747.498 ± 139676.668  ops/s
```

(2 forks, 5 warmup + 5 measured iterations each, JMH 1.37, Temurin 26.0.2.)

This **overturns the field-name-escaping hypothesis as an explanation for a
deficit** - there is no deficit. Rainbow Gum's direct byte-array encoding
(`JsonBuffer`/`RawJsonWriter`) beats Logback's own `StringBuilder`-then-`getBytes()`
approach by a wide margin even while escaping every field name, known-safe or not.
Whatever gave Logback the edge in the full HTTP benchmark is not the JSON encoding
step itself - it has to be something else in that end-to-end path (appender/locking
strategy is the leading suspect, consistent with this whole benchmark's other
findings that lock strategy affects throughput more than encoding does; see
`benchmark/native`'s `0-11-2-RESULTS.md` "sync-vs-lock" section).

The escaping question is still real, just smaller in consequence than first thought:
it's overhead that exists and could in principle be removed (only `EXTENDED_F`/MDC-
derived keys actually need it for the security reason in `1f2bb502`; Rainbow Gum's own
hardcoded field names do not), but it isn't costing a competitive loss today - it would
just make an already-winning number bigger.

## Payload size: closer than the first (buggy) attempt suggested

First attempt showed Rainbow Gum 32.5% smaller, which turned out to be measuring the
wrong thing - Logback's plain `new JsonEncoder()` defaults to a different, more
verbose shape (`sequenceNumber`, `context`, raw message template + separate
`arguments` array instead of one formatted `message`) than what the native
benchmark's own `logback-json-builtin.xml` configured. Once both sides are
configured to the same field set:

```
rainbowgum bytes: 243
{"timestamp":...,"nanoseconds":...,"level":"INFO","threadName":"main","loggerName":"...","mdc":{"requestId":"42"},"message":"processing business logic step=1 value=3512882862"}

logback bytes:    270
{"timestamp":...,"nanoseconds":...,"level":"INFO","threadName":"main","loggerName":"...","mdc": {"requestId":"42"},"formattedMessage":"processing business logic step=1 value=3512882862","throwable":null}

rainbowgum is 10.0% smaller than logback for this event
```

Most of the remaining 27-byte gap is `"throwable":null` (17 bytes) - Rainbow Gum's own
`LogbackJsonEncoder` omits the `throwable` field entirely when there is none, Logback
always writes it. Not a meaningful difference either way - "less data" is not the
explanation for the throughput gap; the encoding mechanism itself is.

## Two real bugs found and fixed while building this

Both worth remembering if this benchmark (or anything similar) is extended later:

1. **Constructing a `LoggerContext` directly (`new LoggerContext()`) instead of going
   through `org.slf4j.LoggerFactory.getLogger(...)` never initializes
   `org.slf4j.MDC`'s backing adapter** - `MDC.put(...)` then silently has nowhere to
   go, and the first real symptom is a `NullPointerException` deep inside
   `LoggingEvent.getMDCPropertyMap()` the first time an encoder actually tries to read
   it. Fixed by always obtaining the `Logger` via the real `LoggerFactory` entry
   point, matching how every real application actually gets one.
2. **`ILoggingEvent#getMDCPropertyMap()` is not always eagerly copied at
   event-construction time** - confirmed by hand that whether it is varies with
   something about the appender-dispatch path (reproduced going from populated-map to
   empty-map just by attaching a second appender, or by calling `setAdditive(false)`
   with only one appender attached - not fully root-caused, Logback's own internals,
   out of scope to chase further here). Calling `MDC.remove(...)` before anything has
   actually read the captured event's map can wipe it out from under you. Fixed by
   always encoding (or otherwise reading the MDC map) before removing anything from
   the live MDC, never after.

## Not yet done

* Root-cause the `getMDCPropertyMap()` lazy-vs-eager-copy behavior in Logback's own
  source, if it ever matters again - not chased past confirming the practical
  workaround (encode before removing).
* An actual isolated micro-measurement of the field-name-escaping cost alone (i.e.
  `writeString` vs `writeAsciiString` for a handful of field names, many iterations)
  would need to live inside `rainbowgum-json` itself - both are package-private in
  `RawJsonWriter`, unreachable from this separate module.
* A throwable-bearing event variant - this pass only measured the no-throwable case.
