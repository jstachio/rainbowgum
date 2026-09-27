# logstash-logback-encoder: unbounded memory growth on HotSpot under sustained load

Found on `feature/graalvm-native-benchmark` (`benchmark/native/0-11-2-RESULTS.md`,
"Post-v0.11.2 quick pass" section, `logback` + GELF row), kept here in the same spirit as
`doc/logback-json-encoder-bugs.md` and the JDK bug docs, since it is a real, reproducible
finding worth being able to point at (and the reason `benchmark/micronaut/` deliberately
avoids this encoder, see that module's `FINDINGS.md`).

## Versions and environment

- **`net.logstash.logback:logstash-logback-encoder:9.0`**, `ch.qos.logback:logback-classic:1.6.3`.
- **JDK**: Temurin 26.0.2 (HotSpot). The leak did not reproduce under GraalVM 25.3.4 CE
  native-image, same benchmark, same encoder.
- **Benchmark**: `benchmark/native/` on `feature/graalvm-native-benchmark`, a plain
  `com.sun.net.httpserver.HttpServer`-based app (RainbowGum vs Log4j2 vs Logback, no
  Spring/Micronaut), GELF output via `net.logstash.logback.encoder.LogstashEncoder`
  (Logback has no built-in GELF layout of its own, so this third-party dependency was
  the natural choice for GELF specifically). Host: 121 GB RAM, no `-Xmx` set anywhere in
  this benchmark's own methodology.

## What happened

A batch run's `logback-gelf-run1` row showed RSS avg 5,407 MB / max 6,403 MB, when every
other row in the whole benchmark file, across both native-image and HotSpot, sits under
650 MB, plus a 116 ms max latency where every other row's max sits in single digits. The
background batch was killed partway through run 2, apparently by a host/sandbox resource
guard reacting to the runaway memory use, not a JVM crash.

Re-probed by hand, foreground, with live per-second RSS sampling, twice, to rule out a
fluke:

| variant | t=1s | t=8s | t=15-18s (still climbing) |
|---|---:|---:|---:|
| virtual threads | 466 MB | 8,384 MB | 8,511 MB |
| platform threads (fixed 200-thread pool) | 429 MB | 7,295 MB | 7,663 MB |

Growth was monotonic and never plateaued within either run, at roughly 34,000-38,000
req/s throughout (the leak, if that is what it is, did not appear to choke request
handling within this benchmark's short window, only memory).

## What was ruled out

- **Virtual-thread-per-request identity churn** (a new virtual thread every request,
  `~35,000/s`, could in principle leak through a `ThreadLocal`-cached buffer never
  reclaimed): ruled out, since the identical unbounded growth reproduced with a small,
  fixed, reused 200-platform-thread pool. 200 reused threads cannot explain multi-gigabyte
  growth through a per-thread cache.
- **A JVM/GC configuration mismatch generically**: a capped-heap sanity check (`-Xmx1g`,
  a shorter/lighter run) showed no growth at all, topping out at a stable 793 MB.

## Working hypothesis (not confirmed with a profiler)

HotSpot's default heap ergonomics on a high-RAM host (no `-Xmx` set, 121 GB available to
grow into) combined with `logstash-logback-encoder`'s Jackson-based allocation profile
under sustained load: garbage accumulates faster than GC reclaims it, and with a huge
default max heap available, HotSpot keeps expanding committed memory rather than
collecting aggressively. This is an informed hypothesis from the differential tests
above, not a diagnosed root cause. A heap histogram or allocation-profiler run against
this specific scenario is the natural next step if this gets picked up again; that was
not done here (risk of triggering another external kill on a background run was judged
not worth it for one more data point once the pattern was already clear from the
foreground reruns).

Never reproduced under native-image: the same `logback` + GELF scenario there showed a
normal 105.1 MB RSS. Whatever this is, it looks specific to HotSpot's heap behavior on
this particular host, not to Logback or `logstash-logback-encoder` architecturally in
every environment.

## The leak is specific to the third-party encoder, not to Logback or JSON logging generically

A follow-up in the same investigation gave Logback's own built-in
`ch.qos.logback.classic.encoder.JsonEncoder` (no third-party dependency) "a fair shot at
JSON," native-image, corrected numbers after an earlier (retracted) run turned out to be
measuring a silently-broken config:

| | JSON (`JsonEncoder`) | GELF (`LogstashEncoder`) |
|---|---:|---:|
| throughput | 69,298 req/s | 56,692 req/s |
| RSS avg | 77.9 MB | 105.5 MB |

Logback's own `JsonEncoder` beat the third-party Logstash encoder by +22.2% throughput
and -26.1% RSS on native-image, with zero sign of the HotSpot memory blow-up in any run
that used it. So the finding is specifically about `logstash-logback-encoder`'s own
allocation profile, not about Logback's architecture or JSON/structured logging in
general: worth being precise about that distinction, since it is easy to mischaracterize
this as "Logback is slow/leaky at structured logging" when the third-party path is the
actual variable.

## Where this informed later work

`benchmark/micronaut/` (RainbowGum vs Logback on Micronaut, JSON, HotSpot) deliberately
does not use `logstash-logback-encoder` for its Logback app because of this finding,
using `JsonEncoder` instead (see that module's `FINDINGS.md`). Separately,
`doc/logback-json-encoder-bugs.md` covers a different issue found with that same
`JsonEncoder` class while building that benchmark (message interpolation, not memory), in
case both come up together.

## Not yet done

- Root-cause with a heap histogram or allocation profiler instead of RSS-sampling
  differentials.
- Confirm whether an explicit `-Xmx` (a completely reasonable production setting, just
  not this benchmark's own default methodology) fully closes the gap, or whether it only
  masks the underlying allocation-rate problem by forcing more frequent, cheaper GC
  cycles.
