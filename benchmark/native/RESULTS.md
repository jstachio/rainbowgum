# GraalVM native image benchmark results

Methodology: see [README.md](README.md). Each app run as a real native executable
(GraalVM 21 Oracle distro, `21.0.12-graal`), driven by
`rainbowgum-benchmark-native-driver` over plain HTTP/1.1, concurrency 50 (virtual
threads on both client and server side), 5s warmup (discarded) + 20s measured, against
`GET /greet/world`. RSS sampled from `/proc/<pid>/status` while the measurement ran.

| | Rainbow Gum | Log4j2 | Logback |
|---|---:|---:|---:|
| throughput | 23,057 req/s | **26,846 req/s** | 24,035 req/s |
| p50 latency | 2.13 ms | **1.65 ms** | 2.04 ms |
| p99 latency | 4.84 ms | 5.72 ms | **4.64 ms** |
| RSS avg | **45.7 MB** | 81.4 MB | 50.6 MB |

Checked for run-to-run stability with a second, independent pass (different warmup/
duration, same relative ordering held): Log4j2 led throughput and had the lowest
latency both times; Rainbow Gum led RSS (lowest memory) both times, by a wide and
consistent margin over Log4j2 specifically (roughly half).

## Honest read

Log4j2 is ahead here on raw throughput and p50 latency, not Rainbow Gum. This is
consistent with (not a departure from) `why_rainbowgum_is_better.md`'s own existing
"Fast" section, which already notes: *"on plain platform threads with no structured
logging, Log4j2 still leads there; that specific gap isn't closed."* This benchmark
uses virtual threads on both client and server, and TTLL (not structured) output, and
Log4j2 still leads - so that already-acknowledged gap shows up here too, not just on
platform threads.

Memory tells a different, clearly favorable story: Rainbow Gum uses roughly half the
resident memory of Log4j2 under sustained load, consistent with the module/image-size
story already established in `why_rainbowgum_is_better.md`'s "Small" section (Log4j2's
native image itself is also markedly larger - 25.86MB code area vs Rainbow Gum's
9.53MB, from the build output in this same session).

## Structured logging

Same methodology, `STRUCTURED_FORMAT=gelf`. Each framework uses its own idiomatic
structured format rather than being forced onto identical wire output - see
[README.md](README.md) for why: Rainbow Gum uses `rainbowgum-json`'s `GelfEncoder`
(GELF), Log4j2 uses `log4j-core`'s own built-in `GelfLayout` (also GELF, no extra
dependency), Logback uses the third-party `logstash-logback-encoder` (Logstash-format
JSON, not GELF - Logback has no first-party or well-maintained GELF option, and
forcing GELF parity there isn't worth a dependency nobody would actually pick).

| | Rainbow Gum | Log4j2 | Logback |
|---|---:|---:|---:|
| throughput | 31,464 req/s | **34,229 req/s** | 27,452 req/s |
| p50 latency | 1.54 ms | **1.37 ms** | 1.60 ms |
| p99 latency | **4.01 ms** | 4.33 ms | 7.77 ms |
| RSS avg | **52.7 MB** | 94.0 MB | 128.2 MB |

A more differentiated - and more favorable to Rainbow Gum - picture than the plain TTLL
result above. Log4j2 still leads raw throughput and p50, but by a narrower margin here.
Rainbow Gum now clearly beats Logback on throughput (not just memory), and leads on p99
tail latency too, not just RSS. Logback's p99 (7.77ms) and max (24.52ms) stand out
specifically - Jackson-based serialization (`logstash-logback-encoder` depends on
`jackson-databind`) is doing real, comparatively expensive work per event that neither
Rainbow Gum's own hand-rolled JSON writer nor Log4j2's built-in `GelfLayout` (also not
Jackson-based) has to pay for. Logback's memory (128.2 MB avg) is roughly 2.4x Rainbow
Gum's here, a wider gap than the already-wide TTLL-scenario gap against Log4j2.

## Not yet tried

* Rainbow Gum's per-appender locking strategy (`logging.appender.<name>.type`,
  `AppenderType`) is configurable and untouched here (default:
  `LOCK_THREAD_LOCAL_BUFFER`) - worth revisiting against this specific result before
  drawing final conclusions, but deliberately not chased yet.
* Theory (unverified, needs real profiling - JFR/async-profiler, not guessing): TTLL is
  mostly time formatting, and Log4j2's own fast-path date formatting may just be
  quicker than Rainbow Gum's here. Rainbow Gum's default TTLL formatter
  (`DefaultInstantFormatter`/`MillisCache`, `core/.../LogFormatter.java`) already
  caches the formatted string per millisecond, the same trick Log4j2's
  `FixedDateFormat`/Logback's `CachingDateFormatter` use, so it is not naively
  reformatting every event - but the cache is one shared `AtomicReference<Entry>`,
  meaning every concurrent thread contends on it, and two threads racing in the same
  millisecond both reformat and clobber each other's write. Separately, `Instant` is
  a real heap-allocated object today; Project Valhalla's value types could remove that
  allocation cost entirely if `Instant` (or a Valhalla-friendly replacement) becomes a
  flattened value type in a future JDK. Neither half of this theory has been profiled
  yet to confirm it is actually where the time goes.
