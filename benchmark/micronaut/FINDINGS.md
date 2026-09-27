# Findings

Durable knowledge from this benchmark investigation. For actual numbers see `RESULTS.md`;
this file is for the *why*.

## Why this benchmark does not use logstash-logback-encoder

An earlier benchmark (`feature/graalvm-native-benchmark`,
`benchmark/native/0-11-2-RESULTS.md`) found that `net.logstash.logback:logstash-logback-encoder`
shows unbounded memory growth on HotSpot under sustained load: RSS climbed monotonically
from ~450 MB to 8+ GB within 15-18 seconds and never plateaued, reproducing identically
under both virtual threads and a fixed 200-thread platform pool (ruling out
virtual-thread-per-request churn as the cause), and disappearing entirely under a capped
heap (`-Xmx1g`, stable at 793 MB). The working hypothesis (not confirmed with a heap
histogram or profiler) is HotSpot's default heap ergonomics on a high-RAM host letting
committed heap grow under the encoder's Jackson-based allocation profile rather than
collecting aggressively. It never reproduced under native-image (a normal 105.1 MB RSS
for the same scenario there).

A follow-up in that same investigation gave Logback's own built-in
`ch.qos.logback.classic.encoder.JsonEncoder` (no third-party dependency, part of
`logback-classic` since 1.5.x) "a fair shot at JSON": it showed zero sign of the leak and
actually beat the Logstash encoder on both throughput and RSS. So the finding is
specifically about the third-party Logstash encoder's own allocation profile, not about
Logback's architecture or JSON logging generically. That is exactly why this benchmark
uses Logback's own `JsonEncoder` (and RainbowGum's `LogbackJsonEncoder`, which mirrors
it) instead.

## LogbackJsonEncoder does not exactly match Logback's own JsonEncoder schema

Confirmed by running both side by side. Logback's own `JsonEncoder` keeps `message` as
the raw, unresolved `{}`-template string and puts the substituted values in a separate
`arguments` array:

```json
{"sequenceNumber":0,"timestamp":...,"level":"INFO","threadName":"...","loggerName":"...",
 "context":{...},"mdc":{"requestId":"1"},"message":"received request name={}",
 "arguments":["world"],"throwable":null}
```

RainbowGum's `LogbackJsonEncoder` (deliberately, per its own javadoc) formats the message
directly instead:

```json
{"timestamp":...,"nanoseconds":...,"level":"INFO","threadName":"...","loggerName":"...",
 "mdc":{"requestId":"1"},"message":"received request name=world"}
```

It also omits `sequenceNumber`, `context`, and `kvpList` (see the encoder's own javadoc
for why: no context-wide sequence generator, no `LoggerContext` equivalent, no
MDC/fluent-key-value distinction). Both are reasonable, readable JSON; they are not
byte-for-byte the same schema, which matters if something downstream parses these fields
by name.

## A real config-loading bug found while wiring this up (outside this benchmark)

`LogConfig.Builder.build()` silently dropped `SYSTEM_PROPERTIES` (every `-D` override)
once any `PropertiesProvider` contributed anything at all. `rainbowgum-micronaut5`'s own
`GLOBAL_CHANGE_PROPERTY` layer was enough to trigger it. Root-caused to
`LogProperties.of(List, LogProperties)` only using its second (fallback) argument when
the list is empty, while `LogConfig.Builder.build()` passed `SYSTEM_PROPERTIES` as that
fallback instead of adding it to the list unconditionally. **Fixed** on
`fix/system-properties-dropped-with-custom-provider` (not yet merged as of this
benchmark), with a regression test (`LogConfigTest`) and a golden-string update
(`AvajePropertiesProviderTest`, which was hitting the identical bug independently via
`AvajePropertiesProvider`). This benchmark's `rainbowgum` app does not need a workaround
for it (it configures the encoder via the classpath `logging.properties` convention, not
a `-D` flag), but the bug is worth knowing about if a future pass adds a `-D` override
here while running against a `main` that predates the fix.

## The Maven build cache silently drops annotation-processor-generated resources, non-deterministically

Not a code bug: a build tooling reliability issue, but a genuinely disruptive one. Maven's
build-cache extension can serve a stale or incompletely-restored compiled artifact on a
cache hit, silently dropping annotation-processor-generated resources like
`META-INF/services` entries, with no warning or error. First surfaced as what looked
exactly like a `rainbowgum-simple-props` code bug (`SimplePropertiesProvider` never
discovered by `ServiceLoader` once shaded onto a plain classpath): it resolved itself
completely, both in this benchmark and in the pre-existing `examples/micronaut`, once
re-tested with `-Dmaven.build.cache.enabled=false`.

**This is not just a "when investigating by hand" problem.** `run-all.sh`'s own build
step reproduced the identical symptom in a real, unattended run, non-deterministically:
one full `clean package` produced a working jar (`Encoder: LogbackJsonEncoder`), and the
very next `clean package` of the exact same sources produced a jar silently missing
`SimplePropertiesProvider` from its merged services file, falling back to the plain
pattern encoder with no error anywhere in the build log. `run-all.sh` now always passes
`-Dmaven.build.cache.enabled=false` for its own build step to keep this benchmark's
numbers trustworthy, and every recorded run's `results/rainbowgum-config-report.txt` is
checked to confirm `Encoder: LogbackJsonEncoder` actually resolved before the numbers are
treated as real (this is what caught it the second time). Worth raising as its own
concern: this could just as easily bite a real release or CI build of any module using
`pistachio-svc-apt`-generated services, not just this benchmark.

## Micronaut's out-of-the-box threading, confirmed not assumed

`BenchController`'s response body includes `Thread.currentThread().getName()`. Every
request observed in this benchmark, for both apps, was handled directly on a Netty
event-loop thread (`default-eventLoopGroup-2-N`), not offloaded to a separate blocking/
IO executor. This is Micronaut 5's real default for this specific handler shape (a plain,
fast, non-reactive-return-type method doing synchronous work); it is not a claim about
every possible controller method, and a handler that actually blocks (a JDBC call, a
synchronous HTTP client call) may be treated differently by Micronaut's own blocking
detection. Worth re-confirming if a future pass adds a deliberately slow/blocking
endpoint.

## Not yet investigated

- Whether the RSS difference (a wash in both runs so far) holds up over a longer duration
  or higher concurrency.
- GraalVM native-image behavior for either encoder in a Micronaut context specifically
  (the logstash-encoder memory-leak finding above was from a plain `HttpServer`
  benchmark, not Micronaut).
