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

## Two real config-loading bugs found while wiring this up (both outside this benchmark)

**`rainbowgum-simple-props`'s `SimplePropertiesProvider` is never discovered once shaded
onto a plain classpath.** It only declares itself via `module-info.java`'s `provides`
clause, with no generated `META-INF/services` entry the way every other
`RainbowGumServiceProvider` in this codebase has (confirmed: `rainbowgum-pattern`'s
`PatternConfigurator` has both `module-info.class` and a real services file in its
installed jar; `rainbowgum-simple-props` has only the former). `module-info.class`
`provides` declarations are not consulted by `ServiceLoader` for a jar placed on a plain
classpath (as opposed to the module path), which is exactly what happens once
`maven-shade-plugin` merges everything into a Micronaut app's runnable fat jar. Confirmed
reproducing in the already-existing `examples/micronaut` too: its README claims a
`[RAINBOW_GUM]`-tagged pattern from `logging.properties` should appear in the output, but
a real `mvn package && java -jar` run shows Micronaut's plain default instead, with
`logging.properties` silently never read. This benchmark works around it by setting
`-Dlogging.appender.console.encoder=logback` as a JVM system property in `run.sh` instead
of relying on the classpath `logging.properties` convention. Not yet root-caused inside
`pistachio-svc-apt` itself (a separate project): flagged, not fixed, here.

**`LogConfig.Builder.build()` silently dropped `SYSTEM_PROPERTIES` (every `-D` override)
once any `PropertiesProvider` contributed anything at all**: `rainbowgum-micronaut5`'s
own `GLOBAL_CHANGE_PROPERTY` layer was enough to trigger it, which is exactly what broke
the `-D` workaround above on the first attempt. Root-caused to
`LogProperties.of(List, LogProperties)` only using its second (fallback) argument when
the list is empty, while `LogConfig.Builder.build()` passed `SYSTEM_PROPERTIES` as that
fallback instead of adding it to the list unconditionally. **Fixed** on
`fix/system-properties-dropped-with-custom-provider` (not yet merged as of this
benchmark), with a regression test (`LogConfigTest`) and a golden-string update
(`AvajePropertiesProviderTest`, which was hitting the identical bug independently via
`AvajePropertiesProvider`). This benchmark's `run.sh` workaround remains in place either
way since it targets whatever is on `main` right now, not the unmerged fix.

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
